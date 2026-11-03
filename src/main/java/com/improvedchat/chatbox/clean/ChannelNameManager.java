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
import static com.improvedchat.ImprovedChatConfig.DEFAULT_CUSTOM_CHANNEL_NAME;
import com.improvedchat.chatbox.clean.util.CleanChatUtil;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.MAX_CHANNEL_LIST_SIZE;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
@Singleton
public class ChannelNameManager
{

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ImprovedChatConfig config;

	@Getter
	private final List<String> clanNames = new ArrayList<>();
	@Getter
	private final List<String> guestClanNames = new ArrayList<>();
	@Getter
	private final List<String> friendsChatNames = new ArrayList<>();
	@Getter
	private final List<String> groupIronNames = new ArrayList<>();

	@Getter
	private String shortClanName = DEFAULT_CUSTOM_CHANNEL_NAME;
	@Getter
	private String shortGuestClanName = DEFAULT_CUSTOM_CHANNEL_NAME;
	@Getter
	private String shortFriendsChatName = DEFAULT_CUSTOM_CHANNEL_NAME;
	@Getter
	private String shortGroupIronName = DEFAULT_CUSTOM_CHANNEL_NAME;

	public void startup()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> {
				updateFriendsChatName();

				ClanChannel clanChannel = client.getClanChannel(ClanID.CLAN);
				if (clanChannel != null)
				{
					addName(clanNames, clanChannel.getName());
				}

				ClanChannel groupIronChannel = client.getClanChannel(ClanID.GROUP_IRONMAN);
				if (groupIronChannel != null)
				{
					addName(groupIronNames, groupIronChannel.getName());
				}

				ClanChannel guestClanChannel = client.getGuestClanChannel();
				if (guestClanChannel != null)
				{
					addName(guestClanNames, guestClanChannel.getName());
				}
			});
		}

		updateShortNames();
	}

	public void shutdown()
	{
		clanNames.clear();
		groupIronNames.clear();
		guestClanNames.clear();
		friendsChatNames.clear();
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		String channelName = event.getClanChannel() != null ? event.getClanChannel().getName() : null;

		if (channelName == null)
		{
			return;
		}

		switch (event.getClanId())
		{
			case CleanChatUtil.GUEST_CLAN:
				addName(guestClanNames, channelName);
				break;
			case ClanID.CLAN:
				addName(clanNames, channelName);
				break;
			case ClanID.GROUP_IRONMAN:
				addName(groupIronNames, channelName);
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{

		updateFriendsChatName();
	}

	public void updateFriendsChatName()
	{
		FriendsChatManager friendsChatManager = client.getFriendsChatManager();

		if (friendsChatManager != null)
		{
			addName(friendsChatNames, friendsChatManager.getName());
		}
	}

	private void addName(List<String> nameList, String name)
	{
		if (nameList.contains(name) || name == null)
		{
			return;
		}

		if (nameList.size() == MAX_CHANNEL_LIST_SIZE)
		{
			nameList.remove(0);
		}

		nameList.add(name);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (Objects.equals(event.getGroup(), ImprovedChatConfig.GROUP) && event.getKey().startsWith("short"))
		{
			updateShortNames();
		}
	}

	private void updateShortNames()
	{
		shortClanName = config.getShortClanName();
		shortGuestClanName = config.getShortGuestClanName();
		shortFriendsChatName = config.getShortFriendsName();
		shortGroupIronName = config.getShortGroupIronName();
	}
}
