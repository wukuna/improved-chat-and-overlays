package com.improvedchat;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import com.improvedchat.overlay.OverlayConfig;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;

final class VisibilityHotkeys implements KeyListener {
    private final Consumer<Activation> action;
    private final Set<Integer> held = new HashSet<>();
    private Snapshot snapshot = new Snapshot(Collections.emptyList(), Keybind.NOT_SET);

    VisibilityHotkeys(Consumer<Activation> action) { this.action = action; }

    synchronized void configure(List<OverlayConfig> overlays, Keybind hideChatbox) {
        List<Binding> bindings = new ArrayList<>();
        for (OverlayConfig overlay : overlays) bindings.add(new Binding(overlay.getId(), overlay.getToggleKeybind()));
        Keybind hide = hideChatbox == null ? Keybind.NOT_SET : hideChatbox;
        if (snapshot.hideChatbox.equals(hide) && snapshot.bindings.size() == bindings.size()) {
            boolean same = true;
            for (int i = 0; i < bindings.size(); i++) {
                Binding a = snapshot.bindings.get(i), b = bindings.get(i);
                same &= a.id.equals(b.id) && a.key.equals(b.key);
            }
            if (same) return;
        }
        snapshot = new Snapshot(bindings, hide);
    }

    synchronized boolean isCurrent(Activation activation) { return activation.snapshot == snapshot; }

    synchronized void reset() {
        held.clear();
        snapshot = new Snapshot(Collections.emptyList(), Keybind.NOT_SET);
    }

    @Override public synchronized void keyPressed(KeyEvent event) {
        List<String> ids = new ArrayList<>();
        for (Binding binding : snapshot.bindings) if (binding.key.matches(event)) ids.add(binding.id);
        boolean hide = snapshot.hideChatbox.matches(event);
        if (ids.isEmpty() && !hide) return;
        event.consume();
        if (held.add(event.getKeyCode())) action.accept(new Activation(snapshot, ids, hide));
    }

    @Override public synchronized void keyReleased(KeyEvent event) {
        if (held.remove(event.getKeyCode())) event.consume();
    }

    @Override public synchronized void keyTyped(KeyEvent event) { if (!held.isEmpty()) event.consume(); }
    @Override public synchronized void focusLost() { held.clear(); }

    static final class Activation {
        private final Snapshot snapshot;
        final List<String> overlayIds;
        final boolean hideChatbox;
        Activation(Snapshot snapshot, List<String> ids, boolean hide) {
            this.snapshot = snapshot; overlayIds = ids; hideChatbox = hide;
        }
    }

    private static final class Snapshot {
        final List<Binding> bindings;
        final Keybind hideChatbox;
        Snapshot(List<Binding> bindings, Keybind hideChatbox) { this.bindings = bindings; this.hideChatbox = hideChatbox; }
    }

    private static final class Binding {
        final String id;
        final Keybind key;
        Binding(String id, Keybind key) { this.id = id; this.key = key; }
    }
}
