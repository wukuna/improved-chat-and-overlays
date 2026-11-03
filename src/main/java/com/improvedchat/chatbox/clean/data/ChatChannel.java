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
package com.improvedchat.chatbox.clean.data;

import com.improvedchat.chatbox.clean.ChannelNameManager;
import com.improvedchat.ImprovedChatConfig;
import static com.improvedchat.ImprovedChatConfig.DEFAULT_CUSTOM_CHANNEL_NAME;
import com.improvedchat.chatbox.clean.util.CleanChatUtil;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.CURRENT_CLAN_REPLACER;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.sanitizeName;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@AllArgsConstructor
public enum ChatChannel
{

	CLAN(
		ImprovedChatConfig::removeClanName,
		ChannelNameManager::getClanNames,
		(c, t) -> false,
		ChannelNameManager::getShortClanName,
		ImprovedChatConfig::removeClanRank,
		ImprovedChatConfig::clanChannelColor
	),
	GUEST_CLAN(
		ImprovedChatConfig::removeGuestClanName,
		ChannelNameManager::getGuestClanNames,
		(c, t) -> false,
		ChannelNameManager::getShortGuestClanName,
		c -> false,
		ImprovedChatConfig::guestClanChannelColor
	),
	FRIENDS_CHAT(
		ImprovedChatConfig::removeFriendsChatName,
		ChannelNameManager::getFriendsChatNames,
		(c, t) -> false,
		ChannelNameManager::getShortFriendsChatName,
		c -> false,
		ImprovedChatConfig::friendsChannelColor
	),
	GROUP_IRON(
		ImprovedChatConfig::removeGroupIronName,
		ChannelNameManager::getGroupIronNames,
		(config, tab) -> config.removeGroupIronFromClan() && tab == ChatTab.CLAN,
		ChannelNameManager::getShortGroupIronName,
		c -> false,
		ImprovedChatConfig::groupIronChannelColor
	);

	public List<String> getNames(ChannelNameManager channelNameManager)
	{
		return getNames.apply(channelNameManager);
	}

	public boolean isChannelNameRemovalEnabled(ImprovedChatConfig config)
	{
		return isEnabled.apply(config);
	}

	public boolean isTabBlocked(ImprovedChatConfig config, ChatTab tab)
	{
		return isTabBlocked.apply(config, tab);
	}

	public boolean isShortNameDefault(ChannelNameManager channelNameManager)
	{

		return Objects.equals(getShortName.apply(channelNameManager), DEFAULT_CUSTOM_CHANNEL_NAME);
	}

	public String getShortName(ChannelNameManager channelNameManager, String matchedName)
	{
		final String shortName = getShortName.apply(channelNameManager);

		if (shortName.contains(CURRENT_CLAN_REPLACER))
		{
			return shortName.replace(CURRENT_CLAN_REPLACER, matchedName);
		}
		else
		{
			return shortName;
		}
	}

	public boolean isRemoveRankEnabled(ImprovedChatConfig config)
	{
		return isRemoveRank.apply(config);
	}

	public Color getColor(ImprovedChatConfig config)
	{
		return getColor.apply(config);
	}

	private final Function<ImprovedChatConfig, Boolean> isEnabled;
	private final Function<ChannelNameManager, List<String>> getNames;
	private final BiFunction<ImprovedChatConfig, ChatTab, Boolean> isTabBlocked;
	private final Function<ChannelNameManager, String> getShortName;
	private final Function<ImprovedChatConfig, Boolean> isRemoveRank;
	private final Function<ImprovedChatConfig, Color> getColor;

	public static Pair<ChatChannel, String> findChannelMatch(String channel, ChannelNameManager channelNameManager)
	{
		for (ChatChannel channelRemoval : ChatChannel.values())
		{
			if (channel == null)
			{
				break;
			}
			String widgetChannelName = sanitizeName(channel);
			String matchedChannelName = channelRemoval.getNames(channelNameManager).stream()
				.map(CleanChatUtil::sanitizeName)
				.filter(widgetChannelName::contains)
				.findFirst()
				.orElse(null);

			if (matchedChannelName != null)
			{
				return Pair.of(channelRemoval, matchedChannelName);
			}
		}

		return null;
	}
}
