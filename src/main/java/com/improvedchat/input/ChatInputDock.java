package com.improvedchat.input;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.resize.TopLevelModals;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.WidgetNode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.MouseAdapter;

@Singleton
public class ChatInputDock extends MouseAdapter {
    private final Client client;
    private final ClientThread clientThread;
    private final ImprovedChatConfig config;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean active;
    private volatile HitMap painted;
    private boolean pressed;
    private boolean clicked;

    @Inject
    public ChatInputDock(Client client, ClientThread clientThread, ImprovedChatConfig config) {
        this.client = client; this.clientThread = clientThread; this.config = config;
    }

    public void startUp() { invalidate(); active = true; }
    public void shutDown() { active = false; invalidate(); }
    public void invalidate() { generation.incrementAndGet(); painted = null; }
    @Subscribe public void onProfileChanged(ProfileChanged event) { invalidate(); }
    @Subscribe public void onGameStateChanged(GameStateChanged event) { invalidate(); }
    @Subscribe public void onConfigChanged(ConfigChanged event) {
        if (ImprovedChatConfig.GROUP.equals(event.getGroup())) invalidate();
    }
    @Subscribe public void onBeforeRender(BeforeRender event) { painted = null; }

    public InputDockLayout layout(Graphics2D graphics, boolean vertical) {
        if (!active || !config.inputDockEnabled() || client.getGameState() != GameState.LOGGED_IN) return null;
        List<ChatInputMode> modes = new ArrayList<>();
        for (ChatInputMode mode : ChatInputMode.values()) {
            if (mode.selected(config) && (mode != ChatInputMode.GIM || mode.available(client))) modes.add(mode);
        }
        return modes.isEmpty() ? null : new InputDockLayout(graphics, modes, config.inputDockScale(), vertical);
    }

    public InputDockLayout attachedLayout(Graphics2D graphics, String overlayId) {
        return config.inputDockPlacement() != InputDockPlacement.FLOATING
                && overlayId.equals(config.inputDockTarget())
                ? layout(graphics, config.inputDockPlacement().isVertical()) : null;
    }

    public boolean canSwitch() {
        return active && config.inputDockEnabled() && client.getGameState() == GameState.LOGGED_IN
                && client.getLocalPlayer() != null && !client.isMenuOpen() && !client.isWidgetSelected()
                && !client.isDraggingWidget() && !TopLevelModals.hasOpenModal(client)
                && client.getVarcIntValue(VarClientID.MESLAYERMODE) == 0
                && !hasChatDialog()
                && client.getWidget(InterfaceID.Chatbox.INPUT) != null;
    }

    private boolean hasChatDialog() {
        Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
        if (layer != null && !layer.isHidden()) return true;
        if (client.getComponentTable() != null) {
            for (WidgetNode node : client.getComponentTable()) {
                if ((node.getHash() >>> 16) == InterfaceID.CHATBOX) return true;
            }
        }
        return false;
    }

    private Area coveredWidgets() {
        Area covered = new Area();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] ids = {InterfaceID.Toplevel.MAPCONTAINER, InterfaceID.Toplevel.SIDE3,
                InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelOsrsStretch.SIDE_MENU,
                InterfaceID.ToplevelPreEoc.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER};
        for (int id : ids) {
            coverWidget(client.getWidget(id), null, covered, visited, 0);
        }
        return covered;
    }

    private static void coverWidget(Widget widget, Rectangle clip, Area covered, Set<Widget> visited, int depth) {
        if (widget == null || depth > 16 || !visited.add(widget) || widget.isHidden()) return;
        Rectangle bounds = widget.getBounds();
        if (clip != null) bounds = bounds.intersection(clip);
        if (bounds.isEmpty()) return;
        if (widget.getType() != WidgetType.LAYER) {
            covered.add(new Area(bounds));
            return;
        }
        coverChildren(widget.getStaticChildren(), bounds, covered, visited, depth + 1);
        coverChildren(widget.getDynamicChildren(), bounds, covered, visited, depth + 1);
        coverChildren(widget.getNestedChildren(), bounds, covered, visited, depth + 1);
    }

    private static void coverChildren(Widget[] children, Rectangle clip, Area covered, Set<Widget> visited, int depth) {
        if (children == null) return;
        for (Widget child : children) coverWidget(child, clip, covered, visited, depth);
    }

    public void paint(Graphics2D target, InputDockLayout layout, boolean floating) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(layout.font);
            if (floating) {
                g.setColor(new Color(28, 30, 32, 245));
                g.fillRoundRect(0, 0, layout.size.width - 1, layout.size.height - 1, layout.px(14), layout.px(14));
                g.setColor(new Color(68, 71, 74));
                g.drawRoundRect(0, 0, layout.size.width - 1, layout.size.height - 1, layout.px(14), layout.px(14));
            }
            boolean ready = canSwitch();
            int selected = client.getVarcIntValue(VarClientID.CHATBOX_MODE);
            List<Shape> regions = new ArrayList<>();
            List<Boolean> enabled = new ArrayList<>();
            Area covered = floating ? new Area() : coveredWidgets();
            for (int i = 0; i < layout.modes.size(); i++) {
                ChatInputMode mode = layout.modes.get(i);
                Rectangle rect = layout.buttons.get(i);
                boolean available = ready && mode.available(client);
                boolean chosen = selected == mode.value;
                g.setColor(config.inputDockButtonBackground());
                g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, layout.px(8), layout.px(8));
                g.setColor(chosen ? config.inputDockSelectedBorder() : config.inputDockButtonBorder());
                g.drawRoundRect(rect.x, rect.y, rect.width - 1, rect.height - 1, layout.px(8), layout.px(8));
                g.setColor(available ? config.inputDockButtonForeground() : config.inputDockDisabledForeground());
                Graphics2D icon = (Graphics2D) g.create();
                icon.translate(rect.x + layout.px(10), rect.y + layout.px(9));
                icon.scale(layout.scale, layout.scale);
                drawIcon(icon, mode);
                icon.dispose();
                int baseline = rect.y + (rect.height - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                g.drawString(mode.label, rect.x + layout.px(32), baseline);
                if (chosen) {
                    g.setColor(config.inputDockSelectedBorder());
                    g.fillOval(rect.x + rect.width - layout.px(9), rect.y + rect.height / 2 - layout.px(2), layout.px(4), layout.px(4));
                }
                Area visible = new Area(rect);
                if (g.getClip() != null) visible.intersect(new Area(g.getClip()));
                visible.transform(g.getTransform());
                visible.subtract(covered);
                regions.add(visible);
                enabled.add(available);
            }
            Area area = new Area(new Rectangle(layout.size));
            if (g.getClip() != null) area.intersect(new Area(g.getClip()));
            area.transform(g.getTransform());
            area.subtract(covered);
            painted = ready ? new HitMap(area, regions,
                    layout.modes, enabled, generation.get(), floating) : null;
        } finally { g.dispose(); }
    }

    private static void drawIcon(Graphics2D g, ChatInputMode mode) {
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (mode == ChatInputMode.PUBLIC) {
            g.drawOval(0, 0, 16, 16);
            g.drawOval(4, 0, 8, 16);
            g.drawLine(0, 8, 16, 8);
            g.drawArc(1, 2, 14, 4, 180, 180);
            g.drawArc(1, 10, 14, 4, 0, 180);
        } else if (mode == ChatInputMode.CLAN) {
            g.drawOval(6, 1, 5, 5); g.drawArc(3, 8, 11, 10, 0, 180);
            g.drawArc(0, 2, 5, 5, 80, 210); g.drawArc(-2, 9, 8, 8, 45, 120);
            g.drawArc(12, 2, 5, 5, -110, 210); g.drawArc(11, 9, 8, 8, 15, 120);
        } else if (mode == ChatInputMode.GIM) {
            Path2D shield = new Path2D.Double(); shield.moveTo(8, 0); shield.lineTo(15, 3);
            shield.lineTo(14, 10); shield.quadTo(12, 14, 8, 16); shield.quadTo(4, 14, 2, 10);
            shield.lineTo(1, 3); shield.closePath(); g.draw(shield);
            g.drawLine(8, 4, 8, 11); g.drawLine(5, 7, 11, 7);
        } else {
            if (mode == ChatInputMode.CHANNEL) g.drawRoundRect(5, 0, 11, 10, 3, 3);
            g.drawRoundRect(0, mode == ChatInputMode.CHANNEL ? 5 : 1, 12, 10, 3, 3);
            g.drawLine(3, mode == ChatInputMode.CHANNEL ? 15 : 11, 3, 16);
            g.drawLine(3, 16, 7, mode == ChatInputMode.CHANNEL ? 15 : 11);
        }
    }

    @Override public MouseEvent mousePressed(MouseEvent event) {
        pressed = false; clicked = false;
        HitMap hit = painted;
        if (event.isConsumed() || event.getButton() != MouseEvent.BUTTON1 || event.isAltDown()
                || event.isControlDown() || event.isShiftDown() || event.isMetaDown()
                || hit == null || !active || System.nanoTime() - hit.time > 500_000_000L
                || !hit.bounds.contains(event.getPoint())) return event;
        pressed = true;
        event.consume();
        for (int i = 0; i < hit.regions.size(); i++) {
            if (hit.enabled.get(i) && hit.regions.get(i).contains(event.getPoint())) {
                ChatInputMode mode = hit.modes.get(i);
                clientThread.invokeLater(() -> {
                    if (hit.generation == generation.get() && canSwitch() && mode.selected(config) && mode.available(client)
                            && (hit.floating || !coveredWidgets().contains(event.getPoint()))) {
                        client.setVarcIntValue(VarClientID.CHATBOX_MODE, mode.value);
                        client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
                    }
                });
                break;
            }
        }
        return event;
    }
    @Override public MouseEvent mouseDragged(MouseEvent event) { if (pressed) event.consume(); return event; }
    @Override public MouseEvent mouseReleased(MouseEvent event) {
        if (event.getButton() == MouseEvent.BUTTON1 && pressed) {
            pressed = false; clicked = true; event.consume();
        }
        return event;
    }
    @Override public MouseEvent mouseClicked(MouseEvent event) {
        if (event.getButton() == MouseEvent.BUTTON1 && clicked) { clicked = false; event.consume(); }
        return event;
    }

    private static final class HitMap {
        final Shape bounds;
        final List<Shape> regions;
        final List<ChatInputMode> modes;
        final List<Boolean> enabled;
        final long generation;
        final boolean floating;
        final long time = System.nanoTime();
        HitMap(Shape bounds, List<Shape> regions, List<ChatInputMode> modes, List<Boolean> enabled, long generation, boolean floating) {
            this.bounds = bounds; this.regions = regions; this.modes = modes; this.enabled = enabled; this.generation = generation;
            this.floating = floating;
        }
    }
}
