/*
 * Adapted portions from Collapse Chat by stutify.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, stutify
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

package com.improvedchat.chatbox.collapse;

import com.improvedchat.ImprovedChatConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.IdentityHashMap;
import java.util.Map;

@Slf4j
@Singleton
public class ChatWidgetManager {
    @Inject
    private Client client;

    @Inject
    private ImprovedChatConfig config;

    private static final String ORIGINAL_ALL_BUTTON_TEXT = "All";
    private final Map<Widget, Boolean> collapsedTabs = new IdentityHashMap<>();
    private Widget collapsedBackground;
    private boolean backgroundWasHidden;

    public void updateChatWidgets(ChatState state) {
        updateControlsBackground(state.isCollapsed());
        toggleChatButtonVisibility(state);
        updateAllButtonGraphic(state);
        updateButtonContent(state);
    }

    private void updateControlsBackground(boolean collapsed) {
        Widget background = client.getWidget(InterfaceID.Chatbox.CONTROLS_BACKGROUND_GRAPHIC);
        if (collapsedBackground != null && (!collapsed || collapsedBackground != background)) {
            if (collapsedBackground.isSelfHidden()) collapsedBackground.setHidden(backgroundWasHidden);
            collapsedBackground = null;
        }
        if (!collapsed || background == null) return;
        if (collapsedBackground == null) {
            collapsedBackground = background;
            backgroundWasHidden = background.isSelfHidden();
        }

        background.setHidden(true);
    }

    private void toggleChatButtonVisibility(ChatState state) {
        Map<Widget, Boolean> live = new IdentityHashMap<>();
        if (state.isCollapsed()) {
            for (int buttonID : ChatButton.CONTAINER_IDS_TO_TOGGLE) {
                Widget widget = client.getWidget(buttonID);
                if (widget != null) live.put(widget, Boolean.TRUE);
            }
        }
        collapsedTabs.entrySet().removeIf(entry -> {
            if (live.containsKey(entry.getKey())) return false;
            if (entry.getKey().isSelfHidden()) entry.getKey().setHidden(entry.getValue());
            return true;
        });
        for (Widget widget : live.keySet()) {
            if (!collapsedTabs.containsKey(widget) || !widget.isSelfHidden()) {
                collapsedTabs.put(widget, widget.isSelfHidden());
            }
            widget.setHidden(true);
        }
    }

    private void updateAllButtonGraphic(ChatState state) {
        runOnWidgetIfPresent(ChatButton.ALL.graphicID, (Widget w) -> {
            boolean shouldBeTransparent = state.isCollapsed() && config.collapsedButtonTransparent();
            w.setOpacity(shouldBeTransparent ? 255 : 0);

            if (config.collapsedButtonTransparent()) {
                return;
            }

            w.setSpriteId(determineAllButtonSprite(state));
        });
    }

    private int determineAllButtonSprite(ChatState state) {
        if (state.isCollapsed()) {
            if (state.isMouseOverAllButton) {
                return SpriteID.ChatTabButton.HOVERED;
            }
            if (state.hasUnseenMessages) {
                return SpriteID.ChatTabButton.NEW_MESSAGES;
            }
        } else {
            if (state.selectedChatButton == ChatButton.ALL && state.isMouseOverAllButton) {
                return SpriteID.ChatTabButton.SELECTED_HOVERED;
            }
            if (state.selectedChatButton == ChatButton.ALL) {
                return SpriteID.ChatTabButton.SELECTED;
            }
            if (state.isMouseOverAllButton) {
                return SpriteID.ChatTabButton.HOVERED;
            }
        }
        return SpriteID.ChatTabButton.BUTTON;
    }

    public void setupMouseListeners(ChatState state, Runnable onStateChanged) {
        runOnWidgetIfPresent(ChatButton.ALL.containerID, (Widget allButton) -> {
            allButton.setOnMouseOverListener((JavaScriptCallback) ev -> {
                state.isMouseOverAllButton = true;
                onStateChanged.run();
            });
            allButton.setOnMouseLeaveListener((JavaScriptCallback) ev -> {
                state.isMouseOverAllButton = false;
                onStateChanged.run();
            });
            allButton.setHasListener(true);
        });
    }

    private void updateButtonContent(ChatState state) {
        if (state.collapseState != ChatCollapseState.COLLAPSED) {
            updateTextWidgetIfChanged(ChatButton.ALL.textID, ORIGINAL_ALL_BUTTON_TEXT);
            return;
        }

        String textToSet = ORIGINAL_ALL_BUTTON_TEXT;
        switch (config.collapsedButtonContent()) {
            case STATIC_TEXT:
                textToSet = state.isMouseOverAllButton
                        ? config.collapsedButtonContentCustomTextHovered()
                        : config.collapsedButtonContentCustomText();
                break;
            case REPORT_BUTTON_TEXT:
                Widget reportText = client.getWidget(ChatButton.REPORT.textID);
                if (reportText != null) {
                    textToSet = reportText.getText();
                }
                break;
        }
        updateTextWidgetIfChanged(ChatButton.ALL.textID, textToSet);
    }

    private void updateTextWidgetIfChanged(int textWidgetID, String newText) {
        runOnWidgetIfPresent(textWidgetID, (Widget w) -> {
            if (!w.getText().equals(newText)) {
                w.setText(newText);
            }
        });
    }

    public ChatButton getSelectedChatButton() {
        for (int buttonGraphicId : ChatButton.ALL_GRAPHIC_IDS) {
            Widget graphic = client.getWidget(buttonGraphicId);
            if (graphic == null || graphic.getSpriteId() == -1) {
                continue;
            }

            int sprite = graphic.getSpriteId();
            if (sprite == SpriteID.ChatTabButton.SELECTED || sprite == SpriteID.ChatTabButton.SELECTED_HOVERED) {
                return ChatButton.fromGraphicID(buttonGraphicId);
            }
        }
        return null;
    }

    private void runOnWidgetIfPresent(int widgetID, Consumer<Widget> action) {
        Widget w = client.getWidget(widgetID);
        if (w != null) {
            action.accept(w);
        }
    }
}
