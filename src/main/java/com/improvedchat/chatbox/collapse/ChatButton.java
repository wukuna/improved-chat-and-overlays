/*
 * Adapted portions from Collapse Chat by stutify.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, stutify
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

package com.improvedchat.chatbox.collapse;

import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.InterfaceID;

import java.util.Arrays;
import java.util.function.Predicate;

@RequiredArgsConstructor
public enum ChatButton {
    ALL(InterfaceID.Chatbox.CHAT_ALL, InterfaceID.Chatbox.CHAT_ALL_GRAPHIC, InterfaceID.Chatbox.CHAT_ALL_TEXT1, false),
    GAME(InterfaceID.Chatbox.CHAT_GAME, InterfaceID.Chatbox.CHAT_GAME_GRAPHIC, InterfaceID.Chatbox.CHAT_GAME_TEXT1, true),
    PUBLIC(InterfaceID.Chatbox.CHAT_PUBLIC, InterfaceID.Chatbox.CHAT_PUBLIC_GRAPHIC, InterfaceID.Chatbox.CHAT_PUBLIC_TEXT1, true),
    PRIVATE(InterfaceID.Chatbox.CHAT_PRIVATE, InterfaceID.Chatbox.CHAT_PRIVATE_GRAPHIC, InterfaceID.Chatbox.CHAT_PRIVATE_TEXT1, true),
    FRIENDS(InterfaceID.Chatbox.CHAT_FRIENDSCHAT, InterfaceID.Chatbox.CHAT_FRIENDSCHAT_GRAPHIC, InterfaceID.Chatbox.CHAT_FRIENDSCHAT_TEXT1, true),
    CLAN(InterfaceID.Chatbox.CHAT_CLAN, InterfaceID.Chatbox.CHAT_CLAN_GRAPHIC, InterfaceID.Chatbox.CHAT_CLAN_TEXT1, true),
    TRADE(InterfaceID.Chatbox.CHAT_TRADE, InterfaceID.Chatbox.CHAT_TRADE_GRAPHIC, InterfaceID.Chatbox.CHAT_TRADE_TEXT, true),
    REPORT(InterfaceID.Chatbox.REPORTABUSE, InterfaceID.Chatbox.REPORTABUSE_GRAPHIC, InterfaceID.Chatbox.REPORTABUSE_TEXT1, true);

    public final int containerID;
    public final int graphicID;
    public final int textID;
    public final boolean shouldToggle;

    public static final int[] CONTAINER_IDS_TO_TOGGLE;
    public static final int[] ALL_GRAPHIC_IDS;

    static {
        CONTAINER_IDS_TO_TOGGLE = Arrays.stream(values())
                .filter(button -> button.shouldToggle)
                .mapToInt(button -> button.containerID)
                .toArray();

        ALL_GRAPHIC_IDS = Arrays.stream(values())
                .mapToInt(button -> button.graphicID)
                .toArray();
    }

    public static ChatButton fromGraphicID(int graphicID) {
        return ChatButton.findOrNull(button -> button.graphicID == graphicID);
    }

    private static ChatButton findOrNull(Predicate<ChatButton> predicate) {
        return Arrays.stream(values())
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }
}