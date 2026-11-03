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
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

@Singleton
public class DialogueFontsOverlay extends Overlay
{

	private static final Color OPTION_TITLE_COLOR = new Color(0x80, 0x00, 0x00);

	private static final Color NAME_COLOR = new Color(0x00, 0x00, 0x80);

	private static final Color PARCHMENT_COLOR = new Color(0xD6, 0xCC, 0xAF);

	private static final int V_PADDING = 4;

	@Inject
	private Client client;

	@Inject
	private ImprovedChatConfig config;

	@Inject
	private FontRenderer fontRenderer;

	private volatile DialogueState currentState;

	@Inject
	DialogueFontsOverlay()
	{
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.HIGH);
	}

	public void setState(DialogueState state)
	{
		this.currentState = state;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		DialogueState state = currentState;
		if (state == null)
		{
			return null;
		}

		reBlankWidgets(state);

		fontRenderer.applyRenderingHints(graphics);

		switch (state.getType())
		{
			case NPC_DIALOGUE:
				if (config.replaceNpc())
				{
					renderNpcDialogue(graphics, state);
				}
				break;

			case PLAYER_DIALOGUE:
				if (config.replacePlayer())
				{
					renderPlayerDialogue(graphics, state);
				}
				break;

			case OPTION_DIALOGUE:
				if (config.replaceOptions())
				{
					renderOptionDialogue(graphics, state);
				}
				break;

			case SPRITE_DIALOGUE:
				if (config.replaceSprite())
				{
					renderSpriteDialogue(graphics, state);
				}
				break;

			default:
				break;
		}

		return null;
	}

	private void renderNpcDialogue(Graphics2D g, DialogueState state)
	{
		Widget textWidget = state.getTextWidget();
		if (textWidget == null || textWidget.isHidden())
		{
			return;
		}

		Rectangle bounds = textWidget.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}

		Widget nameWidget = state.getNameWidget();
		if (nameWidget != null && !nameWidget.isHidden() && state.getNpcName() != null && !state.getNpcName().isEmpty())
		{
			Rectangle nameBounds = nameWidget.getBounds();
			if (nameBounds != null && nameBounds.width > 0)
			{
				fontRenderer.drawCenteredString(
					g, state.getNpcName(), nameBounds,
					centreY(g, nameBounds, fontRenderer.getFont()),
					NAME_COLOR);
			}
		}

		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);

		renderContinueText(g, state.getContinueWidget(), state.getContinueText());
	}

	private void renderPlayerDialogue(Graphics2D g, DialogueState state)
	{
		Widget textWidget = state.getTextWidget();
		if (textWidget == null || textWidget.isHidden())
		{
			return;
		}

		Rectangle bounds = textWidget.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}

		Widget nameWidget = state.getNameWidget();
		if (nameWidget != null && !nameWidget.isHidden() && state.getNpcName() != null && !state.getNpcName().isEmpty())
		{
			Rectangle nameBounds = nameWidget.getBounds();
			if (nameBounds != null && nameBounds.width > 0)
			{
				fontRenderer.drawCenteredString(
					g, state.getNpcName(), nameBounds,
					centreY(g, nameBounds, fontRenderer.getFont()),
					NAME_COLOR);
			}
		}

		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);

		renderContinueText(g, state.getContinueWidget(), state.getContinueText());
	}

	private void renderOptionDialogue(Graphics2D g, DialogueState state)
	{
		Widget container = state.getTextWidget();
		if (container == null || container.isHidden())
		{
			return;
		}

		Rectangle containerBounds = container.getBounds();
		if (containerBounds == null || containerBounds.width <= 0)
		{
			return;
		}

		Font optionFont = fontRenderer.getOptionFont();

		Widget titleWidget = state.getNameWidget();
		if (titleWidget != null && !titleWidget.isHidden())
		{
			Rectangle titleBounds = titleWidget.getBounds();
			if (titleBounds != null && titleBounds.width > 0)
			{
				fontRenderer.drawCenteredString(
					g, state.getNpcName(), titleBounds,
					centreY(g, titleBounds, optionFont),
					OPTION_TITLE_COLOR, optionFont);
			}
		}

		Widget[] optionWidgets = state.getOptionWidgets();
		List<String> options = state.getOptions();

		if (optionWidgets == null || options == null)
		{
			return;
		}

		Point mouse = client.getMouseCanvasPosition();

		for (int i = 0; i < optionWidgets.length && i < options.size(); i++)
		{
			Widget optWidget = optionWidgets[i];
			if (optWidget == null || optWidget.isHidden())
			{
				continue;
			}

			Rectangle optBounds = optWidget.getBounds();
			if (optBounds == null || optBounds.width <= 0)
			{
				continue;
			}

			boolean hovered = mouse != null
				&& optBounds.contains(mouse.getX(), mouse.getY());
			Color textColor = hovered ? Color.WHITE : Color.BLACK;

			fontRenderer.drawCenteredString(
				g, options.get(i), optBounds,
				centreY(g, optBounds, optionFont),
				textColor, optionFont);
		}
	}

	private static int centreY(Graphics2D g, Rectangle bounds, Font font)
	{
		FontMetrics fm = g.getFontMetrics(font);
		return bounds.y + (bounds.height - fm.getHeight()) / 2;
	}

	private void renderSpriteDialogue(Graphics2D g, DialogueState state)
	{
		Widget textWidget = state.getTextWidget();
		if (textWidget == null || textWidget.isHidden())
		{
			return;
		}

		Rectangle bounds = textWidget.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}

		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);

		Widget spriteRoot = client.getWidget(net.runelite.api.widgets.InterfaceID.DIALOG_SPRITE, 0);
		Widget liveContinue = null;
		if (spriteRoot != null)
		{
			Widget[] dyn = spriteRoot.getDynamicChildren();
			if (dyn != null && dyn.length > DialogueWidgetManager.SPRITE_CONTINUE_DYN_INDEX)
			{
				liveContinue = dyn[DialogueWidgetManager.SPRITE_CONTINUE_DYN_INDEX];
			}
		}
		if (liveContinue != null)
		{
			safeBlank(liveContinue);
		}
		renderContinueText(g, liveContinue != null ? liveContinue : state.getContinueWidget(),
			state.getContinueText());
	}

	private void renderContinueText(Graphics2D g, Widget widget, String fallbackText)
	{
		if (widget == null)
		{
			return;
		}

		String liveText = widget.getText();
		String text = (liveText != null && !liveText.isEmpty())
			? DialogueWidgetManager.stripTags(liveText)
			: fallbackText;

		if (text == null || text.isEmpty())
		{
			return;
		}

		Rectangle cb = widget.getBounds();
		if (cb == null || cb.width <= 0)
		{
			return;
		}

		Font font = fontRenderer.getFont();
		fontRenderer.drawCenteredString(
			g, text, cb,
			centreY(g, cb, font),
			NAME_COLOR, font);
	}

	private void reBlankWidgets(DialogueState state)
	{

		safeBlank(state.getTextWidget());

		if (state.getType() == DialogueType.OPTION_DIALOGUE)
		{

			safeCamouflage(state.getNameWidget());
			Widget[] optWidgets = state.getOptionWidgets();
			if (optWidgets != null)
			{
				for (Widget opt : optWidgets)
				{
					safeCamouflage(opt);
				}
			}
		}
		else
		{
			safeBlank(state.getNameWidget());

			if (state.getType() == DialogueType.SPRITE_DIALOGUE)
			{
				safeBlank(state.getContinueWidget());
			}
			else
			{
				safeCamouflage(state.getContinueWidget());
			}
			Widget[] optWidgets = state.getOptionWidgets();
			if (optWidgets != null)
			{
				for (Widget opt : optWidgets)
				{
					safeBlank(opt);
				}
			}
		}
	}

	private static void safeBlank(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		String t = widget.getText();
		if (t != null && !t.isEmpty())
		{
			widget.setText("");
		}
	}

	private static void safeCamouflage(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		widget.setTextColor(DialogueWidgetManager.OPTION_CAMOUFLAGE_COLOR);
	}
}

