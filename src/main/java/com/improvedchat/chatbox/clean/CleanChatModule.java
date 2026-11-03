/*
 * Adapted portions from Clean Chat by ldavid432.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, ldavid432
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
package com.improvedchat.chatbox.clean;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.clean.overlay.ChatColorBarOverlay;
import com.improvedchat.chatbox.clean.overlay.ChatTimestampOverlay;
import com.improvedchat.chatbox.clean.util.FormatterExtractor;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timestamp.TimestampPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
public final class CleanChatModule {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ImprovedChatConfig config;
    @Inject private ChannelNameManager channelNameManager;
    @Inject private ChatWidgetEditor chatWidgetEditor;
    @Inject private ChatBlocker chatBlocker;
    @Inject private EventBus eventBus;
    @Inject private OverlayManager overlayManager;
    @Inject private PluginManager pluginManager;
    @Inject private ChatColorBarOverlay colorBarOverlay;
    @Inject private ChatTimestampOverlay timestampOverlay;

    private boolean started;
    private boolean timestampPluginEnabled;
    private FormatterExtractor.ExtractionResult timestampTemplate;
    private int timestampTemplateWidth;

    public synchronized void startUp() {
        if (started) {
            return;
        }
        started = true;
        timestampPluginEnabled = findTimestampPluginEnabled();

        eventBus.register(this);
        eventBus.register(chatBlocker);
        eventBus.register(chatWidgetEditor);
        eventBus.register(channelNameManager);
        channelNameManager.startup();

        eventBus.register(timestampOverlay);
        overlayManager.add(timestampOverlay);
        timestampOverlay.startUp();
        overlayManager.add(colorBarOverlay);

        clientThread.invoke(() -> {
            if (config.hideScrollbar()) {
                handleScrollbarVisibility(true);
            }
            if (client.getGameState() == GameState.LOGGED_IN && hasActiveCleanupSettings(config)) {
                client.refreshChat();
            }
        });
    }

    public synchronized void shutDown() {
        if (!started) {
            return;
        }
        started = false;

        eventBus.unregister(chatBlocker);
        eventBus.unregister(chatWidgetEditor);
        eventBus.unregister(channelNameManager);
        eventBus.unregister(timestampOverlay);
        eventBus.unregister(this);

        overlayManager.remove(timestampOverlay);
        overlayManager.remove(colorBarOverlay);
        channelNameManager.shutdown();

        clientThread.invoke(() -> {
            if (config.hideScrollbar()) {
                handleScrollbarVisibility(false);
            }
            if (client.getGameState() == GameState.LOGGED_IN && hasActiveCleanupSettings(config)) {
                client.refreshChat();
            }
        });
    }

    public boolean isFixedWidthTimestampEnabled() {
        return started && config.isFixedWidthTimestampEnabled() && timestampPluginEnabled;
    }

    public FormatterExtractor.ExtractionResult getTimestampTemplate() {
        return timestampTemplate;
    }

    public void setTimestampTemplate(FormatterExtractor.ExtractionResult timestampTemplate) {
        this.timestampTemplate = timestampTemplate;
    }

    public int getTimestampTemplateWidth() {
        return timestampTemplateWidth;
    }

    public void setTimestampTemplateWidth(int timestampTemplateWidth) {
        this.timestampTemplateWidth = timestampTemplateWidth;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (ImprovedChatConfig.GROUP.equals(event.getGroup())) {
            if (!started || !isCleanupConfigKey(event.getKey())) {
                return;
            }
            clientThread.invokeLater(() -> {
                if (ImprovedChatConfig.HIDE_SCROLLBAR_KEY.equals(event.getKey())) {
                    handleScrollbarVisibility(config.hideScrollbar());
                }
                client.refreshChat();
            });
            return;
        }

        if ("runelite".equals(event.getGroup()) && "timestampplugin".equals(event.getKey())) {
            timestampPluginEnabled = findTimestampPluginEnabled();
            clientThread.invokeLater(client::refreshChat);
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (started && event.getGroupId() == InterfaceID.CHATBOX) {
            handleScrollbarVisibility(config.hideScrollbar());
        }
    }

    private static boolean isCleanupConfigKey(String key) {
        if (key == null) {
            return false;
        }

        switch (key) {
            case "removeWelcome":
            case "lineBreakIndentationMode":
            case ImprovedChatConfig.HIDE_SCROLLBAR_KEY:
            case "hideSpecs":
            case "improvedTimestamps":
            case "colorBar":
            case "colorBarOffset":
            case "colorBarWidth":
            case "noChannelColor":
            case "clanColor":
            case "friendColor":
            case "groupIronColor":
            case "guestClanColor":
            case "removeClanInstruction":
            case "removeClanName":
            case "shortClanName":
            case "removeClanRank":
            case "removeGuestClanInstruction":
            case "removeGuestClanReconnecting":
            case "removeGuestClanName":
            case "shortGuestClanName":
            case "removeGroupIronInstruction":
            case "removeGroupIronName":
            case "moveGroupIronBroadcasts":
            case "shortGroupIronName":
            case "removeFriendsChatInstruction":
            case "removeFriendsChatName":
            case "removeFriendsAttempting":
            case "removeFriendsNowTalking":
            case "shortFriendsName":
                return true;
            default:
                return false;
        }
    }

    static boolean hasActiveCleanupSettings(ImprovedChatConfig config) {
        return config.removeWelcome()
            || config.indentationMode() != com.improvedchat.chatbox.clean.data.IndentMode.MESSAGE
            || config.hideScrollbar()
            || config.hideSpecs()
            || config.isFixedWidthTimestampEnabled()
            || config.isColorBarEnabled()
            || config.removeClanInstruction()
            || config.removeClanName()
            || config.removeClanRank()
            || config.removeGuestClanInstruction()
            || config.removeGuestClanReconnecting()
            || config.removeGuestClanName()
            || config.removeGroupIronInstruction()
            || config.removeGroupIronName()
            || config.removeGroupIronFromClan()
            || config.removeFriendsChatStartup()
            || config.removeFriendsChatName()
            || config.removeFriendsAttempting()
            || config.removeFriendsNowTalking()
            || !ImprovedChatConfig.DEFAULT_CUSTOM_CHANNEL_NAME.equals(config.getShortClanName())
            || !ImprovedChatConfig.DEFAULT_CUSTOM_CHANNEL_NAME.equals(config.getShortGuestClanName())
            || !ImprovedChatConfig.DEFAULT_CUSTOM_CHANNEL_NAME.equals(config.getShortGroupIronName())
            || !ImprovedChatConfig.DEFAULT_CUSTOM_CHANNEL_NAME.equals(config.getShortFriendsName());
    }

    private boolean findTimestampPluginEnabled() {
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin instanceof TimestampPlugin) {
                return pluginManager.isPluginEnabled(plugin);
            }
        }
        return false;
    }

    private void handleScrollbarVisibility(boolean hide) {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        Widget scrollBar = client.getWidget(InterfaceID.Chatbox.CHATSCROLLBAR);
        if (scrollArea == null || scrollBar == null) {
            return;
        }

        scrollBar.setHidden(hide);

        scrollArea.setOriginalWidth(hide ? 0 : scrollBar.getWidth());
        scrollArea.revalidate();
    }
}
