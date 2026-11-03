/*
 * Adapted portions from Remove Chat Options by Andrew Jones.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, Andrew Jones
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
package com.improvedchat.chatbox.menu;

import com.improvedchat.ImprovedChatConfig;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public final class RemoveChatOptionsModule {
    private static final int REPORT_BUTTON_COMPONENT_ID = 10616863;

    private static final Set<Integer> PRESERVED_CHAT_COMPONENTS = Set.of(
        ComponentID.CHATBOX_TAB_PRIVATE,
        ComponentID.CHATBOX_TAB_ALL,
        ComponentID.CHATBOX_TAB_CHANNEL,
        ComponentID.CHATBOX_TAB_CLAN,
        ComponentID.CHATBOX_TAB_GAME,
        ComponentID.CHATBOX_TAB_TRADE,
        ComponentID.CHATBOX_TAB_PUBLIC,
        ComponentID.CHATBOX_GE_SEARCH_RESULTS,
        REPORT_BUTTON_COMPONENT_ID
    );

    private static final Set<String> EXTERNAL_CHAT_OPTIONS = Set.of("Copy to clipboard");

    @Inject private Client client;
    @Inject private ImprovedChatConfig config;
    @Inject private EventBus eventBus;

    private boolean started;

    public synchronized void startUp() {
        if (started || !config.enableRemoveChatOptions()) return;
        started = true;
        eventBus.register(this);
    }

    public synchronized void shutDown() {
        if (!started) return;
        started = false;
        eventBus.unregister(this);
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (!started || !config.removeLookupChatOption()
            || client.isKeyPressed(KeyCode.KC_CONTROL)
            || !isMouseOverChatMessages()) {
            return;
        }

        MenuEntry[] entries = event.getMenuEntries();
        List<MenuEntry> kept = new LinkedList<>();
        for (MenuEntry entry : entries) {
            if (entry.getType() == MenuAction.RUNELITE && "Lookup".equals(entry.getOption())) {
                continue;
            }
            kept.add(entry);
        }

        MenuEntry[] filtered = kept.toArray(new MenuEntry[0]);
        event.setMenuEntries(filtered);
        client.setMenuEntries(filtered);
    }

    private boolean isMouseOverChatMessages() {
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) return false;
        Widget chatLines = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
        if (chatLines != null && !chatLines.isHidden()
            && chatLines.getBounds().contains(mouse.getX(), mouse.getY())) {
            return true;
        }

        Widget privateChat = client.getWidget(net.runelite.api.gameval.InterfaceID.PM_CHAT, 0);
        return privateChat != null && !privateChat.isHidden()
            && privateChat.getBounds().contains(mouse.getX(), mouse.getY());
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!started) return;

        if (client.isKeyPressed(KeyCode.KC_CONTROL)) return;

        MenuEntry[] entries = client.getMenuEntries();
        List<MenuEntry> kept = new LinkedList<>();
        for (MenuEntry entry : entries) {
            int componentId = entry.getParam1();
            int groupId = WidgetUtil.componentToInterface(componentId);
            if ((groupId != InterfaceID.CHATBOX || PRESERVED_CHAT_COMPONENTS.contains(componentId))
                && groupId != InterfaceID.PRIVATE_CHAT
                && !(entry.getType() == MenuAction.RUNELITE
                    && EXTERNAL_CHAT_OPTIONS.contains(net.runelite.client.util.Text.removeTags(entry.getOption()))
                    && (componentId <= 0 || groupId == InterfaceID.CHATBOX || groupId == InterfaceID.PRIVATE_CHAT)
                    && isMouseOverChatMessages())) {
                kept.add(entry);
            }
        }
        client.setMenuEntries(kept.toArray(new MenuEntry[0]));
    }
}
