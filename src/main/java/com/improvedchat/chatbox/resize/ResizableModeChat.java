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

import com.improvedchat.chatbox.resize.internal.ChatGeometry;
import com.improvedchat.chatbox.resize.internal.ChatRebuild;
import com.improvedchat.chatbox.resize.internal.RawScripts;
import com.improvedchat.chatbox.resize.internal.SizeClamps;
import com.improvedchat.chatbox.resize.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import java.awt.Dimension;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ResizableModeChat {
    private final Client client;
    private final ImprovedChatConfig config;
    private final SecondarySize swapSize;
    private final ChatBackgroundGraphic bgGraphic;
    private final PrivateMessageSplit pmSplit;
    private final ChatDialogBoxes dialogBoxes;
    private final TopLevelModals mainModals;
    private final RuneLiteHudAnchors hudAnchors;

    private int lastOpenTab;
    private boolean lastCollapsed;
    private boolean relayoutNeeded;
    private boolean enablePending;
    private int lastAppliedWidth = -1;
    private int lastAppliedHeight = -1;

    @Inject
    ResizableModeChat(
        Client client, ImprovedChatConfig config, SecondarySize swapSize,
        ChatBackgroundGraphic bgGraphic, PrivateMessageSplit pmSplit,
        ChatDialogBoxes dialogBoxes, TopLevelModals mainModals,
        RuneLiteHudAnchors hudAnchors
    ) {
        this.client = client;
        this.config = config;
        this.swapSize = swapSize;
        this.bgGraphic = bgGraphic;
        this.pmSplit = pmSplit;
        this.dialogBoxes = dialogBoxes;
        this.mainModals = mainModals;
        this.hudAnchors = hudAnchors;
    }

    void onEnable() {
        if (apply(swapSize.effectiveHeightChange() == 0 && swapSize.effectiveWidthChange() == 0) == null) {
            enablePending = true;
        } else {
            ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
            mainModals.relayout();
            consumeRelayoutNeeded();
        }
    }

    boolean consumeEnablePending() {
        boolean pending = enablePending;
        enablePending = false;
        return pending;
    }

    Dimension apply(boolean force) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return null;
        Widget slot = universe.getParent();
        if (slot == null || slot.getId() == InterfaceID.Toplevel.CHAT_CONTAINER) return null;

        boolean dialogOpen = dialogBoxes.isDialogOpen();
        int widthChange = SizeClamps.clamp(swapSize.effectiveWidthChange(), false, false, dialogOpen, config);
        int heightChange = SizeClamps.clamp(effectiveHeightChange(dialogOpen), false, mainModals.isModalOpen(), dialogOpen, config);

        int openTab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        boolean collapsed = openTab == RawScripts.COLLAPSED_TAB;
        if (!collapsed) lastOpenTab = openTab;
        if (collapsed != lastCollapsed) {
            lastCollapsed = collapsed;
            relayoutNeeded = true;
        }

        int slotW = ChatGeometry.CHATBOX_SPRITE_W + widthChange;

        int slotH = Math.max(0, ChatGeometry.CHATBOX_SLOT_H + heightChange);
        int backgroundH = Math.max(0, ChatGeometry.CHATBOX_SPRITE_H + heightChange);

        if (slotW != lastAppliedWidth || slotH != lastAppliedHeight) {
            lastAppliedWidth = slotW;
            lastAppliedHeight = slotH;
            relayoutNeeded = true;
        }

        hudAnchors.sync(heightChange);
        if (hudAnchors.consumeLayoutChanged()) {
            relayoutNeeded = true;
        }

        if (!force &&
            slot.getWidth() == slotW && slot.getHeight() == slotH &&
            universe.getWidth() == slotW && universe.getHeight() == slotH &&
            chatArea.getWidth() == slotW &&
            bgGraphic.tabBarMatches(widthChange)
        ) {
            bgGraphic.resizeTabBar(widthChange);
            bgGraphic.syncBackground(slotW, backgroundH, dialogOpen);
            if (dialogOpen) dialogBoxes.centerDialogs();
            bgGraphic.syncBorder(chatArea, false, dialogOpen);
            pmSplit.resizePmBox(slotW);
            sizeHpBarBand(slotH);
            return new Dimension(slotW, slotH);
        }

        slot.setSize(slotW, slotH);
        slot.revalidate();

        universe.setSize(slotW, slotH, WidgetSizeMode.ABSOLUTE, WidgetSizeMode.ABSOLUTE);
        universe.setForcedPosition(0, 0);
        universe.revalidate();

        chatArea.setOriginalWidth(slotW);

        bgGraphic.resizeTabBar(widthChange);
        Widgets.revalidateChildren(universe);
        bgGraphic.syncBorder(chatArea, true, dialogOpen);
        bgGraphic.syncBackground(slotW, backgroundH, dialogOpen);
        if (dialogOpen) dialogBoxes.centerDialogs();
        pmSplit.resizePmBox(slotW);
        sizeHpBarBand(slotH);

        return new Dimension(slotW, slotH);
    }

    void restore() {
        lastCollapsed = false;
        relayoutNeeded = false;
        enablePending = false;
        lastAppliedWidth = -1;
        lastAppliedHeight = -1;

        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return;

        Widget slot = universe.getParent();
        if (slot != null && slot.getId() != InterfaceID.Toplevel.CHAT_CONTAINER) {
            slot.setSize(ChatGeometry.CHATBOX_SPRITE_W, ChatGeometry.CHATBOX_SLOT_H);

            slot.revalidate();
        }

        universe.setSize(0, 0, WidgetSizeMode.MINUS, WidgetSizeMode.MINUS);
        universe.setForcedPosition(-1, -1);
        universe.revalidate();

        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea != null) chatArea.setOriginalWidth(ChatGeometry.CHATBOX_SPRITE_W);

        bgGraphic.resizeTabBar(0);
        bgGraphic.destroyBorder();
        pmSplit.restorePmBox();
        Widgets.revalidateChildren(universe);
        bgGraphic.revertBackground();
        dialogBoxes.resetDialogPositions();
        hudAnchors.restore();
        hudAnchors.consumeLayoutChanged();

        Widget dodger = client.getWidget(InterfaceID.HpbarHud.HPDODGER);
        if (dodger != null) dodger.revalidate();
    }

    void restoreBackground() {
        bgGraphic.revertBackground();
    }

    void toggleHidden() {
        int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        client.runScript(RawScripts.CHAT_TAB_CLICKED, 1, tab == RawScripts.COLLAPSED_TAB ? lastOpenTab : tab);
    }

    private void sizeHpBarBand(int slotH) {
        Widget dodger = client.getWidget(InterfaceID.HpbarHud.HPDODGER);
        if (dodger == null) return;
        Widget root = dodger.getParent();
        if (root == null) return;

        int h = Math.max(0, root.getHeight() - slotH - dodger.getRelativeY());
        if (dodger.getHeight() != h) Widgets.setHeight(dodger, h);
    }

    boolean consumeRelayoutNeeded() {
        boolean needed = relayoutNeeded;
        relayoutNeeded = false;
        return needed;
    }

    boolean isCollapsed() {
        return client.getVarcIntValue(VarClientID.CHAT_VIEW) == RawScripts.COLLAPSED_TAB;
    }

    int effectiveHeightChange(boolean dialogOpen) {
        int configured = swapSize.effectiveHeightChange();
        return isCollapsed() && !dialogOpen ? Math.min(-ChatGeometry.CHATBOX_SPRITE_H, configured) : configured;
    }
}