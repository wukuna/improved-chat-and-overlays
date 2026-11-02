package com.improvedchat.release;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;

/** Bundled release notes, shown once per release in each RuneLite profile. */
@Singleton
public final class ReleaseNoticeModule
{
    static final String RELEASE = "2026-11-02-chat-wheel-fix";
    static final String SEEN_KEY = "releaseNoticeId";
    private static final String[] RECENT = {
        "Restored camera zoom while scroll-history overlays are enabled and removed their instructional footer.",
        "Overlay timestamps can show Time ago: now, 30s, 20m or 2h. Choose Timestamp style under Style → Text; ages update automatically.",
        "Overlay borders leave space on all four sides, with full-height lines to keep text and icons clear of the frame.",
        "Simple Overlays, Create and Tools tabs; click an overlay name for a resizable editor window with Messages, Style and Alerts tabs.",
        "Overlays can scroll through session history: enable Scroll history, use the wheel, and right-click Latest to return.",
    };

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ChatMessageManager chat;
    @Inject private net.runelite.client.eventbus.EventBus eventBus;
    private volatile boolean running;
    private volatile int generation;

    public synchronized void startUp() { if (running) return; running = true; generation++; eventBus.register(this); schedule(); }
    public synchronized void shutDown() { if (!running) return; running = false; generation++; eventBus.unregister(this); }

    private void schedule()
    {
        final int scheduledGeneration = generation;
        clientThread.invokeLater(() -> {
            if (running && generation == scheduledGeneration) showIfNeeded();
        });
    }

    private void showIfNeeded()
    {
        if (client.getGameState() != GameState.LOGGED_IN
            || RELEASE.equals(configManager.getConfiguration("improvedchat", SEEN_KEY)))
            return;
        send("Recent updates (newest first):");
        for (int i = 0; i < Math.min(5, RECENT.length); i++) send((i + 1) + ". " + RECENT[i]);
        configManager.setConfiguration("improvedchat", SEEN_KEY, RELEASE);
    }

    private void send(String text)
    {
        String message = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("[")
            .append(ChatColorType.HIGHLIGHT).append("Improved Chat and Overlays")
            .append(ChatColorType.NORMAL).append("] ").append(text).build();
        chat.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message).build());
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (running && event.getGameState() == GameState.LOGGED_IN) schedule();
    }

    @Subscribe public void onProfileChanged(ProfileChanged event)
    {
        // An old queued callback must not announce against a replacement profile.
        generation++;
        if (running) schedule();
    }
}
