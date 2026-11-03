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

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.opacity.ChatboxOpacityModule;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import com.improvedchat.chatbox.resize.ChatResizeModule;
import com.improvedchat.chatbox.resize.internal.RawScripts;

@Slf4j
@Singleton
public class ChatCollapseModule {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ChatWidgetManager widgetManager;

    private final ChatState state = new ChatState();

    @Inject
    private ImprovedChatConfig config;
    @Inject
    private EventBus eventBus;
    @Inject
    private ChatboxOpacityModule chatboxOpacityModule;
    @Inject
    private KeyManager keyManager;
    @Inject
    private ChatResizeModule chatResizeModule;

    private boolean started;
    private int lastOpenTab;

    private final HotkeyListener collapseHotkey = new HotkeyListener(() -> config.toggleShowChat()) {
        @Override public void hotkeyPressed() {
            clientThread.invoke(ChatCollapseModule.this::toggleChat);
        }
    };

    void toggleChat() {
        if (!started || client.getGameState() != GameState.LOGGED_IN) return;
        if (config.enableResizableChat()) {
            chatResizeModule.toggleChat();
        } else if (client.isResized()) {
            int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
            if (tab >= 0 && tab <= 6) lastOpenTab = tab;
            client.runScript(RawScripts.CHAT_TAB_CLICKED, 1,
                tab == RawScripts.COLLAPSED_TAB ? lastOpenTab : tab);
        }
    }

    public synchronized void startUp() {
        if (started || !config.enableCollapsibleChat()) {
            return;
        }
        started = true;
        lastOpenTab = 0;
        keyManager.registerKeyListener(collapseHotkey);
        state.reset();
        eventBus.register(this);
        clientThread.invokeLater(this::refreshAll);
    }

    public synchronized void shutDown() {
        if (!started) {
            return;
        }
        started = false;
        keyManager.unregisterKeyListener(collapseHotkey);
        eventBus.unregister(this);
        state.reset();
        clientThread.invokeLater(() -> {
            refreshChatWidgets();
            client.runScript(113);
            chatboxOpacityModule.reapplyAfterChatMutation();
        });
    }

    private void refreshAll() {
        widgetManager.setupMouseListeners(state, this::refreshChatWidgets);
        updateChatState();
        refreshChatWidgets();
    }

    private void refreshChatWidgets() {
        widgetManager.updateChatWidgets(state);
        chatboxOpacityModule.reapplyAfterChatMutation();
    }

    @Subscribe(priority = -1)
    public void onBeforeRender(BeforeRender event) {

        if (!started) return;
        updateChatState();
        widgetManager.updateChatWidgets(state);
    }

    @Subscribe
    void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.CHAT_PROMPT_INIT) {
            refreshAll();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!"Switch tab".equals(event.getMenuOption())) return;

        clientThread.invokeLater(() -> {
            updateChatState();
            refreshChatWidgets();
        });
    }

    @Subscribe
    void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() == VarClientID.CHAT_LASTREBUILD) {
            refreshAll();
        }
    }

    @Subscribe
    void onConfigChanged(ConfigChanged event) {
        if (ImprovedChatConfig.GROUP.equals(event.getGroup())) {
            clientThread.invokeLater(this::refreshChatWidgets);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (state.isCollapsed()) {
            if (isSubscribedToChatMessageType(event.getType())) {
                state.hasUnseenMessages = true;
            }
        }
        clientThread.invokeLater(this::refreshChatWidgets);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (config.collapsedButtonContent() == ImprovedChatConfig.CollapsedButtonContent.REPORT_BUTTON_TEXT) {
            clientThread.invokeLater(this::refreshChatWidgets);
        }
    }

    private boolean isUsingSplitPrivateChat() {
        return client.getVarpValue(VarPlayerID.OPTION_PM) == 1;
    }

    private boolean isPrivateChatHiddenWithChat() {
        return client.getVarbitValue(VarbitID.HIDE_PM_ALONGSIDE_CHATBOX) == 1;
    }

    private boolean arePrivateMessagesHiddenOnCollapse() {
        return !isUsingSplitPrivateChat() || isPrivateChatHiddenWithChat();
    }

    private boolean isSubscribedToChatMessageType(ChatMessageType messageType) {
        switch (messageType) {
            case PUBLICCHAT:
                return config.highlightOnUnreadPublicMessages();
            case PRIVATECHAT:
                return config.highlightOnUnreadPrivateMessages() && arePrivateMessagesHiddenOnCollapse();
            case CLAN_CHAT:
                return config.highlightOnUnreadClanChatMessages();
            case TRADE:
                return config.highlightOnUnreadTradeMessages();
            case FRIENDSCHAT:
                return config.highlightOnUnreadFriendsChatMessages();
            default:
                return false;
        }
    }

    private void updateChatState() {
        int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        if (tab >= 0 && tab <= 6) lastOpenTab = tab;
        state.selectedChatButton = widgetManager.getSelectedChatButton();
        if (state.selectedChatButton == null) {
            state.collapseState = ChatCollapseState.COLLAPSED;
        } else {
            state.collapseState = ChatCollapseState.EXPANDED;
            state.hasUnseenMessages = false;
        }
    }
}
