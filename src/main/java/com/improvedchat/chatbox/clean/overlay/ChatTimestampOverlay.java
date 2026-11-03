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

import com.improvedchat.chatbox.clean.ChatWidgetGroup;
import com.improvedchat.chatbox.clean.CleanChatModule;
import static com.improvedchat.chatbox.clean.util.CleanChatUtil.getTextLength;
import com.improvedchat.chatbox.clean.util.FormatterExtractor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.timestamp.TimestampConfig;
import net.runelite.client.ui.FontManager;

@Slf4j
@Singleton
public class ChatTimestampOverlay extends BaseCleanChatOverlay
{
	private static final int NUMERIC_CELL_WIDTH = 8;
	private static final int TEXT_CELL_WIDTH = 9;

	@Inject
	private ConfigManager configManager;

	@Inject
	private CleanChatModule plugin;

	@Inject
	private Client client;

	@Override
	boolean isEnabled()
	{
		return plugin.isFixedWidthTimestampEnabled();
	}

	@Override
	void render(Graphics2D graphics, int x, int y, ChatWidgetGroup group)
	{
		FormatterExtractor.ExtractionResult timestamp = group.getTimestamp();

		if (timestamp == null)
		{
			return;
		}

		int timestampY = y + 14;

		Color timestampColor = getTimestampColour();
		graphics.setColor(timestampColor);

		graphics.setFont(FontManager.getRunescapeFont());

		AtomicInteger timestampX = new AtomicInteger(group.getTimestampX());

		boolean isChatboxTransparent = client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;

		FormatterExtractor.iterateOutputParts(timestamp, new FormatterExtractor.OutputPartConsumer()
		{
			@Override
			public void consumeSegment(FormatterExtractor.FormatSegment segment)
			{
				String value = segment.value == null ? "" : segment.value;
				int cellWidth = cellWidth(segment);
				for (int i = 0; i < value.length(); i++)
				{
					String str = String.valueOf(value.charAt(i));

					if (isChatboxTransparent)
					{

						graphics.setColor(Color.BLACK);
						graphics.drawString(str, timestampX.get() + 1, timestampY + 1);
						graphics.setColor(timestampColor);
					}
					graphics.drawString(str, timestampX.get(), timestampY);
					timestampX.addAndGet(cellWidth);
				}

				int reservedChars = segment.endIndex - segment.startIndex;
				int remainingCells = Math.max(0, reservedChars - value.length());
				timestampX.addAndGet(remainingCells * cellWidth);
			}

			@Override
			public void consumeText(String text, int startIndex, int endIndex)
			{
				if (isChatboxTransparent)
				{

					graphics.setColor(Color.BLACK);
					graphics.drawString(text, timestampX.get() + 1, timestampY + 1);
					graphics.setColor(timestampColor);
				}
				graphics.drawString(text, timestampX.get(), timestampY);
				timestampX.addAndGet(graphics.getFontMetrics().stringWidth(text));
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(TimestampConfig.GROUP) && event.getKey().equals("format"))
		{
			updateTemplate();
		}
	}

	public void startUp()
	{
		updateTemplate();
	}

	private void updateTemplate()
	{
		TimestampConfig timestampConfig = timestampConfig();
		FormatterExtractor.ExtractionResult newTemplate = FormatterExtractor.createFromFormatString(timestampConfig.timestampFormat());

		plugin.setTimestampTemplateWidth(0);
		plugin.setTimestampTemplate(newTemplate);

		if (newTemplate != null) {
			FormatterExtractor.iterateOutputParts(newTemplate, new FormatterExtractor.OutputPartConsumer()
			{
				@Override
				public void consumeSegment(FormatterExtractor.FormatSegment segment)
				{
					int reservedChars = segment.endIndex - segment.startIndex;
					plugin.setTimestampTemplateWidth(plugin.getTimestampTemplateWidth()
						+ (cellWidth(segment) * reservedChars));
				}

				@Override
				public void consumeText(String text, int startIndex, int endIndex)
				{
					plugin.setTimestampTemplateWidth(plugin.getTimestampTemplateWidth() + getTextLength(text, client));
				}
			});

			int spaceWidth = Math.max(1, getTextLength(" ", client));
			int rawWidth = plugin.getTimestampTemplateWidth();
			plugin.setTimestampTemplateWidth(((rawWidth + spaceWidth - 1) / spaceWidth) * spaceWidth);
		}

		client.refreshChat();
	}

	private static int cellWidth(FormatterExtractor.FormatSegment segment)
	{
		switch (segment.tokenChar)
		{
			case 'M':
				return segment.tokenCount >= 3 ? TEXT_CELL_WIDTH : NUMERIC_CELL_WIDTH;
			case 'E':
			case 'a':
			case 'z':
				return TEXT_CELL_WIDTH;
			default:
				return NUMERIC_CELL_WIDTH;
		}
	}

	private TimestampConfig timestampConfig()
	{
		return configManager.getConfig(TimestampConfig.class);
	}

	private Color getTimestampColour()
	{
		boolean isChatboxTransparent = client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;

		TimestampConfig timestampConfig = timestampConfig();
		Color color = isChatboxTransparent ? timestampConfig.transparentTimestamp() : timestampConfig.opaqueTimestamp();

		if (color == null)
		{
			color = isChatboxTransparent ? Color.WHITE : Color.BLACK;
		}

		return color;
	}

}
