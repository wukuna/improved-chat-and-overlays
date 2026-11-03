/*
 * Adapted portions from Chat Resizer by shanktank.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2026, shanktank
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.improvedchat.chatbox.resize;

import com.improvedchat.ImprovedChatConfig;

import com.improvedchat.chatbox.resize.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.HotkeyListener;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class RuneLiteHudAnchors {
    private final Client client;
    private final ImprovedChatConfig config;
    private final ConfigManager configManager;

    private final Map<Integer, Integer> stockReserve = new HashMap<>();

    private boolean applied;
    private boolean swapped;
    private int swappedHudId = -1;
    private boolean layoutChanged;
    private Widget lastLayoutHud;
    private int lastLayoutReserve = -1;

    private boolean dragHotkeyHeld;
    private RuneLiteConfig runeLiteConfig;

    final HotkeyListener dragHotkeyListener = new HotkeyListener(this::runeLiteDragHotkey) {
        @Override public void hotkeyPressed() { dragHotkeyHeld = true; }
        @Override public void hotkeyReleased() { dragHotkeyHeld = false; }
    };

    final BeforeDrawRestore beforeDrawRestore = new BeforeDrawRestore();

    final Overlay indicatorPresent = new HeightSlice(this::presentAnchorHeight, OverlayPosition.DYNAMIC);
    final Overlay indicatorRestore = new HeightSlice(this::restoreLayoutHeight, OverlayPosition.TOP_LEFT);

    @Inject
    RuneLiteHudAnchors(Client client, ImprovedChatConfig config, ConfigManager configManager) {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
    }

    private Keybind runeLiteDragHotkey() {
        if (runeLiteConfig == null) runeLiteConfig = configManager.getConfig(RuneLiteConfig.class);
        return runeLiteConfig.dragHotkey();
    }

    void sync(int chatHeightDelta) {

        if (!config.growInterfaces() && !(chatHeightDelta > 0 && !config.revertForModals().ungrows())) {
            if (applied) restore();
            return;
        }

        int delta = config.growInterfaces() ? chatHeightDelta : Math.max(0, chatHeightDelta);

        int id = activeFrontId();
        Widget hud = client.getWidget(id);
        if (hud == null) return;

        int base = stockReserve.computeIfAbsent(id, k -> hud.getOriginalHeight());
        int target = Math.max(0, base + delta);

        if (hud != lastLayoutHud || target != lastLayoutReserve) {
            lastLayoutHud = hud;
            lastLayoutReserve = target;
            layoutChanged = true;
        }
        if (hud.getOriginalHeight() != target) {
            hud.setOriginalHeight(target);
            hud.revalidate();

            Widgets.revalidateChildren(hud);
        }

        applied = true;
    }

    boolean consumeLayoutChanged() {
        boolean changed = layoutChanged;
        layoutChanged = false;
        return changed;
    }

    void presentAnchorHeight() {

        int id = activeFrontId();
        swapped = applied && client.isResized() && presentStockRendered(id);
        swappedHudId = swapped ? id : -1;
    }

    void forceStockRendered() {
        presentStockRendered(activeFrontId());
    }

    private boolean presentStockRendered(int id) {
        Widget hud = client.getWidget(id);
        if (hud == null) return false;
        Integer base = stockReserve.get(id);
        if (base == null || hud.getOriginalHeight() == base) return false;
        Widget parent = hud.getParent();
        if (parent == null) return false;
        int stockRendered = parent.getHeight() - base;
        if (hud.getHeight() != stockRendered) Widgets.setHeight(hud, stockRendered);
        return true;
    }

    private void restoreLayoutHeight() {
        if (!swapped || dragHotkeyHeld) return;
        int id = swappedHudId;
        swapped = false;
        swappedHudId = -1;
        Widget hud = id < 0 ? null : client.getWidget(id);
        if (hud != null) hud.revalidate();
    }

    void restore() {
        applied = false;
        lastLayoutHud = null;
        lastLayoutReserve = -1;
        swapped = false;
        swappedHudId = -1;
        restore(InterfaceID.ToplevelPreEoc.HUD_CONTAINER_FRONT);
        restore(InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT);
    }

    private void restore(int id) {
        Integer base = stockReserve.get(id);
        if (base == null) return;
        Widget w = client.getWidget(id);
        if (w != null && w.getOriginalHeight() != base) {
            w.setOriginalHeight(base);
            w.revalidate();
            Widgets.revalidateChildren(w);
            layoutChanged = true;
        }
    }

    private int activeFrontId() {

        return frontIdForTopLevel(client.getTopLevelInterfaceId());
    }

    static int frontIdForTopLevel(int topLevelId) {
        return topLevelId == InterfaceID.TOPLEVEL_PRE_EOC
            ? InterfaceID.ToplevelPreEoc.HUD_CONTAINER_FRONT
            : InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT;
    }

    final class BeforeDrawRestore {

        @Subscribe(priority = -1)
        void onBeforeRender(BeforeRender event) {
            restoreLayoutHeight();
        }
    }

    private static final class HeightSlice extends Overlay {
        private final Runnable slice;

        HeightSlice(Runnable slice, OverlayPosition position) {
            this.slice = slice;
            setLayer(OverlayLayer.ABOVE_WIDGETS);
            setPosition(position);
            setPriority(PRIORITY_LOW);
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            slice.run();
            return null;
        }
    }
}
