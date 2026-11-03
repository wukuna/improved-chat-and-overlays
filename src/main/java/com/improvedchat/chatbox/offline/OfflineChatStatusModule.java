/*
 * Adapted portions from Offline Chat Icon by Bailey Townsend.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2022, Bailey Townsend
 * All rights reserved.
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
package com.improvedchat.chatbox.offline;

import com.improvedchat.ImprovedChatConfig;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Singleton
public final class OfflineChatStatusModule
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ImprovedChatConfig config;
    @Inject private EventBus eventBus;
    @Inject private ClanChatPresentationService clanPresentation;

    private final Map<MessageNode, ClanNameState> nameStates = new IdentityHashMap<>();
    private boolean started;

    public synchronized void startUp()
    {
        if (started || !config.enableOfflineChatStatus())
        {
            return;
        }

        started = true;
        eventBus.register(this);

        clientThread.invokeLater(() -> clientThread.invokeLater(this::formatAll));
    }

    public synchronized void shutDown()
    {
        if (!started)
        {
            return;
        }

        started = false;
        eventBus.unregister(this);
        clientThread.invoke(this::restoreAll);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!ImprovedChatConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if ("enableOfflineIcon".equals(event.getKey())
            || "enableOfflineColor".equals(event.getKey())
            || "offlineColor".equals(event.getKey()))
        {
            clientThread.invokeLater(this::formatAll);
        }
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
        clientThread.invokeLater(this::formatAll);
    }

    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {
        clientThread.invokeLater(this::formatAll);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() -> clientThread.invokeLater(this::formatAll));
        }
    }

    private void formatAll()
    {
        if (!started)
        {
            return;
        }

        IterableHashTable<MessageNode> messages = client.getMessages();
        if (messages == null)
        {
            return;
        }

        boolean changed = false;
        Set<MessageNode> liveNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MessageNode message : messages)
        {
            liveNodes.add(message);
            changed |= format(message);
        }

        nameStates.keySet().removeIf(node -> !liveNodes.contains(node));

        if (changed)
        {
            client.refreshChat();
        }
    }

    private boolean format(MessageNode message)
    {
        ChatMessageType type = message.getType();
        if (type != ChatMessageType.CLAN_CHAT
            && type != ChatMessageType.CLAN_GUEST_CHAT
            && type != ChatMessageType.CLAN_GIM_CHAT)
        {
            return false;
        }

        String currentName = message.getName();
        ClanNameState state = nameStates.get(message);
        if (state == null || !state.matches(message))
        {

            state = ClanNameState.capture(message);
            nameStates.put(message, state);
        }

        String base = state.baseName(currentName);

        ClanChatPresentationService.Presentation presentation =
            clanPresentation.resolve(base, type);

        String result = base;
        if (presentation.isOffline())
        {
            result = offlineName(base, config.enableOfflineIcon(), presentation.getOfflineIconId());
        }

        boolean changed = !result.equals(currentName);
        if (changed)
        {
            message.setName(result);
        }
        state.recordApplied(result);
        return changed;
    }

    static String offlineName(String base, boolean showIcon, int iconId)
    {

        String result = "<col=808080>" + base.replaceAll("(?i)</?col(?:=[^>]*)?>", "") + "</col>";
        return showIcon && iconId >= 0 ? "<img=" + iconId + ">" + result : result;
    }

    private void restoreAll()
    {
        boolean changed = false;
        for (Map.Entry<MessageNode, ClanNameState> entry : nameStates.entrySet())
        {
            try
            {
                MessageNode node = entry.getKey();
                ClanNameState state = entry.getValue();

                if (state.shouldRestore(node))
                {
                    node.setName(state.getOriginalName());
                    changed = true;
                }
            }
            catch (Exception ignored)
            {

            }
        }

        nameStates.clear();
        if (changed)
        {
            client.refreshChat();
        }
    }
}
