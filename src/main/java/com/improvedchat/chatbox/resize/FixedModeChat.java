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
import net.runelite.api.WidgetNode;
import net.runelite.api.annotations.Component;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.eventbus.Subscribe;
import lombok.Getter;
import lombok.Setter;
import java.awt.Dimension;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FixedModeChat {
    private static final int STOCK_Y = ChatGeometry.FIXED_CHAT_Y;
    private static final int STOCK_W = ChatGeometry.CHATBOX_SPRITE_W;
    private static final int STOCK_H = ChatGeometry.CHATBOX_SLOT_H;
    private static final int TAB_BAR_H = STOCK_H - ChatGeometry.CHATBOX_SPRITE_H;
    private static final int STOCK_BOTTOM = STOCK_Y + STOCK_H;

    private static final int MAIN_TOP = 4;
    private static final int STOCK_MAIN_H = 334;

    private static final int RIGHT_BORDER_TOP = 205;
    private static final int RIGHT_BORDER_H = 133;

    private static final String SWITCH_TAB = "Switch tab";

    private final Client client;
    private final ImprovedChatConfig config;
    private final SecondarySize swapSize;
    private final ChatBackgroundGraphic bgGraphic;
    private final PrivateMessageSplit pmSplit;
    private final ChatDialogBoxes dialogBoxes;
    private final TopLevelModals mainModals;

    private int lastTargetY = STOCK_Y;
    private boolean relayoutNeeded;
    private boolean rebuildNeeded;

    @Getter @Setter private boolean collapsed;
    private int savedTab = -1;

    @Inject
    FixedModeChat(
        Client client, ImprovedChatConfig config, SecondarySize swapSize,
        ChatBackgroundGraphic bgGraphic, PrivateMessageSplit pmSplit,
        ChatDialogBoxes dialogBoxes, TopLevelModals mainModals
    ) {
        this.client = client;
        this.config = config;
        this.swapSize = swapSize;
        this.bgGraphic = bgGraphic;
        this.pmSplit = pmSplit;
        this.dialogBoxes = dialogBoxes;
        this.mainModals = mainModals;
    }

    void onEnable() {
        apply(true);
        if (consumeRebuildNeeded()) ChatRebuild.now(client, RawScripts.REWRAPS_CHAT);
    }

    Dimension apply(boolean force) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget slot = universe.getParent();
        if (slot == null || slot.getId() != InterfaceID.Toplevel.CHAT_CONTAINER) return null;
        Widget chatArea = getChatArea(universe);
        if (chatArea == null || chatArea.getId() != InterfaceID.Chatbox.CHATAREA) return null;

        boolean modalOpen = mainModals.isModalOpen();
        boolean dialogOpen = dialogBoxes.isDialogOpen();
        int rawHeightChange = effectiveHeightChange(dialogOpen);
        int heightChange = SizeClamps.clamp(rawHeightChange, true, modalOpen, dialogOpen, config);

        int heldHeightChange = dialogOpen && !modalOpen ? effectiveHeightChange(false) : heightChange;

        int targetH = Math.min(STOCK_BOTTOM, Math.max(0, STOCK_H + heightChange));
        int targetY = STOCK_BOTTOM - targetH;
        int heldH = Math.min(STOCK_BOTTOM, Math.max(0, STOCK_H + heldHeightChange));
        int heldY = STOCK_BOTTOM - heldH;

        if (targetY != lastTargetY) {
            lastTargetY = targetY;
            rebuildNeeded = true;
            if (mainModals.isTopLevelModalOpen()) relayoutNeeded = true;
        }

        int backgroundH = Math.max(0, targetH - TAB_BAR_H);
        int minViewH = config.fixedAdjustViewport() ? 0 : STOCK_MAIN_H;
        int chatBandH = Math.max(minViewH, targetY - MAIN_TOP);
        int mainH = Math.max(chatBandH, heldY - MAIN_TOP);
        int viewportBottom = MAIN_TOP + mainH;
        int pmH = targetY - MAIN_TOP;

        Widget main = client.getWidget(InterfaceID.Toplevel.MAIN);

        if (!force &&
            slot.getHeight() == targetH && slot.getRelativeY() == targetY &&
            universe.getWidth() == STOCK_W && universe.getHeight() == targetH &&
            (main == null || main.getHeight() == mainH)
        ) {
            bgGraphic.syncBackground(STOCK_W, backgroundH, dialogOpen);
            bgGraphic.syncBorder(chatArea, false, dialogOpen);
            extendViewportBorders(viewportBottom);
            pmSplit.setPmBoxHeight(pmH);
            if (dialogOpen) dialogBoxes.centerDialogs();
            return new Dimension(STOCK_W, targetH);
        }

        sizeChat(slot, universe, targetY, targetH);
        sizeViewport(main, mainH);
        extendViewportBorders(viewportBottom);
        bgGraphic.syncBorder(chatArea, true, dialogOpen);
        bgGraphic.syncBackground(STOCK_W, backgroundH, dialogOpen);
        pmSplit.setPmBoxHeight(pmH);
        if (dialogOpen) dialogBoxes.centerDialogs();
        return new Dimension(STOCK_W, targetH);
    }

    boolean consumeRelayoutNeeded() {
        boolean needed = relayoutNeeded;
        relayoutNeeded = false;
        return needed;
    }

    boolean consumeRebuildNeeded() {
        boolean needed = rebuildNeeded;
        rebuildNeeded = false;
        return needed;
    }

    void restore() {
        collapsed = false;
        if (savedTab != -1 && client.getVarcIntValue(VarClientID.CHAT_VIEW) == RawScripts.COLLAPSED_TAB)
            client.setVarcIntValue(VarClientID.CHAT_VIEW, savedTab);
        savedTab = -1;
        lastTargetY = STOCK_Y;
        relayoutNeeded = false;
        rebuildNeeded = false;
        Widget slot = client.getWidget(InterfaceID.Toplevel.CHAT_CONTAINER);
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (slot != null && !isStock(slot, universe)) sizeChat(slot, universe, STOCK_Y, STOCK_H);
        sizeViewport(client.getWidget(InterfaceID.Toplevel.MAIN), STOCK_MAIN_H);
        extendViewportBorders(STOCK_Y);
        pmSplit.setPmBoxHeight(STOCK_Y - MAIN_TOP);
        dialogBoxes.resetDialogPositions();
        bgGraphic.revertBackground();
        bgGraphic.destroyBorder();
    }

    void syncCollapsedTab() {
        int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        if (collapsed) {
            if (tab != RawScripts.COLLAPSED_TAB) {
                if (savedTab == -1) savedTab = tab;
                client.setVarcIntValue(VarClientID.CHAT_VIEW, RawScripts.COLLAPSED_TAB);
                client.runScript(RawScripts.REDRAW_CHAT_BUTTONS);
            }
        } else if (tab == RawScripts.COLLAPSED_TAB) {
            if (savedTab != -1) {
                client.runScript(RawScripts.CHAT_TAB_CLICKED, 1, savedTab);
                savedTab = -1;
            }
        } else {
            savedTab = -1;
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (client.isResized() || !config.fixedTabCollapse() || !event.getMenuOption().equals(SWITCH_TAB)) return;
        int tab = ChatBackgroundGraphic.tabIndexOf(event.getParam1());
        if (tab != -1) setCollapsed(!isCollapsed() && tab == client.getVarcIntValue(VarClientID.CHAT_VIEW));
    }

    int effectiveHeightChange(boolean dialogOpen) {
        int configured = swapSize.effectiveFixedHeightChange();
        return collapsed && !dialogOpen ? Math.min(-ChatGeometry.CHATBOX_SPRITE_H, configured) : configured;
    }

    private static boolean isStock(Widget slot, Widget universe) {
        return slot.getOriginalY() == STOCK_Y && slot.getOriginalHeight() == STOCK_H
            && (universe == null || (universe.getWidth() == STOCK_W && universe.getHeight() == STOCK_H));
    }

    private static void sizeChat(Widget slot, Widget universe, int y, int h) {
        slot.setOriginalY(y);
        slot.setOriginalHeight(h);
        slot.revalidate();
        if (universe == null) return;
        universe.setSize(STOCK_W, h, WidgetSizeMode.ABSOLUTE, WidgetSizeMode.ABSOLUTE);
        universe.revalidate();
        Widgets.revalidateChildren(universe);
    }

    private void sizeViewport(Widget main, int h) {
        if (main == null || main.getOriginalHeight() == h) return;
        main.setOriginalHeight(h);
        main.revalidate();
        Widgets.revalidateChildren(main);
        sizeAtmosphere();
    }

    private void sizeAtmosphere() {
        Widget slot = client.getWidget(InterfaceID.Toplevel.OVERLAY_ATMOSPHERE);
        if (slot == null) return;
        WidgetNode mounted = client.getComponentTable().get(InterfaceID.Toplevel.OVERLAY_ATMOSPHERE);
        if (mounted == null) return;
        Widget root = Widgets.mountedRoot(client, mounted.getId());
        if (root == null) return;
        Widgets.setWidth(root, slot.getWidth());
        Widgets.setHeight(root, slot.getHeight());
        Widgets.revalidateChildren(root);
    }

    private void extendViewportBorders(int vpB) {
        resizeBorder(InterfaceID.Toplevel.CONTROL, MAIN_TOP, STOCK_MAIN_H, vpB, false);
        resizeBorder(InterfaceID.Toplevel.GAMEFRAME_GRAPHIC5, RIGHT_BORDER_TOP, RIGHT_BORDER_H, vpB, true);
    }

    private void resizeBorder(@Component int widgetId, int top, int stockH, int bottom, boolean tile) {
        Widget border = client.getWidget(widgetId);
        if (border == null) return;
        int h = Math.max(stockH, bottom - top);
        if (border.getOriginalHeight() == h) return;
        if (tile) border.setSpriteTiling(true);
        border.setOriginalHeight(h);
        border.revalidate();
    }

    private Widget getChatArea(Widget universe) {
        Widget[] children = universe.getStaticChildren();
        return children == null || children.length < 2 ? null : children[1];
    }
}