/*
 * Copyright (c) 2026, theOranguzang
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.improvedchat.dialogue;

import com.improvedchat.ImprovedChatConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class DialogueWidgetManager
{

	static final int OPTION_CAMOUFLAGE_COLOR = 0xD6CCAF;

	private static final int NPC_CHILD_NAME     = 4;
	private static final int NPC_CHILD_TEXT     = 6;
	private static final int NPC_CHILD_CONTINUE = 5;

	private static final int PLAYER_CHILD_NAME     = 4;
	private static final int PLAYER_CHILD_TEXT     = 6;
	private static final int PLAYER_CHILD_CONTINUE = 5;

	private static final int SPRITE_CHILD_TEXT         = 2;
	static final int          SPRITE_CONTINUE_DYN_INDEX = 2;

	private static final Pattern TAG_STRIP = Pattern.compile("<[^>]*>");

	@Inject
	private Client client;

	@Inject
	private ImprovedChatConfig config;

	private final Map<Widget, String> savedTexts = new HashMap<>();

	private final Map<Widget, Integer> savedColors = new HashMap<>();

	private List<TextSegment> cachedNpcBody     = Collections.emptyList();
	private String            cachedNpcName     = "";
	private String            cachedNpcContinue = "";

	private List<TextSegment> cachedPlayerBody     = Collections.emptyList();
	private String            cachedPlayerName     = "";
	private String            cachedPlayerContinue = "";

	private List<TextSegment> cachedSpriteBody     = Collections.emptyList();
	private String            cachedSpriteContinue = "";

	private List<String> cachedOptionTexts   = Collections.emptyList();
	private Widget[]     cachedOptionWidgets = new Widget[0];
	private String       cachedOptionTitle   = "";

	private Widget       cachedOptionTitleWidget = null;

	private DialogueType lastSeenType = null;

	public DialogueState getCurrentDialogue()
	{
		DialogueState state = detectAndBuild();

		DialogueType currentType = state != null ? state.getType() : null;

		if (currentType != lastSeenType)
		{
			clearCacheFor(lastSeenType);
			lastSeenType = currentType;
		}

		return state;
	}

	public void restoreAll()
	{
		for (Map.Entry<Widget, String> entry : savedTexts.entrySet())
		{
			try
			{
				entry.getKey().setText(entry.getValue());
			}
			catch (Exception e)
			{
				log.warn("Failed to restore widget text", e);
			}
		}
		savedTexts.clear();

		for (Map.Entry<Widget, Integer> entry : savedColors.entrySet())
		{
			try
			{
				entry.getKey().setTextColor(entry.getValue());
			}
			catch (Exception e)
			{
				log.warn("Failed to restore widget text color", e);
			}
		}
		savedColors.clear();

		clearCacheFor(DialogueType.NPC_DIALOGUE);
		clearCacheFor(DialogueType.PLAYER_DIALOGUE);
		clearCacheFor(DialogueType.OPTION_DIALOGUE);
		clearCacheFor(DialogueType.SPRITE_DIALOGUE);
		lastSeenType = null;
	}

	private DialogueState detectAndBuild()
	{
		Widget npcRoot = client.getWidget(InterfaceID.DIALOG_NPC, 0);
		if (isVisible(npcRoot) && config.replaceNpc())
		{
			return buildNpcState();
		}

		Widget playerRoot = client.getWidget(InterfaceID.DIALOG_PLAYER, 0);
		if (isVisible(playerRoot) && config.replacePlayer())
		{
			return buildPlayerState();
		}

		Widget optionContainer = client.getWidget(InterfaceID.DIALOG_OPTION, 1);
		if (isVisible(optionContainer) && config.replaceOptions())
		{
			return buildOptionState();
		}

		Widget spriteRoot = client.getWidget(InterfaceID.DIALOG_SPRITE, 0);
		if (isVisible(spriteRoot) && config.replaceSprite())
		{
			return buildSpriteState();
		}

		return null;
	}

	private DialogueState buildNpcState()
	{
		Widget nameWidget     = client.getWidget(InterfaceID.DIALOG_NPC, NPC_CHILD_NAME);
		Widget textWidget     = client.getWidget(InterfaceID.DIALOG_NPC, NPC_CHILD_TEXT);
		Widget continueWidget = client.getWidget(InterfaceID.DIALOG_NPC, NPC_CHILD_CONTINUE);

		if (textWidget == null)
		{
			return null;
		}

		String raw = textWidget.getText();
		if (raw != null && !raw.isEmpty())
		{
			cachedNpcBody = parseSegments(raw, Color.BLACK);
			cachedNpcName = nameWidget != null ? stripTags(nameWidget.getText()) : "";
		}

		if (continueWidget != null)
		{
			String rawContinue = continueWidget.getText();
			if (rawContinue != null && !rawContinue.isEmpty())
			{
				cachedNpcContinue = stripTags(rawContinue);
			}
		}

		blankWidget(textWidget);
		blankWidget(nameWidget);
		camouflageWidget(continueWidget);

		if (cachedNpcBody.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.NPC_DIALOGUE,
			cachedNpcName,
			cachedNpcBody,
			null,
			textWidget,
			nameWidget,
			continueWidget,
			null,
			cachedNpcContinue
		);
	}

	private DialogueState buildPlayerState()
	{
		Widget nameWidget     = client.getWidget(InterfaceID.DIALOG_PLAYER, PLAYER_CHILD_NAME);
		Widget textWidget     = client.getWidget(InterfaceID.DIALOG_PLAYER, PLAYER_CHILD_TEXT);
		Widget continueWidget = client.getWidget(InterfaceID.DIALOG_PLAYER, PLAYER_CHILD_CONTINUE);

		if (textWidget == null)
		{
			return null;
		}

		String raw = textWidget.getText();
		if (raw != null && !raw.isEmpty())
		{
			cachedPlayerBody = parseSegments(raw, Color.BLACK);
			cachedPlayerName = nameWidget != null ? stripTags(nameWidget.getText()) : "";
		}

		if (continueWidget != null)
		{
			String rawContinue = continueWidget.getText();
			if (rawContinue != null && !rawContinue.isEmpty())
			{
				cachedPlayerContinue = stripTags(rawContinue);
			}
		}

		blankWidget(textWidget);
		blankWidget(nameWidget);
		camouflageWidget(continueWidget);

		if (cachedPlayerBody.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.PLAYER_DIALOGUE,
			cachedPlayerName,
			cachedPlayerBody,
			null,
			textWidget,
			nameWidget,
			continueWidget,
			null,
			cachedPlayerContinue
		);
	}

	private DialogueState buildOptionState()
	{

		Widget container = client.getWidget(InterfaceID.DIALOG_OPTION, 1);
		if (container == null)
		{
			return null;
		}

		Widget[] dynChildren = container.getDynamicChildren();
		if (dynChildren == null || dynChildren.length == 0)
		{
			return null;
		}

		Widget titleWidget = dynChildren[0];
		cachedOptionTitleWidget = titleWidget;
		String titleRaw = titleWidget.getText();
		if (titleRaw != null && !titleRaw.isEmpty())
		{
			cachedOptionTitle = stripTags(titleRaw);
		}
		camouflageWidget(titleWidget);

		List<String> freshTexts  = new ArrayList<>();
		List<Widget> liveWidgets = new ArrayList<>();

		for (int i = 1; i < dynChildren.length; i++)
		{
			Widget opt = dynChildren[i];
			if (opt == null || opt.isHidden())
			{
				continue;
			}

			liveWidgets.add(opt);

			String optText = opt.getText();
			if (optText != null && !optText.isEmpty())
			{
				freshTexts.add(stripTags(optText));
			}

			camouflageWidget(opt);
		}

		if (!freshTexts.isEmpty())
		{

			cachedOptionTexts   = freshTexts;
			cachedOptionWidgets = liveWidgets.toArray(new Widget[0]);
		}
		else if (!liveWidgets.isEmpty())
		{

			cachedOptionWidgets = liveWidgets.toArray(new Widget[0]);
		}

		if (cachedOptionTexts.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.OPTION_DIALOGUE,
			cachedOptionTitle,
			null,
			cachedOptionTexts,
			container,
			cachedOptionTitleWidget,
			null,
			cachedOptionWidgets,
			""
		);
	}

	private DialogueState buildSpriteState()
	{
		Widget spriteRoot = client.getWidget(InterfaceID.DIALOG_SPRITE, 0);
		Widget textWidget  = client.getWidget(InterfaceID.DIALOG_SPRITE, SPRITE_CHILD_TEXT);

		Widget continueWidget = null;
		if (spriteRoot != null)
		{
			Widget[] dynChildren = spriteRoot.getDynamicChildren();
			if (dynChildren != null && dynChildren.length > SPRITE_CONTINUE_DYN_INDEX)
			{
				continueWidget = dynChildren[SPRITE_CONTINUE_DYN_INDEX];
			}
		}

		if (textWidget == null)
		{
			return null;
		}

		String raw = textWidget.getText();
		if (raw != null && !raw.isEmpty())
		{
			cachedSpriteBody = parseSegments(raw, Color.BLACK);
		}

		if (continueWidget != null)
		{
			String rawContinue = continueWidget.getText();
			if (rawContinue != null && !rawContinue.isEmpty())
			{
				cachedSpriteContinue = stripTags(rawContinue);
			}
		}

		blankWidget(textWidget);
		blankWidget(continueWidget);

		if (cachedSpriteBody.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.SPRITE_DIALOGUE,
			null,
			cachedSpriteBody,
			null,
			textWidget,
			null,
			continueWidget,
			null,
			cachedSpriteContinue
		);
	}

	private void clearCacheFor(DialogueType type)
	{
		if (type == null)
		{
			return;
		}
		switch (type)
		{
			case NPC_DIALOGUE:
				cachedNpcBody     = Collections.emptyList();
				cachedNpcName     = "";
				cachedNpcContinue = "";
				break;
			case PLAYER_DIALOGUE:
				cachedPlayerBody     = Collections.emptyList();
				cachedPlayerName     = "";
				cachedPlayerContinue = "";
				break;
			case OPTION_DIALOGUE:
				cachedOptionTexts       = Collections.emptyList();
				cachedOptionWidgets     = new Widget[0];
				cachedOptionTitle       = "";
				cachedOptionTitleWidget = null;
				break;
			case SPRITE_DIALOGUE:
				cachedSpriteBody     = Collections.emptyList();
				cachedSpriteContinue = "";
				break;
			default:
				break;
		}
	}

	private void blankWidget(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		String current = widget.getText();
		if (current == null)
		{
			return;
		}
		if (!current.isEmpty() && !savedTexts.containsKey(widget))
		{
			savedTexts.put(widget, current);
		}
		widget.setText("");
	}

	private void camouflageWidget(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		if (!savedColors.containsKey(widget))
		{
			savedColors.put(widget, widget.getTextColor());
		}
		widget.setTextColor(OPTION_CAMOUFLAGE_COLOR);
	}

	private static boolean isVisible(Widget w)
	{
		return w != null && !w.isHidden();
	}

	static String stripTags(String text)
	{
		if (text == null)
		{
			return "";
		}
		return TAG_STRIP.matcher(text)
			.replaceAll("")
			.replace("<br>", " ")
			.trim();
	}

	public List<TextSegment> parseSegments(String raw, Color defaultColor)
	{
		List<TextSegment> segments = new ArrayList<>();
		if (raw == null || raw.isEmpty())
		{
			return segments;
		}

		String text = raw
			.replace("<br>", " ")
			.replace("<lt>", "<")
			.replace("<gt>", ">")
			.replaceAll("\\s{2,}", " ")
			.trim();

		Color currentColor = defaultColor;
		int pos = 0;
		int len = text.length();

		while (pos < len)
		{
			int tagStart = text.indexOf('<', pos);
			if (tagStart == -1)
			{
				appendSegment(segments, text.substring(pos), currentColor);
				break;
			}

			if (tagStart > pos)
			{
				appendSegment(segments, text.substring(pos, tagStart), currentColor);
			}

			int tagEnd = text.indexOf('>', tagStart);
			if (tagEnd == -1)
			{
				appendSegment(segments, text.substring(tagStart), currentColor);
				break;
			}

			String tagContent = text.substring(tagStart + 1, tagEnd);
			if (tagContent.startsWith("col="))
			{
				try
				{
					currentColor = new Color(Integer.parseInt(tagContent.substring(4), 16));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
			else if (tagContent.equals("/col"))
			{
				currentColor = defaultColor;
			}

			pos = tagEnd + 1;
		}

		return segments;
	}

	private static void appendSegment(List<TextSegment> list, String text, Color color)
	{
		if (!text.isEmpty())
		{
			list.add(new TextSegment(text, color));
		}
	}
}
