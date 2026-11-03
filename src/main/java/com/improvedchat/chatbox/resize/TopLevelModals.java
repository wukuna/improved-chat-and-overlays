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
/* Derived from Chat Resizer by shanktank under BSD 2-Clause. See THIRD_PARTY_NOTICES.md. */
package com.improvedchat.chatbox.resize;

import com.improvedchat.chatbox.resize.internal.RawScripts;
import com.improvedchat.chatbox.resize.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.ScriptEvent;
import net.runelite.api.WidgetNode;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TopLevelModals {
    // Top-level interface slots, per layout, into which most overlays, such as the bank screen, are loaded
    @Component private static final int[] MODAL_SLOTS = {
        InterfaceID.Toplevel.MAINMODAL, InterfaceID.Toplevel.FLOATER,
        InterfaceID.ToplevelOsrsStretch.MAINMODAL, InterfaceID.ToplevelOsrsStretch.FLOATER,
        InterfaceID.ToplevelDisplay.MAINMODAL, InterfaceID.ToplevelDisplay.FLOATER,
        InterfaceID.ToplevelOsm.MAINMODAL, InterfaceID.ToplevelOsm.FLOATER,
        InterfaceID.ToplevelPreEoc.MAINMODAL, InterfaceID.ToplevelPreEoc.FLOATER,
        InterfaceID.ToplevelSpectator.MAINMODAL, InterfaceID.ToplevelSpectator.FLOATER,
    };

    // One self-refitting overlay's live-captured size poll, replayed in relayout() so it
    // re-fits in the same pass instead of showing one stale frame from its own ontimer poll
    @RequiredArgsConstructor
    private static final class RefitSpec {
        final int timerScript;  // Ontimer size poll whose root invocation carries the ScriptEvent we replay
        final int argCount;     // Expected getArguments() length; a different length is a different script shape, skip
        final int wipeW, wipeH; // Arg indices of the poll's last-seen w/h; wiped to -1 so it can't early-out on a match
        final int universe;     // The window's UNIVERSE root: refit runs only while it exists (and is the filter value)
        final int filterArg;    // Arg index that must equal universe to accept a shared-helper poll, or -1 for none
        Object[] captured;      // Last captured invocation (dims wiped), replayed by refit(); null until first seen
    }

    private final RefitSpec[] refits = {
        // Bank: measures the toplevel mount slot directly, so it alone needs no post-realign ordering
        new RefitSpec(RawScripts.BANKMAIN_SIZE_CHECK_TIMER, 7, 3, 4, InterfaceID.Bankmain.UNIVERSE, -1),
        // All Settings: measures the settings group's own root
        new RefitSpec(RawScripts.SETTINGS_SIZE_CHECK_TIMER, 8, 6, 7, InterfaceID.Settings.UNIVERSE, -1),
        // Collection Log owns its row geometry and selection. Never replay a guessed window script.
        // Seed Vault: measures the mounted root, then sizes its window absolutely, so nothing else can re-fit it
        new RefitSpec(RawScripts.SEED_VAULT_SIZE_CHECK_TIMER, 10, 3, 4, InterfaceID.SeedVault.UNIVERSE, -1),
    };

    private final Client client;

    @Getter private boolean modalOpen;

    @Inject
    TopLevelModals(Client client) {
        this.client = client;
    }

    boolean isTopLevelModalOpen() {
        return hasOpenModal(client);
    }

    /** Read on the client thread; also used when resizing is disabled. */
    public static boolean hasOpenModal(Client client) {
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return false;
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        if (componentTable == null) return false;
        for (int slot : MODAL_SLOTS) if (componentTable.get(slot) != null) return true;
        return false;
    }

    boolean topLevelModalOpenStateChanged() {
        boolean open = isTopLevelModalOpen();
        boolean changed = open != modalOpen;
        modalOpen = open;
        return changed;
    }

    // Re-prime the cached open-state on enable; the singleton survives disable -> enable
    void reset() {
        modalOpen = isTopLevelModalOpen();
        for (RefitSpec spec : refits) spec.captured = null;
    }

    // Re-fit the UI to current available space. The toplevel ID can flip a cycle before the swap completes,
    // so it is used for relayout target selection only; modal detection stays the scan-all above.
    void relayout() {
        // A generic relayout/revalidate can rebuild or overlap Collection Log's interactive rows.
        // Its native window handlers own resizing; leave the entire mounted view intact.
        if (isCollectionLogOpen()) return;
        int controlId = -1, layoutEnum = -1;
        switch (client.getTopLevelInterfaceId()) {
            case InterfaceID.TOPLEVEL:
                controlId = InterfaceID.Toplevel.CONTROL;
                layoutEnum = RawScripts.LAYOUT_ENUM_FIXED;
                break;
            case InterfaceID.TOPLEVEL_OSRS_STRETCH:
                controlId = InterfaceID.ToplevelOsrsStretch.CONTROL;
                layoutEnum = RawScripts.LAYOUT_ENUM_OSRS_STRETCH;
                break;
            case InterfaceID.TOPLEVEL_PRE_EOC:
                controlId = InterfaceID.ToplevelPreEoc.CONTROL;
                layoutEnum = RawScripts.LAYOUT_ENUM_PRE_EOC;
                break;
            default:
                // Some temporary/fullscreen toplevels have no matching 904 layout enum. Their child
                // slots may still have been resized by the HUD-band change, so mounted roots still
                // need the direct realign/refit pass below.
                break;
        }

        if (controlId >= 0) {
            client.runScript(RawScripts.TOPLEVEL_ONRESIZE, controlId, layoutEnum);
        }
        realignMounted();
        // Must follow the realign: the own-root pollers measure a root only that call re-aligns
        for (RefitSpec spec : refits) if (spec.captured != null && client.getWidget(spec.universe) != null) client.runScript(spec.captured);
    }

    boolean isCollectionLogOpen() {
        HashTable<WidgetNode> table = client.getComponentTable();
        if (table == null) return false;
        for (int slot : MODAL_SLOTS) {
            WidgetNode node = table.get(slot);
            if (node != null && node.getId() == InterfaceID.COLLECTION) return true;
        }
        return false;
    }

    // Called for every pre-fired script; only the rows' timer scripts match
    void cacheRefit(int scriptId, ScriptEvent event) {
        if (event == null) return; // Only a root invocation carries the ScriptEvent whose args we replay
        for (RefitSpec spec : refits) {
            if (spec.timerScript != scriptId) continue;
            Object[] args = event.getArguments();
            if (args == null || args.length != spec.argCount) return; // Different script shape -> not the poll we mean to replay
            // Shared-helper polls also fire for other windows; accept only this window's own root
            if (spec.filterArg >= 0 && (!(args[spec.filterArg] instanceof Integer) || (Integer) args[spec.filterArg] != spec.universe)) return;
            spec.captured = args.clone();
            spec.captured[spec.wipeW] = -1; // Wipe the last-seen dims, or the replayed poll early-outs on a match
            spec.captured[spec.wipeH] = -1;
            return;
        }
    }

    // Realign each mounted modal's root to its slot; the engine's own revalidate cascade never reaches them
    private void realignMounted() {
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        for (int slotId : MODAL_SLOTS) {
            WidgetNode mounted = componentTable.get(slotId);
            if (mounted == null) continue;
            Widget slot = client.getWidget(slotId);
            if (slot == null) continue;
            slot.revalidate(); // Against the band now, so the realign and the refit replays measure fresh dims
            Widget root = Widgets.mountedRoot(client, mounted.getId());
            if (root == null) continue;
            // Match engine alignment: only MINUS axes track the slot; absolute roots size themselves
            int w = root.getWidthMode() == WidgetSizeMode.MINUS ? slot.getWidth() - root.getOriginalWidth() : root.getWidth();
            int h = root.getHeightMode() == WidgetSizeMode.MINUS ? slot.getHeight() - root.getOriginalHeight() : root.getHeight();
            if (w == root.getWidth() && h == root.getHeight()) continue;
            Widgets.setWidth(root, w);
            Widgets.setHeight(root, h);
            Widgets.revalidateChildren(root);
        }
    }
}
