/*
 * Adapted portions from ChatboxOpacity by Trevor.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2019, Trevor
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
package com.improvedchat.chatbox.opacity;

import com.improvedchat.ImprovedChatConfig;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Singleton
public final class ChatboxOpacityModule {
    private static final int CHATBOX_GROUP_ID = 162;
    private static final int BUTTON_BACKGROUND_CHILD = 3;
    private static final int FILLED_BUTTON_TYPE = 3;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ImprovedChatConfig config;
    @Inject private EventBus eventBus;

    private final Map<Widget, BackgroundOpacity> backgroundOpacity = new IdentityHashMap<>();
    private boolean started;
    private boolean deferredApplyQueued;
    private Widget ownedButton;
    private ButtonStyle originalButton;
    private ButtonStyle appliedButton;

    public synchronized void startUp() {
        if (started || !config.enableChatboxOpacity()) return;
        started = true;
        eventBus.register(this);
        clientThread.invoke(this::apply);
    }

    public synchronized void shutDown() {
        if (!started) return;
        started = false;
        deferredApplyQueued = false;
        eventBus.unregister(this);
        clientThread.invoke(() -> {
            restoreBackground();
            restoreButton();
            if (client.getGameState() == GameState.LOGGED_IN) client.runScript(ScriptID.BUILD_CHATBOX);
        });
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.BUILD_CHATBOX) apply();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!ImprovedChatConfig.GROUP.equals(event.getGroup())) return;
        String key = event.getKey();
        if ("chatboxOpacity".equals(key) || "buttonOpacity".equals(key) || "opacityDialogueMenus".equals(key)) {
            clientThread.invokeLater(this::apply);
        }
    }

    public void reapplyAfterChatMutation() {
        if (!started) return;

        apply();

        if (!deferredApplyQueued) {
            deferredApplyQueued = true;
            clientThread.invokeLater(() -> {
                deferredApplyQueued = false;
                apply();
            });
        }
    }

    private void apply() {
        if (!started) return;

        if (client.getGameState() != GameState.LOGGED_IN
            || !client.isResized()
            || client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 0) {
            restoreBackground();
            restoreButton();
            return;
        }

        Widget background = client.getWidget(net.runelite.api.gameval.InterfaceID.Chatbox.CHAT_BACKGROUND);
        Widget[] children = background == null ? null : background.getDynamicChildren();
        if (children != null) {

            if (!config.opacityDialogueMenus() && isDialogueOrMenuSurface(children)) {
                restoreBackground();
            } else {
                Map<Widget, Boolean> live = new IdentityHashMap<>();
                for (Widget child : children) if (child != null) live.put(child, Boolean.TRUE);
                backgroundOpacity.entrySet().removeIf(entry -> {
                    if (live.containsKey(entry.getKey())) return false;
                    restoreBackgroundChild(entry.getKey(), entry.getValue());
                    return true;
                });
                if (config.chatboxOpacity() == -1) {
                    restoreBackground();
                } else {
                    for (Widget child : children) {
                        if (child == null) continue;
                        BackgroundOpacity snapshot = backgroundOpacity.get(child);
                        if (snapshot == null || child.getOpacity() != snapshot.applied) {
                            snapshot = new BackgroundOpacity(child.getOpacity());
                            backgroundOpacity.put(child, snapshot);
                        }
                        child.setOpacity(config.chatboxOpacity());
                        snapshot.applied = child.getOpacity();
                    }
                }
            }
        } else {
            restoreBackground();
        }

        Widget button = client.getWidget(CHATBOX_GROUP_ID, BUTTON_BACKGROUND_CHILD);
        if (button == null) { restoreButton(); return; }
        if (config.buttonOpacity() == -1) {
            restoreButton();
        } else {
            if (ownedButton != button || appliedButton == null || !appliedButton.matches(button)) {

                restoreButton();
                ownedButton = button;
                originalButton = new ButtonStyle(button);
            }
            button.setSpriteId(-1);
            button.setType(FILLED_BUTTON_TYPE);
            button.setFilled(true);
            button.setOpacity(config.buttonOpacity());
            appliedButton = new ButtonStyle(button);
        }
    }

    private static boolean isDialogueOrMenuSurface(Widget[] widgets) {
        for (Widget widget : widgets) {
            if (widget != null) {
                return widget.getType() == WidgetType.GRAPHIC;
            }
        }
        return false;
    }

    private static final class BackgroundOpacity {
        final int original;
        int applied;

        BackgroundOpacity(int opacity) {
            original = opacity;
            applied = opacity;
        }
    }

    private static void restoreBackgroundChild(Widget widget, BackgroundOpacity snapshot) {
        if (widget.getOpacity() == snapshot.applied) widget.setOpacity(snapshot.original);
    }

    private void restoreBackground() {
        for (Map.Entry<Widget, BackgroundOpacity> entry : backgroundOpacity.entrySet()) {
            restoreBackgroundChild(entry.getKey(), entry.getValue());
        }
        backgroundOpacity.clear();
    }

    private void restoreButton() {
        if (ownedButton != null && originalButton != null && appliedButton != null) {

            if (ownedButton.getSpriteId() == appliedButton.sprite) ownedButton.setSpriteId(originalButton.sprite);
            if (ownedButton.getOpacity() == appliedButton.opacity) ownedButton.setOpacity(originalButton.opacity);
            if (ownedButton.isFilled() == appliedButton.filled) ownedButton.setFilled(originalButton.filled);
            if (ownedButton.getType() == appliedButton.type) ownedButton.setType(originalButton.type);
        }
        ownedButton = null;
        originalButton = null;
        appliedButton = null;
    }

    private static final class ButtonStyle {
        final int sprite, opacity, type;
        final boolean filled;

        ButtonStyle(Widget widget) {
            sprite = widget.getSpriteId();
            opacity = widget.getOpacity();
            type = widget.getType();
            filled = widget.isFilled();
        }

        boolean matches(Widget widget) {
            return sprite == widget.getSpriteId() && opacity == widget.getOpacity()
                    && type == widget.getType() && filled == widget.isFilled();
        }
    }
}
