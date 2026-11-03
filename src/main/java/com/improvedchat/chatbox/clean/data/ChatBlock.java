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

import com.improvedchat.ImprovedChatConfig;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.CLAN_INSTRUCTION_MESSAGE;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import net.runelite.client.util.Text;

@AllArgsConstructor
public enum ChatBlock
{
	CLAN_INSTRUCTION(
		ImprovedChatConfig::removeClanInstruction,
		CLAN_INSTRUCTION_MESSAGE
	),
	GUEST_CLAN_INSTRUCTION(
		ImprovedChatConfig::removeGuestClanInstruction,

		"To talk, start each line of chat with /// or /gc"
	),
	GUEST_CLAN_RECONNECTING(
		ImprovedChatConfig::removeGuestClanReconnecting,
		"Attempting to reconnect to guest channel automatically..."
	),
	GROUP_IRON_INSTRUCTION(
		ImprovedChatConfig::removeGroupIronInstruction,
		"To talk in your Ironman Group's channel"
	),
	FRIENDS_CHAT_INSTRUCTION(
		ImprovedChatConfig::removeFriendsChatStartup,
		"To talk, start each line of chat with the / symbol."
	),
	FRIENDS_CHAT_ATTEMPTING(
		ImprovedChatConfig::removeFriendsAttempting,
		"Attempting to join chat-channel..."
	),
	FRIENDS_CHAT_NOW_TALKING(
		ImprovedChatConfig::removeFriendsNowTalking,
		"Now talking in chat-channel"
	),
	WELCOME(
		ImprovedChatConfig::removeWelcome,
		"Welcome to Old School RuneScape."
	),
	DRAGON_HARPOON(
		ImprovedChatConfig::hideSpecs,
		"Here fishy fishies!"
	),
	DRAGON_PICK(
		ImprovedChatConfig::hideSpecs,
		"Smashing!"
	),
	DRAGON_AXE(
		ImprovedChatConfig::hideSpecs,
		"Chop chop!"
	),
	DRAGON_BATTLEAXE(
		ImprovedChatConfig::hideSpecs,
		"Raarrrrrgggggghhhhhhh!"
	),
	;

	public boolean isEnabled(ImprovedChatConfig config)
	{
		return isEnabled.apply(config);
	}

	public boolean appliesTo(ImprovedChatConfig config, String message)
	{
		return isEnabled(config) && Text.removeTags(message).contains(this.message);
	}

	private final Function<ImprovedChatConfig, Boolean> isEnabled;
	private final String message;
}
