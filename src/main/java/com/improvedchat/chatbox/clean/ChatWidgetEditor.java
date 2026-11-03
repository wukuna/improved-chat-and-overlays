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
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.CLAN_INSTRUCTION_MESSAGE;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.SCRIPT_REBUILD_CHATBOX;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.SCRIPT_SCROLLBAR_MAX;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.SCRIPT_SCROLLBAR_MIN;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.sanitizeName;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.wrapWithBrackets;
import com.improvedchat.chatbox.clean.data.ChatChannel;
import com.improvedchat.chatbox.clean.data.ChatTab;
import static java.lang.Math.max;
import static java.lang.Math.min;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import static net.runelite.api.widgets.WidgetPositionMode.ABSOLUTE_CENTER;
import static net.runelite.api.widgets.WidgetPositionMode.ABSOLUTE_TOP;
import static net.runelite.api.widgets.WidgetSizeMode.ABSOLUTE;
import static net.runelite.api.widgets.WidgetSizeMode.MINUS;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
public class ChatWidgetEditor
{
	@Inject
	private ImprovedChatConfig config;

	@Inject
	private ChannelNameManager channelNameManager;

	@Inject
	private Client client;

	@Inject
	private CleanChatModule plugin;

	private boolean anyChatCleanupEnabled()
	{
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

	private int lastScrollDiff = -1;
	private int lastViewportHeight;
	private int lastChatTab = ChatTab.CLOSED.getValue();
	private boolean chatboxScrolled = false;

	@Getter
	public List<ChatWidgetGroup> chatWidgetGroups = List.of();

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{

			lastScrollDiff = -1;
			chatboxScrolled = false;
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (!anyChatCleanupEnabled())
		{
			return;
		}

		if (event.getScriptId() >= SCRIPT_SCROLLBAR_MIN && event.getScriptId() <= SCRIPT_SCROLLBAR_MAX && event.getScriptEvent() != null)
		{
			Object[] args = event.getScriptEvent().getArguments();
			chatboxScrolled = args.length >= 2 && (int) args[1] == InterfaceID.Chatbox.CHATSCROLLBAR;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (!anyChatCleanupEnabled())
		{
			chatWidgetGroups = List.of();
			return;
		}

		if (event.getScriptId() == SCRIPT_REBUILD_CHATBOX)
		{
			checkReplacements();
		}
		else if (event.getScriptId() >= SCRIPT_SCROLLBAR_MIN && event.getScriptId() <= SCRIPT_SCROLLBAR_MAX && chatboxScrolled)
		{
			chatboxScrolled = false;

			Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
			if (chatbox != null)
			{
				lastScrollDiff = chatbox.getScrollHeight() - chatbox.getScrollY();
				lastViewportHeight = Math.max(0, chatbox.getHeight());
				client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, chatbox.getScrollHeight() - lastScrollDiff);
			}
		}
	}

	public void checkReplacements()
	{
		if (!anyChatCleanupEnabled())
		{
			chatWidgetGroups = List.of();
			return;
		}

		channelNameManager.updateFriendsChatName();

		Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		ChatTab selectedChatTab = ChatTab.of(client.getVarcIntValue(VarClientID.CHAT_VIEW));

		if (chatbox != null && selectedChatTab != ChatTab.CLOSED)
		{
			Widget[] chatWidgets = chatbox.getDynamicChildren().clone();
			Widget[] clickboxWidgets = chatbox.getStaticChildren().clone();

			chatWidgetGroups = Stream.iterate(
					0,
					i -> i < chatWidgets.length,
					i -> i + 4
				)
				.map(i -> {
					int rankWidgetIndex = i + 3;
					int messageWidgetIndex = i + 1;
					int nameWidgetIndex = i;

					Widget channelWidget = chatWidgets[i + 2];
					if (channelWidget.getText().isEmpty())
					{

						if (!chatWidgets[i].getText().isEmpty())
						{

							Widget messageWidget = chatWidgets[i + 1];

							if (messageWidget.getText().isEmpty() || Text.removeTags(messageWidget.getText()).equals(CLAN_INSTRUCTION_MESSAGE))
							{

								messageWidgetIndex = i;

								nameWidgetIndex = i + 1;
							}
							else
							{

								channelWidget = chatWidgets[i];

								nameWidgetIndex = i + 2;
							}
						}
						else if (chatWidgets[messageWidgetIndex].getText().isEmpty())
						{
							return null;
						}
						else
						{

						}
					}

					return new ChatWidgetGroup(channelWidget, chatWidgets[rankWidgetIndex], chatWidgets[nameWidgetIndex], chatWidgets[messageWidgetIndex], clickboxWidgets[i / 4]);
				})
				.filter(Objects::nonNull)
				.peek(group -> {
					if (!group.getChannelText().isEmpty())
					{
						Pair<ChatChannel, String> match = ChatChannel.findChannelMatch(group.getChannelText(), channelNameManager);
						if (match != null)
						{
							ChatChannel channel = match.getLeft();
							String matchedChannelName = match.getRight();
							String widgetChannelText = sanitizeName(group.getChannelText());
							String shortName = channel.getShortName(channelNameManager, matchedChannelName);

							group.setChannelType(channel);

							if (channel.isChannelNameRemovalEnabled(config))
							{
								group.removeFromChannel(matchedChannelName, client);

								matchedChannelName = wrapWithBrackets(matchedChannelName);
							}
							else if (!shortName.isBlank() && !channel.isShortNameDefault(channelNameManager))
							{
								String updatedChannelText = group.replaceChannelName(matchedChannelName, shortName, client);

								matchedChannelName = sanitizeName(shortName);
								widgetChannelText = sanitizeName(updatedChannelText);
							}
							else
							{
								matchedChannelName = wrapWithBrackets(matchedChannelName);
							}

							if (channel.isRemoveRankEnabled(config))
							{
								group.removeRank();
							}

							if (channel != ChatChannel.FRIENDS_CHAT)
							{
								group.calculateChannelIndent(config, matchedChannelName, widgetChannelText,
									plugin.getTimestampTemplateWidth(), plugin.isFixedWidthTimestampEnabled(), client);
							}
						}
					}

					if (plugin.isFixedWidthTimestampEnabled())
					{
						group.extractTimestamp(plugin.getTimestampTemplate(), plugin.getTimestampTemplateWidth(), client);
					}

					group.applyIndent();

					group.calculateHeight(client);
				})
				.collect(Collectors.toList());

			Collections.reverse(chatWidgetGroups);

			int totalHeight = chatWidgetGroups.stream()
				.map(ChatWidgetGroup::getHeight)
				.reduce(0, Integer::sum);

			int y = totalHeight >= chatbox.getHeight() ? 0 : chatbox.getHeight() - totalHeight - 2;

			for (ChatWidgetGroup group : chatWidgetGroups)
			{
				group.place(y);

				y += group.getHeight();
			}

			if (totalHeight >= chatbox.getHeight())
			{
				y += 2;
			}

			y = max(y, chatbox.getHeight());

			chatbox.setScrollHeight(y);
			chatbox.revalidateScroll();

			scrollbar_resize(chatbox);

			lastScrollDiff = chatbox.getScrollHeight() - chatbox.getScrollY();
			lastViewportHeight = Math.max(0, chatbox.getHeight());
		}
		else
		{

			lastScrollDiff = -1;
			chatboxScrolled = false;
		}
		lastChatTab = selectedChatTab.getValue();
	}

	private void scrollbar_resize(Widget scrollArea)
	{
		Widget scrollBarContainer = client.getWidget(InterfaceID.Chatbox.CHATSCROLLBAR);

		int scrollAreaHeight = scrollArea.getScrollHeight();
		if (scrollAreaHeight <= 0)
		{
			scrollAreaHeight = scrollArea.getHeight();
		}

		int scrollBarHeight;
		if (scrollAreaHeight > 0)
		{
			scrollBarHeight = (scrollBarContainer.getHeight() - 32) * scrollArea.getHeight() / scrollAreaHeight;
		}
		else
		{
			scrollBarHeight = scrollBarContainer.getHeight() - 32;
		}
		if (scrollBarHeight < 10)
		{
			scrollBarHeight = 10;
		}

		Widget scrollBar = scrollBarContainer.getChild(1);
		if (scrollBar != null)
		{
			scrollBar.setSize(0, scrollBarHeight, MINUS, ABSOLUTE);
			scrollBar.revalidate();

			scrollbar_vertical_doscroll(scrollBarContainer, scrollArea, scrollBar);

			scrollBarContainer.revalidateScroll();
			scrollArea.revalidateScroll();
		}
	}

	private void scrollbar_vertical_doscroll(Widget scrollBarContainer, Widget scrollArea, Widget scrollBar)
	{
		int int2;
		if (lastChatTab != ChatTab.CLOSED.getValue() && lastScrollDiff != -1)
		{

			int viewport = max(0, scrollArea.getHeight());
			int distanceFromBottom = lastViewportHeight > 0 ? max(0, lastScrollDiff - lastViewportHeight) : 0;
			int2 = max(scrollArea.getScrollHeight() - viewport - distanceFromBottom, 0);
		}
		else if (scrollArea.getHeight() < 0)
		{

			int2 = 0;

			scrollArea.setScrollHeight(114);
		}
		else
		{

			int2 = scrollArea.getScrollY();
			int int3 = max(scrollArea.getScrollHeight() - scrollArea.getHeight(), 1);
			int2 = max(min(int2, int3), 0);
		}
		scrollArea.setScrollY(int2);
		scrollArea.revalidateScroll();
		client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, scrollArea.getScrollY());

		scrollbar_vertical_setdragger(scrollBarContainer, scrollArea, scrollBar);
	}

	private void scrollbar_vertical_setdragger(Widget scrollBarContainer, Widget scrollArea, Widget scrollBar)
	{
		int int2 = max(scrollArea.getScrollHeight() - scrollArea.getHeight(), 1);
		int int3 = scrollBarContainer.getHeight() - 32 - scrollBar.getHeight();

		int scrollBarPos = max(16 + int3 * scrollArea.getScrollY() / int2, 16);
		scrollBar.setPos(0, scrollBarPos, ABSOLUTE_CENTER, ABSOLUTE_TOP);
		scrollBar.revalidate();

		Widget scrollbarElementTop = scrollBarContainer.getChild(2);
		if (scrollbarElementTop != null)
		{
			scrollbarElementTop.setPos(0, scrollBar.getOriginalY(), ABSOLUTE_CENTER, ABSOLUTE_TOP);
			scrollbarElementTop.revalidate();
		}

		Widget scrollbarElementBottom = scrollBarContainer.getChild(3);
		if (scrollbarElementBottom != null)
		{
			scrollbarElementBottom.setPos(0, scrollBar.getOriginalY() + scrollBar.getHeight() - 5, ABSOLUTE_CENTER, ABSOLUTE_TOP);
			scrollbarElementBottom.revalidate();
		}
	}

}
