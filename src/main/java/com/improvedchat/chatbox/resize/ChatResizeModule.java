/*
 * Adapted portions from Chat Resizer by shanktank.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2026, shanktank
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

package com.improvedchat.chatbox.resize;

import com.improvedchat.chatbox.opacity.ChatboxOpacityModule;
import com.improvedchat.chatbox.resize.internal.ChatRebuild;
import com.improvedchat.chatbox.resize.internal.RawScripts;
import com.improvedchat.chatbox.resize.internal.SizeClamps;
import com.improvedchat.chatbox.resize.internal.Widgets;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CanvasSizeChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import java.awt.Dimension;
import com.improvedchat.ImprovedChatConfig;
import java.awt.Rectangle;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatResizeModule {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ImprovedChatConfig config;
    @Inject private KeyManager keyManager;
    @Inject private MouseManager mouseManager;
    @Inject private OverlayManager overlayManager;
    @Inject private EventBus eventBus;
    @Inject private ChatboxOpacityModule chatboxOpacityModule;

    @Inject private RuneLiteHudAnchors hudAnchors;
    @Inject private TopLevelModals mainModals;
    @Inject private OverlayTransitions transitions;
    @Inject private ChatDialogBoxes dialogBoxes;
    @Inject private FixedModeChat fixedChat;
    @Inject private ResizableModeChat resizable;
    @Inject private ChatScrollRetainer scrollKeep;
    @Inject private RuneLiteChatInput rlInput;
    @Inject private SecondarySize swapSize;
    @Inject private DragResizeActuator dragResizeActuator;
    @Inject private DragResizePreview dragResizePreview;

    private volatile Events events;

    private boolean started;
    private Dimension lastOpacityTrackedSize;

    public synchronized void startUp() {
        if (started || !config.enableResizableChat()) {
            return;
        }
        started = true;
        lastOpacityTrackedSize = null;
        keyManager.registerKeyListener(hideChatHotkey);
        keyManager.registerKeyListener(swapSizeHotkey);
        keyManager.registerKeyListener(dragResizeActuator.getKeyListener());
        keyManager.registerKeyListener(hudAnchors.dragHotkeyListener);
        mouseManager.registerMouseListener(dragResizeActuator);
        overlayManager.add(dragResizePreview);
        overlayManager.add(hudAnchors.indicatorPresent);
        overlayManager.add(hudAnchors.indicatorRestore);

        clientThread.invoke(() -> {

            transitions.reset();
            scrollKeep.reset();
            swapSize.reset();
            enable();
        });
    }

    public synchronized void shutDown() {
        if (!started) {
            return;
        }
        started = false;
        lastOpacityTrackedSize = null;
        unregisterHandlers();
        keyManager.unregisterKeyListener(hideChatHotkey);
        keyManager.unregisterKeyListener(swapSizeHotkey);
        keyManager.unregisterKeyListener(dragResizeActuator.getKeyListener());
        keyManager.unregisterKeyListener(hudAnchors.dragHotkeyListener);
        mouseManager.unregisterMouseListener(dragResizeActuator);
        overlayManager.remove(dragResizePreview);
        overlayManager.remove(hudAnchors.indicatorPresent);
        overlayManager.remove(hudAnchors.indicatorRestore);
        dragResizeActuator.reset();

        clientThread.invoke(() -> {
            unregisterHandlers();
            scrollKeep.sync();
            if (client.isResized()) {
                resizable.restore();
                ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
                chatboxOpacityModule.reapplyAfterChatMutation();
                resizable.restoreBackground();
                mainModals.relayout();
            } else {
                fixedChat.restore();
                ChatRebuild.now(client, RawScripts.REWRAPS_CHAT);
                if (mainModals.isTopLevelModalOpen()) mainModals.relayout();
            }
            if (dialogBoxes.isDialogOpen()) dialogBoxes.resetDialogPositions();
            rlInput.refit();
            scrollKeep.sync();
            chatboxOpacityModule.reapplyAfterChatMutation();
        });
    }

    private void enable() {
        if (!started) {
            return;
        }
        registerHandlers();
        scrollKeep.sync();
        if (client.isResized()) resizable.onEnable(); else fixedChat.onEnable();
        scrollKeep.sync();
    }

    private void registerHandlers() {
        assert events == null : "registerHandlers called while already armed";
        unregisterHandlers();
        events = new Events();
        eventBus.register(events);
        eventBus.register(fixedChat);
        eventBus.register(hudAnchors);
        eventBus.register(hudAnchors.beforeDrawRestore);
    }

    private void unregisterHandlers() {
        Events e = events;
        events = null;
        if (e != null) eventBus.unregister(e);
        eventBus.unregister(fixedChat);
        eventBus.unregister(hudAnchors);
        eventBus.unregister(hudAnchors.beforeDrawRestore);
    }

    private boolean primarySizeEdited(String key) {
        return client.isResized()
            ? key.equals(ImprovedChatConfig.WIDTH_CHANGE) || key.equals(ImprovedChatConfig.HEIGHT_CHANGE)
            : key.equals(ImprovedChatConfig.FIXED_HEIGHT_CHANGE);
    }

    private boolean swapUnusableAfter(String key) {
        return (key.equals(ImprovedChatConfig.SWAP_SIZE_KEYBIND) && config.secondaryKeybind().equals(Keybind.NOT_SET))
            || (key.equals(ImprovedChatConfig.SWAP_SIZE_MODE) && config.secondaryMode() == ImprovedChatConfig.Mode.HOLD);
    }

    private boolean widthChanged(String key) {
        return key.equals(ImprovedChatConfig.WIDTH_CHANGE) || key.equals(ImprovedChatConfig.SWAP_WIDTH_CHANGE)
            || clampGateChanged(key);
    }

    private boolean heightChanged(String key) {
        return key.equals(ImprovedChatConfig.HEIGHT_CHANGE) || key.equals(ImprovedChatConfig.SWAP_HEIGHT_CHANGE)
            || key.equals(ImprovedChatConfig.GROW_INTERFACES) || clampGateChanged(key);
    }

    private boolean clampGateChanged(String key) {
        return key.equals(ImprovedChatConfig.REVERT_FOR_DIALOGS) || key.equals(ImprovedChatConfig.REVERT_FOR_MODALS);
    }

    private void setSwapActive(boolean active) {
        if (!swapSize.setActive(active)) return;
        if (dragResizeActuator.isDragging()) return;
        reapplySizes(true, true);
    }

    private void reapplySizes(boolean widthChanged, boolean bandChanged) {
        if (events == null) return;
        scrollKeep.sync();
        if (!config.fixedTabCollapse()) fixedChat.setCollapsed(false);
        apply(true);
        if (client.isResized()) {

            ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
            chatboxOpacityModule.reapplyAfterChatMutation();
            if (widthChanged || (bandChanged && mainModals.isModalOpen())) mainModals.relayout();
            if (bandChanged) resizable.consumeRelayoutNeeded();
        } else if (fixedChat.consumeRebuildNeeded()) {
            ChatRebuild.now(client, RawScripts.REWRAPS_CHAT);
        }
        scrollKeep.sync();
    }

    private boolean overlayTransitioned() {
        OverlayTransitions.Edges edges = transitions.poll();
        if (edges.modalChanged) return true;
        if (edges.dialogChanged) {

            if (!client.isResized()) {
                return dialogShifts(fixedChat.effectiveHeightChange(false), fixedChat.effectiveHeightChange(true));
            } else {
                int w = swapSize.effectiveWidthChange();
                return dialogShifts(w, w) || dialogShifts(resizable.effectiveHeightChange(false), resizable.effectiveHeightChange(true));
            }
        }
        return false;
    }

    private boolean dialogShifts(int closed, int open) {
        return closed != SizeClamps.clamp(open, !client.isResized(), false, true, config);
    }

    private boolean modalClampShifts(int delta) {
        return delta != SizeClamps.clamp(delta, true, true, false, config);
    }

    private void applyOverlayTransition() {
        apply(false);
        if (client.isResized()) {
            mainModals.relayout();
            resizable.consumeRelayoutNeeded();
            ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
            chatboxOpacityModule.reapplyAfterChatMutation();
        } else if (!dragResizeActuator.isDragging() && fixedChat.consumeRelayoutNeeded()) {
            mainModals.relayout();
        }
        scrollKeep.sync();
    }

    private Dimension apply(boolean force) {
        return client.isResized() ? resizable.apply(force) : fixedChat.apply(force);
    }

    private void syncOpacityAfterSizeChange(Dimension size) {
        if (size == null) {
            return;
        }
        if (!size.equals(lastOpacityTrackedSize)) {
            lastOpacityTrackedSize = size;
            chatboxOpacityModule.reapplyAfterChatMutation();
        }
    }

    private final HotkeyListener hideChatHotkey = new HotkeyListener(
        () -> config.enableCollapsibleChat() ? Keybind.NOT_SET : config.toggleShowChat()) {
        @Override public void hotkeyPressed() {
            clientThread.invoke(() -> {

                if (!config.enableCollapsibleChat()) toggleChat();
            });
        }
    };

    public void toggleChat() {
        if (events == null || client.getGameState() != GameState.LOGGED_IN) return;
        if (client.isResized()) {
            resizable.toggleHidden();
        } else if (config.fixedTabCollapse()) {
            fixedChat.setCollapsed(!fixedChat.isCollapsed());
        }
    }

    private final HotkeyListener swapSizeHotkey = new HotkeyListener(() -> config.secondaryKeybind()) {
        @Override public void hotkeyPressed() {
            boolean toggle = config.secondaryMode() == ImprovedChatConfig.Mode.TOGGLE;
            clientThread.invoke(() -> setSwapActive(!toggle || !swapSize.isActive()));
        }
        @Override public void hotkeyReleased() {
            if (config.secondaryMode() == ImprovedChatConfig.Mode.HOLD) clientThread.invoke(() -> setSwapActive(false));
        }
    };

    private final class Events {
        private boolean wasDragging;

        private boolean transitionPending;
        private boolean refitPending;

        private final Queue<String> configEdits = new ConcurrentLinkedQueue<>();

        @Subscribe
        private void onConfigChanged(ConfigChanged event) {
            if (!ImprovedChatConfig.GROUP.equals(event.getGroup()) || dragResizeActuator.isDragging()) return;
            configEdits.add(event.getKey());
        }

        @Subscribe
        private void onCanvasSizeChanged(CanvasSizeChanged event) {
            refitPending = true;
        }

        @Subscribe
        private void onResizeableChanged(ResizeableChanged event) {

            unregisterHandlers();
            if (event.isResized()) {
                fixedChat.restore();
            } else {
                resizable.restore();
            }
            clientThread.invokeLater(ChatResizeModule.this::enable);
        }

        @Subscribe
        private void onScriptPreFired(ScriptPreFired event) {

            if (config.growInterfaces() && config.revertForModals().ungrows() &&
                resizable.effectiveHeightChange(false) > 0 &&
                !mainModals.isModalOpen() && mainModals.isTopLevelModalOpen()
            ) {
                hudAnchors.forceStockRendered();
            }

            if (!client.isResized() && !mainModals.isModalOpen() &&
                modalClampShifts(fixedChat.effectiveHeightChange(false)) &&
                transitions.pollModalEdge()
            ) {
                apply(false);
                transitionPending = true;
            }

            int id = event.getScriptId();
            mainModals.cacheRefit(id, event.getScriptEvent());
            if (id == ScriptID.BUILD_CHATBOX || id == ScriptID.SPLITPM_CHANGED || id == RawScripts.TOPLEVEL_RELAYOUT) apply(false);
        }

        @Subscribe
        private void onScriptPostFired(ScriptPostFired event) {
            int id = event.getScriptId();
            if (id == ScriptID.TOPLEVEL_REDRAW) apply(false);

            if (id == ScriptID.MESSAGE_LAYER_OPEN) apply(false);
        }

        @Subscribe
        private void onPostClientTick(PostClientTick event) {
            boolean preApplied = transitionPending;
            transitionPending = false;
            boolean transitioned = overlayTransitioned();
            if (transitioned || preApplied) applyOverlayTransition();

            if (refitPending) {
                refitPending = false;
                mainModals.relayout();
            }
        }

        @Subscribe
        private void onBeforeRender(BeforeRender event) {
            boolean dragging = dragResizeActuator.isDragging();

            if (dragging) {
                if (!wasDragging) fixedChat.setCollapsed(false);
                Dimension size = apply(false), last = dragResizeActuator.getLastDragSize();
                syncOpacityAfterSizeChange(size);
                if (client.isResized() && config.liveRewrap() && size != null && !size.equals(last))
                    client.runScript(RawScripts.RESIZES_CHAT);
                dragResizeActuator.setLastDragSize(size);
            } else {
                adoptConfigEdits();
                Dimension settledSize = apply(false);
                syncOpacityAfterSizeChange(settledSize);
                if (wasDragging && client.isResized()) {
                    ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
                    chatboxOpacityModule.reapplyAfterChatMutation();
                    mainModals.relayout();
                    resizable.consumeRelayoutNeeded();
                    scrollKeep.noteRewrap();
                }
                dragResizeActuator.setLastDragSize(null);
            }
            wasDragging = dragging;

            if (client.isResized() && resizable.consumeEnablePending()) resizable.onEnable();

            if (!client.isResized() && client.getGameState() == GameState.LOGGED_IN) fixedChat.syncCollapsedTab();

            if (!dragging && fixedChat.consumeRelayoutNeeded()) mainModals.relayout();

            if (!client.isResized() && !dragging && fixedChat.consumeRebuildNeeded()) client.refreshChat();

            if (client.isResized() && !dragging && resizable.consumeRelayoutNeeded()) {
                ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
                chatboxOpacityModule.reapplyAfterChatMutation();
                mainModals.relayout();
            }

            rlInput.refit();
            scrollKeep.sync();

            Widget slot = Widgets.chatSlot(client);
            Rectangle bounds = slot == null ? null : Widgets.liveBounds(slot);
            dragResizeActuator.update(bounds, !client.isResized(), client.getCanvasWidth(), client.getCanvasHeight());

            hudAnchors.presentAnchorHeight();

        }

        @Subscribe
        private void onCommandExecuted(CommandExecuted event) {
            if (event.getCommand().equals("testpm")) {
                String message = "ABCDEFGHIJKLMNO PQRSTUVWXYZ ABCDEFG HIJKLMNOP QRSTU VWX YZ AB CD EF G H I J K L M N O P Q R S T U V W X Y Z";
                if (event.getArguments().length != 0) message = String.join(" ", event.getArguments());
                client.addChatMessage(ChatMessageType.MODPRIVATECHAT, "Test", message, null);

            } else if (event.getCommand().equals("clearpm")) {
                ChatLineBuffer buffer = client.getChatLineMap().get(ChatMessageType.MODPRIVATECHAT.getType());
                for (MessageNode node : buffer.getLines().clone()) buffer.removeMessageNode(node);

                clientThread.invokeAtTickEnd(() -> ChatRebuild.now(client, ScriptID.SPLITPM_CHANGED));
            } else if (event.getCommand().equals("lorem")) {
                String lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et "
                    + "dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo "
                    + "consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. "
                    + "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
                java.util.Random rand = new java.util.Random();
                for (int n = 0; n < (event.getArguments().length != 0 ? Integer.parseInt(event.getArguments()[0]) : 1); n++) {
                    for (int i = 0, j = 0; i < lorem.length(); i = j) {
                        String name = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(rand.nextInt(6) + 4);
                        j = Math.min(j + rand.nextInt(90) + 40, lorem.length());
                        client.addChatMessage(ChatMessageType.MODCHAT, name, lorem.substring(i, j).trim(), null);
                    }
                }
            }
        }

        private void adoptConfigEdits() {
            if (configEdits.isEmpty()) return;
            boolean width = false, band = false, unusable = false;
            for (String key = configEdits.poll(); key != null; key = configEdits.poll()) {
                width = width || widthChanged(key);
                band = band || heightChanged(key);
                unusable = unusable || swapUnusableAfter(key) || primarySizeEdited(key);
            }
            boolean deactivated = unusable && swapSize.setActive(false);
            reapplySizes(deactivated || width, deactivated || band);
        }
    }
}
