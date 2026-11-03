package com.improvedchat.dialogue;

import com.improvedchat.ImprovedChatConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.ClientTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
public final class DialogueFontsModule {
    @Inject private ImprovedChatConfig config;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private OverlayManager overlayManager;
    @Inject private DialogueFontsOverlay overlay;
    @Inject private DialogueWidgetManager widgetManager;

    private boolean started;

    public synchronized void startUp() {
        if (started || !config.enableDialogueFonts()) {
            return;
        }
        started = true;
        overlayManager.add(overlay);
        eventBus.register(this);
    }

    public synchronized void shutDown() {
        if (!started) {
            return;
        }
        started = false;
        eventBus.unregister(this);
        overlayManager.remove(overlay);
        widgetManager.restoreAll();
        overlay.setState(null);
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (!started) {
            return;
        }
        overlay.setState(widgetManager.getCurrentDialogue());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!started || !ImprovedChatConfig.GROUP.equals(event.getGroup())) {
            return;
        }

        String key = event.getKey();
        if ("replaceNpc".equals(key)
            || "replacePlayer".equals(key)
            || "replaceOptions".equals(key)
            || "replaceSprite".equals(key)) {
            clientThread.invoke(() -> {
                widgetManager.restoreAll();
                overlay.setState(null);
            });
        }
    }
}
