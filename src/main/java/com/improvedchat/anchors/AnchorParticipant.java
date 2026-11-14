package com.improvedchat.anchors;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;

public final class AnchorParticipant
{
    public static final String NAMESPACE = "interface-layout-anchors";
    public interface Target
    {
        String id();
        String name();
        Rectangle bounds();
        default Rectangle movementBounds() { return null; }
        default String state() { return "VISIBLE"; }
        default boolean available() { return true; }
        default boolean resizable() { return false; }
        default List<String> aliases() { return Collections.emptyList(); }
        boolean claim();
        void move(Point location);
        void release(boolean keepPosition);
        default void resize(Dimension size) { }
    }
    public interface Provider
    {
        Collection<? extends Target> targets();
        default void commit() { }
        default void settle() { }
        default void reset() { }
    }

    private final String owner;
    private final EventBus events;
    private final ClientThread thread;
    private final Provider provider;
    private final Map<String, Target> measured = new LinkedHashMap<>();
    private final Map<String, Target> controlled = new LinkedHashMap<>();
    private String token = UUID.randomUUID().toString();
    private String session = "";
    private long request = -1, applied = -1;
    private volatile boolean active;

    public AnchorParticipant(String owner, EventBus events, ClientThread thread, Provider provider)
    { this.owner = owner; this.events = events; this.thread = thread; this.provider = provider; }

    public void start()
    {
        active = true; events.register(this);
        thread.invoke(() -> { if (active) send("ready", Collections.emptyMap()); });
    }

    public void stop()
    {
        active = false; events.unregister(this);
        thread.invoke(() -> {
            releaseAll(false); send("bye", Collections.emptyMap());
            session = ""; measured.clear();provider.reset();
        });
    }

    @Subscribe(priority=100) public void onProfileChanged(ProfileChanged event)
    {
        OverlayLease.beforeProfileChange();
        thread.invokeLater(() -> {
            if (!active) return;
            releaseAll(false); send("bye", Collections.emptyMap());
            token = UUID.randomUUID().toString(); session = ""; request = -1; applied = -1;
            measured.clear();provider.reset(); send("ready", Collections.emptyMap());
        });
    }

    @Subscribe public void onPluginMessage(PluginMessage event)
    {
        if (!active || !NAMESPACE.equals(event.getNamespace())) return;
        thread.invoke(() -> { if (active) receive(event); });
    }

    private void receive(PluginMessage event)
    {
        Map<String, Object> data = event.getData();
        if (number(data.get("version"), -1) != 1) return;
        String recipient = string(data.get("provider"));
        if (!recipient.isEmpty() && !recipient.equals(token)) return;
        String incoming = string(data.get("session"));
        if (incoming.isEmpty()) return;
        if ("hello".equals(event.getName()))
        {
            if (!session.equals(incoming)) { releaseAll(false); session = incoming; request = -1; applied = -1; }
            send("joined", Collections.emptyMap());
            return;
        }
        if (!incoming.equals(session)) return;
        if ("shutdown".equals(event.getName()))
        {
            releaseAll(false); session = ""; measured.clear();
            return;
        }
        long sequence = number(data.get("request"), -1);
        if ("measure".equals(event.getName()))
        {
            if (sequence <= request) return;
            request = sequence; measured.clear();
            List<Map<String, Object>> targets = new ArrayList<>();
            for (Target target : provider.targets())
            {
                if (targets.size() >= 256 || target == null) break;
                String id = target.id();
                if (id == null || id.length() > 240 || measured.putIfAbsent(id, target) != null) continue;
                Rectangle bounds = target.bounds();
                if (bounds == null) bounds = new Rectangle();
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", id); value.put("name", target.name()); value.put("state", target.state());
                value.put("x", bounds.x); value.put("y", bounds.y);
                value.put("width", bounds.width); value.put("height", bounds.height);
                value.put("movable", target.available()); value.put("resizable", target.resizable());
                value.put("aliases", new ArrayList<>(target.aliases()));
                Rectangle limits=target.movementBounds();
                if(limits!=null)value.put("limits",Map.of("x",limits.x,"y",limits.y,"width",limits.width,"height",limits.height));
                targets.add(value);
            }
            for (String id : new ArrayList<>(controlled.keySet()))
                if (!measured.containsKey(id)) controlled.remove(id).release(false);
            send("measured", Map.of("request", request, "targets", targets));
            return;
        }
        if(sequence==request && "settle".equals(event.getName())) {provider.settle();return;}
        if (sequence != request || sequence <= applied) return;
        if ("apply".equals(event.getName()))
        {
            Object rows = data.get("placements");
            if (!(rows instanceof List) || ((List<?>) rows).size() > 256) return;
            applied = sequence;
            List<Map<String, Object>> accepted = new ArrayList<>();
            for (Object value : (List<?>) rows)
            {
                if (!(value instanceof Map)) continue;
                Map<?, ?> row = (Map<?, ?>) value;
                String id = string(row.get("id"));
                Target target = measured.get(id);
                int x = integer(row.get("x")), y = integer(row.get("y"));
                if (target == null || !target.available() || Math.abs((long) x) > 1000000 || Math.abs((long) y) > 1000000) continue;
                Target previous = controlled.get(id);
                if (previous != null && previous != target) previous.release(false);
                if (previous != target && !target.claim()) { controlled.remove(id); continue; }
                controlled.put(id, target);
                target.move(new Point(x, y));
                Rectangle bounds = target.bounds();
                if (bounds != null) accepted.add(new LinkedHashMap<>(Map.of("id", id, "x", bounds.x, "y", bounds.y, "width", bounds.width, "height", bounds.height)));
            }
            Object releases = data.get("release");
            if (releases instanceof List && ((List<?>) releases).size() <= 256)
                for (Object value : (List<?>) releases)
                {
                    Target target = controlled.remove(string(value));
                    if (target != null) target.release(Boolean.TRUE.equals(data.get("keepPosition")));
                }
            provider.commit();
            for(Map<String,Object> value:accepted) {
                Target target=controlled.get(string(value.get("id")));Rectangle bounds=target==null?null:target.bounds();
                if(bounds!=null) {value.put("x",bounds.x);value.put("y",bounds.y);value.put("width",bounds.width);value.put("height",bounds.height);}
            }
            send("applied", Map.of("request", request, "targets", accepted));
        }
        else if ("resize".equals(event.getName()))
        {
            Target target = measured.get(string(data.get("id")));
            int width = integer(data.get("width")), height = integer(data.get("height"));
            if (target != null && target.available() && target.resizable() && width > 0 && height > 0 && width <= 16384 && height <= 16384)
            {
                applied = sequence; target.resize(new Dimension(width, height)); provider.commit();
            }
        }
    }

    private void releaseAll(boolean keep)
    {
        controlled.values().forEach(target -> target.release(keep));
        controlled.clear(); provider.commit();
    }

    private void send(String name, Map<String, Object> extra)
    {
        Map<String, Object> data = new LinkedHashMap<>(extra);
        data.put("version", 1); data.put("provider", token); data.put("owner", owner); data.put("session", session);
        events.post(new PluginMessage(NAMESPACE, name, data));
    }

    public boolean owns(String id) { return controlled.containsKey(id); }
    public static String string(Object value) { return value instanceof String && ((String) value).length() <= 256 ? (String) value : ""; }
    public static long number(Object value, long fallback)
    {
        if (!(value instanceof Number)) return fallback;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number == Math.rint(number) && number >= -9007199254740991L && number <= 9007199254740991L ? ((Number) value).longValue() : fallback;
    }
    public static int integer(Object value)
    {
        long number = number(value, Long.MIN_VALUE);
        return number < Integer.MIN_VALUE || number > Integer.MAX_VALUE ? Integer.MIN_VALUE : (int) number;
    }
}
