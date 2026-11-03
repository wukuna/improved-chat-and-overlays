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
import com.improvedchat.chatbox.resize.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatBackgroundGraphic {

    private static final int FRAME_RAIL = 2;
    private static final int FRAME_GRIP = 20;
    private static final int FRAME_COLOR = 0x4B5563;

    private static final int TAB_STOCK_W = 56;
    private static final int TAB_STOCK_GAP = 6;
    private static final int TAB_STOCK_MARGIN = 5;
    private static final int[] TAB_STOCK_X = { 458, 396, 334, 272, 210, 148, 86 };
    @Component private static final int[] CHAT_TAB_BUTTONS = {
        InterfaceID.Chatbox.CHAT_ALL,
        InterfaceID.Chatbox.CHAT_GAME,
        InterfaceID.Chatbox.CHAT_PUBLIC,
        InterfaceID.Chatbox.CHAT_PRIVATE,
        InterfaceID.Chatbox.CHAT_FRIENDSCHAT,
        InterfaceID.Chatbox.CHAT_CLAN,
        InterfaceID.Chatbox.CHAT_TRADE,
    };
    @Component private static final int[] CHAT_TAB_GRAPHICS = {
        InterfaceID.Chatbox.CHAT_ALL_GRAPHIC,
        InterfaceID.Chatbox.CHAT_GAME_GRAPHIC,
        InterfaceID.Chatbox.CHAT_PUBLIC_GRAPHIC,
        InterfaceID.Chatbox.CHAT_PRIVATE_GRAPHIC,
        InterfaceID.Chatbox.CHAT_FRIENDSCHAT_GRAPHIC,
        InterfaceID.Chatbox.CHAT_CLAN_GRAPHIC,
        InterfaceID.Chatbox.CHAT_TRADE_GRAPHIC,
    };

    private static final int ALIGN_SHIFT = 1;
    private static final int MINIMUM_GAP = 10;

    private static final int BACKGROUND_STRETCH_X = 40;
    private static final int BACKGROUND_STRETCH_Y = 75;
    private static final int CORNER_BLEED_TRIM = 1;

    private final Client client;
    private final ImprovedChatConfig config;

    private Widget[] borderPieces;
    private boolean trimmed;
    private boolean zoomed;

    @Inject
    ChatBackgroundGraphic(Client client, ImprovedChatConfig config) {
        this.client = client;
        this.config = config;
    }

    void syncBackground(int targetW, int targetH, boolean dialogOpen) {
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (background == null) return;
        Widget body = getBackgroundBody(background);

        if (dialogOpen) {
            untrimBackground(background);
            if (isParchment(body)) {
                unzoomBakedSprite(body);
            }
            return;
        }

        if (!isParchment(body)) {
            untrimBackground(background);
            stackGradientBands(background, targetH);
            return;
        }

        if (config.noBackgroundZoom()) {
            unzoomBakedSprite(body);
        } else {
            zoomBakedSprite(body, targetW, targetH);
        }

        if (config.noBackgroundZoom() || config.noBorders()) {
            untrimBackground(background);
        } else {
            trimBackground(background);
        }
    }

    void revertBackground() {
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (background == null) return;
        untrimBackground(background);
        Widget body = getBackgroundBody(background);
        if (isParchment(body)) {
            unzoomBakedSprite(body);
        } else {
            stackGradientBands(background, ChatGeometry.CHATBOX_SPRITE_H);
        }
    }

    private void zoomBakedSprite(Widget body, int targetW, int targetH) {

        Widgets.setWidth(body, targetW + BACKGROUND_STRETCH_X * 2);
        Widgets.setHeight(body, targetH + BACKGROUND_STRETCH_Y * 2);
        body.setForcedPosition(body.getOriginalX() - BACKGROUND_STRETCH_X, body.getOriginalY() - BACKGROUND_STRETCH_Y);
        body.setSpriteTiling(false);
        zoomed = true;
    }

    private void trimBackground(Widget background) {
        background.setSize(CORNER_BLEED_TRIM, CORNER_BLEED_TRIM);
        background.setForcedPosition(0, CORNER_BLEED_TRIM);
        background.revalidate();
        trimmed = true;
    }

    private void unzoomBakedSprite(Widget body) {
        if (!zoomed) return;
        zoomed = false;
        body.setSpriteTiling(true);
        body.setForcedPosition(-1, -1);
        body.revalidate();
    }

    private void untrimBackground(Widget background) {
        if (!trimmed) return;
        trimmed = false;
        background.setSize(0, 0);
        background.setForcedPosition(-1, -1);
        background.revalidate();
    }

    private static void stackGradientBands(Widget background, int targetH) {
        Widget[] bands = background.getDynamicChildren();
        if (bands == null || bands.length == 0) return;
        if (background.getHeight() != targetH) return;
        if (targetH < bands.length) return;

        int n = bands.length, bandH = targetH / n;
        for (int i = 0; i < n; i++) {
            Widget band = bands[i];
            if (band == null || band.getHeightMode() != WidgetSizeMode.ABSOLUTE) continue;
            int y = bandH * i, h = i == n - 1 ? targetH - y : bandH;
            if (band.getOriginalY() == y && band.getOriginalHeight() == h) continue;
            band.setOriginalY(y);
            band.setOriginalHeight(h);
            band.revalidate();
        }
    }

    private void drawBorder(Widget chatArea, Widget parchment) {
        if (!borderPresent(chatArea)) {
            borderPieces = new Widget[6];
            for (int i = 0; i < borderPieces.length; i++) {
                Widget piece = chatArea.createChild(-1, WidgetType.RECTANGLE);
                piece.setFilled(true);
                borderPieces[i] = piece;
            }
        }

        int w = Math.max(1, chatArea.getWidth());
        int h = Math.max(1, chatArea.getHeight());
        int gripColor = config.indicatorColor().getRGB() & 0xFFFFFF;

        int[][] rects = {
            { 0, 0, w, FRAME_RAIL },
            { 0, Math.max(0, h - FRAME_RAIL), w, FRAME_RAIL },
            { 0, 0, FRAME_RAIL, h },
            { Math.max(0, w - FRAME_RAIL), 0, FRAME_RAIL, h },
            { Math.max(0, (w - FRAME_GRIP) / 2), 0, FRAME_GRIP, 4 },
            { Math.max(0, w - 4), Math.max(0, (h - FRAME_GRIP) / 2), 4, FRAME_GRIP },
        };

        for (int i = 0; i < rects.length; i++) {
            Widget piece = borderPieces[i];
            piece.setTextColor(i < 4 ? FRAME_COLOR : gripColor);
            piece.setOriginalX(rects[i][0]);
            piece.setOriginalY(rects[i][1]);
            piece.setSize(Math.max(1, rects[i][2]), Math.max(1, rects[i][3]));
            piece.revalidate();
        }

        syncBorderVisibility(parchment);
    }

    void destroyBorder() {
        if (borderPieces == null) return;

        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return;
        Widget[] children = chatArea.getChildren();
        if (children == null) return;

        for (Widget borderPiece : borderPieces) {
            if (borderPiece != null) {
                int idx = borderPiece.getIndex();
                if (idx >= 0 && idx < children.length) children[idx] = null;
            }
        }

        borderPieces = null;
    }

    private boolean borderPresent(Widget chatArea) {
        if (borderPieces == null) return false;

        for (Widget borderPiece : borderPieces) {
            if (borderPiece == null) return false;
            if (chatArea.getChild(borderPiece.getIndex()) != borderPiece) return false;
        }

        return true;
    }

    void syncBorder(Widget chatArea, boolean reposition, boolean dialogOpen) {
        if (dialogOpen || config.noBorders()) {
            destroyBorder();
            return;
        }

        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        Widget body = background == null ? null : getBackgroundBody(background);

        if (body == null) {
            syncBorderVisibility(null);
        } else if (!isParchment(body)) {
            destroyBorder();
        } else if (reposition || !borderPresent(chatArea)) {
            drawBorder(chatArea, body);
        } else {
            syncBorderVisibility(body);
        }
    }

    private void syncBorderVisibility(Widget parchment) {
        if (borderPieces == null) return;

        boolean hidden = parchment == null || parchment.isHidden();
        for (Widget piece : borderPieces) if (piece != null && piece.isSelfHidden() != hidden) piece.setHidden(hidden);
    }

    void resizeTabBar(int dw) {
        Widget controls = client.getWidget(InterfaceID.Chatbox.CONTROLS);
        Widget graphic = client.getWidget(InterfaceID.Chatbox.CONTROLS_BACKGROUND_GRAPHIC);
        if (controls == null || graphic == null) return;

        Widget[] tabs = getTabs();
        if (tabs == null) return;

        controls.setOriginalWidth(ChatGeometry.CHATBOX_SPRITE_W + dw);
        graphic.setOriginalWidth(ChatGeometry.CHATBOX_SPRITE_W + dw);

        boolean resize = config.resizeTabButtons();
        boolean measure = resize && dw != 0;
        int n = tabs.length, stonesTW = stonesTotal(dw, n), gapsTW = gapsTotal(dw, n);
        for (int i = 0; i < n; i++) {
            Widget[] tabSC = tabs[i].getStaticChildren();
            int w = resize ? tabTargetW(i, stonesTW, n) : TAB_STOCK_W;
            tabs[i].setOriginalWidth(w);
            tabs[i].setOriginalX(resize ? tabStretchX(i, dw, stonesTW, gapsTW, n) : tabSpreadX(i, dw, n));
            if (tabSC.length > 0 && tabSC[0] != null) tabSC[0].setOriginalWidth(w);
            if (tabSC.length > 1 && tabSC[1] != null) alignLabel(tabSC[1], measure, w);
            if (tabSC.length > 2 && tabSC[2] != null) alignLabel(tabSC[2], measure, w);
        }
    }

    boolean tabBarMatches(int dw) {
        Widget controls = client.getWidget(InterfaceID.Chatbox.CONTROLS);
        if (controls == null) return true;
        Widget[] tabs = getTabs();
        if (tabs == null) return true;

        if (controls.getWidth() != ChatGeometry.CHATBOX_SPRITE_W + dw) return false;

        boolean resize = config.resizeTabButtons();
        int n = tabs.length, stonesTW = stonesTotal(dw, n), gapsTW = gapsTotal(dw, n);
        for (int i = 0; i < n; i++) {
            int w = resize ? tabTargetW(i, stonesTW, n) : TAB_STOCK_W;
            if (tabs[i].getOriginalWidth() != w) return false;
            int x = resize ? tabStretchX(i, dw, stonesTW, gapsTW, n) : tabSpreadX(i, dw, n);
            if (tabs[i].getOriginalX() != x) return false;
            Widget stone = client.getWidget(CHAT_TAB_GRAPHICS[i]);
            if (stone != null && stone.getOriginalWidth() != w) return false;
        }

        return true;
    }

    static int tabIndexOf(@Component int componentId) {
        for (int i = 0; i < CHAT_TAB_BUTTONS.length; i++) if (CHAT_TAB_BUTTONS[i] == componentId) return i;
        return -1;
    }

    private Widget getBackgroundBody(Widget background) {
        Widget[] dynamic = background.getDynamicChildren();
        if (dynamic == null || dynamic.length == 0) return null;
        return dynamic[0];
    }

    private static boolean isParchment(Widget body) {
        return body != null && body.getType() == WidgetType.GRAPHIC;
    }

    private Widget[] getTabs() {
        Widget[] tabs = new Widget[CHAT_TAB_BUTTONS.length];
        for (int i = 0; i < CHAT_TAB_BUTTONS.length; i++) {
            tabs[i] = client.getWidget(CHAT_TAB_BUTTONS[i]);
            if (tabs[i] == null) return null;
            if (tabs[i].getXPositionMode() != WidgetPositionMode.ABSOLUTE_RIGHT) return null;
        }
        return tabs;
    }

    private static int tabSpace(int dw, int n) {
        return n * TAB_STOCK_W + (n - 1) * TAB_STOCK_GAP + dw;
    }

    private static int gapsTotal(int dw, int n) {
        return Math.max(MINIMUM_GAP, Math.min((n - 1) * TAB_STOCK_GAP, tabSpace(dw, n) - n * TAB_STOCK_W));
    }

    private static int stonesTotal(int dw, int n) {
        return Math.max(n, tabSpace(dw, n) - gapsTotal(dw, n));
    }

    private static int tabTargetW(int i, int stones, int n) {
        return stones * (i + 1) / n - stones * i / n;
    }

    private static int tabSpreadX(int i, int dw, int n) {
        return TAB_STOCK_X[i] + dw * (n - i) / n;
    }

    private static int tabStretchX(int i, int dw, int stones, int gaps, int n) {
        int x = TAB_STOCK_MARGIN + stones * i / n + gaps * i / (n - 1);
        return ChatGeometry.CHATBOX_SPRITE_W + dw - x - tabTargetW(i, stones, n);
    }

    private static void alignLabel(Widget label, boolean measure, int stoneW) {
        int alignment = measure && labelOverflows(label, stoneW) ? WidgetTextAlignment.LEFT : WidgetTextAlignment.CENTER;
        int originalX = alignment == WidgetTextAlignment.LEFT ? ALIGN_SHIFT : 0;
        if (label.getXTextAlignment() == alignment && label.getOriginalX() == originalX) return;
        label.setXTextAlignment(alignment);
        label.setOriginalX(originalX);
        label.revalidate();
    }

    private static boolean labelOverflows(Widget label, int stoneW) {
        FontTypeFace font = label.getFont();
        String text = label.getText();
        return font != null && text != null && font.getTextWidth(text) > stoneW;
    }
}