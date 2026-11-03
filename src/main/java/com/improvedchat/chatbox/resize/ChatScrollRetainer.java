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

import com.improvedchat.chatbox.resize.internal.RawScripts;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ChatScrollRetainer {
    private final Client client;
    private final ChatDialogBoxes dialogBoxes;
    private final DragResizeActuator dragResize;

    private Integer lastViewport;
    private int lastWidth;
    private int lastContentH;
    private int distFromBottom;

    private int anchorId = -1;
    private int anchorOffset;
    private int anchorScrollY;
    private int anchorContentH;

    @Inject
    ChatScrollRetainer(Client client, ChatDialogBoxes dialogBoxes, DragResizeActuator dragResize) {
        this.client = client;
        this.dialogBoxes = dialogBoxes;
        this.dragResize = dragResize;
    }

    void reset() {
        lastViewport = null;
        lastWidth = 0;
        lastContentH = 0;
        distFromBottom = 0;
        clearAnchor();
    }

    void sync() {

        if (client.getVarcIntValue(VarClientID.CHAT_VIEW) == RawScripts.COLLAPSED_TAB) {
            reset();
            return;
        }

        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getHeight() <= 0 || scrollArea.getScrollHeight() <= 0) {
            lastViewport = null;
            clearAnchor();
            return;
        }

        int viewport = scrollArea.getHeight();
        int width = scrollArea.getWidth();
        int contentH = scrollArea.getScrollHeight();
        int scrollY = scrollArea.getScrollY();

        boolean reanchored = reanchor(scrollArea, viewport - contentH);
        if (reanchored) contentH = viewport;

        if (reanchored || (lastViewport != null && (viewport != lastViewport || width != lastWidth))) {

            Integer anchored = width != lastWidth ? anchorTarget(viewport, contentH) : null;
            if (anchored == null) anchored = Math.max(0, Math.min(Math.max(1, contentH - viewport), contentH - viewport - distFromBottom));
            client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, anchored);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, anchored);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE, contentH);
        } else if (!dialogBoxes.isDialogOpen() && !dragResize.isDragging()) {

            distFromBottom = contentH - scrollY - viewport;

            if (distFromBottom > 0) {
                if (scrollY != anchorScrollY || contentH != anchorContentH) captureAnchor(scrollArea, scrollY, contentH);
            } else {
                clearAnchor();
            }
        }

        lastViewport = viewport;
        lastWidth = width;
        lastContentH = contentH;
    }

    void noteRewrap() {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getScrollHeight() == lastContentH) return;
        lastWidth = Integer.MIN_VALUE;
        sync();
    }

    private static boolean reanchor(Widget scrollArea, int shift) {
        if (shift <= 0) return false;
        shiftDown(scrollArea.getStaticChildren(), shift);
        shiftDown(scrollArea.getDynamicChildren(), shift);
        scrollArea.setScrollHeight(scrollArea.getHeight());
        return true;
    }

    private static void shiftDown(Widget[] children, int shift) {
        if (children == null) return;
        for (Widget child : children) {
            if (child == null || child.getHeight() <= 0) continue;
            child.setOriginalY(child.getOriginalY() + shift);
            child.revalidate();
        }
    }

    private void captureAnchor(Widget scrollArea, int scrollY, int contentH) {
        clearAnchor();
        anchorScrollY = scrollY;
        anchorContentH = contentH;
        Widget[] rows = scrollArea.getStaticChildren();
        if (rows == null) return;
        Widget top = null;
        for (Widget row : rows) {

            if (row == null || row.getHeight() <= 0 || row.getId() < InterfaceID.Chatbox.LINE0) continue;
            int y = row.getRelativeY();
            if (y <= scrollY && (top == null || y > top.getRelativeY())) top = row;
        }
        if (top == null) return;
        anchorId = top.getId();
        anchorOffset = scrollY - top.getRelativeY();
    }

    private Integer anchorTarget(int viewport, int contentH) {
        if (anchorId == -1) return null;
        Widget row = client.getWidget(anchorId);
        if (row == null || row.getHeight() <= 0) return null;
        int offset = Math.min(anchorOffset, row.getHeight());
        int target = row.getRelativeY() + offset;
        return Math.max(0, Math.min(Math.max(1, contentH - viewport), target));
    }

    private void clearAnchor() {
        anchorId = -1;
        anchorOffset = 0;
        anchorScrollY = -1;
        anchorContentH = -1;
    }
}