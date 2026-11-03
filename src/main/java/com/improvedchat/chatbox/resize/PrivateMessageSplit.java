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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PrivateMessageSplit {
    private final Client client;
    private final ImprovedChatConfig config;

    private boolean pmBoxResized;

    @Inject
    PrivateMessageSplit(Client client, ImprovedChatConfig config) {
        this.client = client;
        this.config = config;
    }

    void resizePmBox(int slotW) {
        if (!config.rewrapPrivateChat()) {
            restorePmBox();
        } else {
            pmBoxResized = true;
            setPmBoxWidth(slotW);
        }
    }

    void restorePmBox() {
        if (!pmBoxResized) return;

        pmBoxResized = false;
        setPmBoxWidth(ChatGeometry.CHATBOX_SPRITE_W);
    }

    void setPmBoxHeight(int height) {
        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return;
        if (pmChat.getHeight() == height) return;

        Widgets.setHeight(pmChat, height);
        Widgets.revalidateChildren(pmChat);
    }

    private void setPmBoxWidth(int slotW) {
        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return;
        Widget pmContainer = pmChat.getParent();
        if (pmContainer == null) return;

        if (pmContainer.getWidthMode() != WidgetSizeMode.ABSOLUTE) return;

        if (pmContainer.getOriginalWidth() != slotW) {
            pmContainer.setOriginalWidth(slotW);
            pmContainer.revalidate();
        }

        if (pmChat.getWidth() != slotW) Widgets.setWidth(pmChat, slotW);
    }
}