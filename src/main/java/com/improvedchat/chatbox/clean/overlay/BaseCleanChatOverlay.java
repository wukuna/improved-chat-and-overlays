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
package com.improvedchat.chatbox.clean.overlay;

import com.improvedchat.chatbox.clean.ChatWidgetEditor;
import com.improvedchat.chatbox.clean.ChatWidgetGroup;
import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.clean.data.ChatTab;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
public abstract class BaseCleanChatOverlay extends Overlay
{
	@Inject
	protected Client client;

	@Inject
	protected ChatWidgetEditor chatWidgetEditor;

	@Inject
	protected ImprovedChatConfig config;

	abstract boolean isEnabled();

	BaseCleanChatOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);

		drawAfterInterface(InterfaceID.CHATBOX);
	}

	@Override
	final public Dimension render(Graphics2D graphics)
	{
		if (!isEnabled())
		{
			return null;
		}

		Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		ChatTab selectedChatTab = ChatTab.of(client.getVarcIntValue(VarClientID.CHAT_VIEW));

		if (chatbox == null || chatbox.isHidden() || selectedChatTab == ChatTab.CLOSED)
		{
			return null;
		}

		graphics.setClip(chatbox.getCanvasLocation().getX(), chatbox.getCanvasLocation().getY(), chatbox.getWidth(), chatbox.getHeight());

		List<ChatWidgetGroup> allChats = List.copyOf(chatWidgetEditor.getChatWidgetGroups());
		if (allChats.isEmpty())
		{
			return null;
		}

		List<ChatWidgetGroup> visibleChats = new ArrayList<>();
		ChatWidgetGroup previousGroup = null;
		boolean foundStart = false;

		for (ChatWidgetGroup group : allChats)
		{
			boolean isVisibleInChat = group.getY() >= chatbox.getCanvasLocation().getY() &&
				group.getY() + group.getHeight() <= chatbox.getCanvasLocation().getY() + chatbox.getHeight();

			if (isVisibleInChat)
			{
				visibleChats.add(group);

				if (!foundStart)
				{
					foundStart = true;
					if (previousGroup != null)
					{

						visibleChats.add(0, previousGroup);
					}
				}
			}
			else if (foundStart)
			{

				visibleChats.add(group);
				break;
			}

			previousGroup = group;
		}

		visibleChats.forEach(group -> {
			int x = group.getX();
			int y = group.getY();

			render(graphics, x, y, group);
		});

		return null;
	}

	abstract void render(Graphics2D graphics, int x, int y, ChatWidgetGroup group);
}
