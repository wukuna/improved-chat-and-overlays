package com.improvedchat;

import com.improvedchat.model.OverlayMessage;
import com.improvedchat.overlay.DynamicChatOverlay;
import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.overlay.OverlayFilterMode;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayHistoryIntegrationTest {
    private static final class TestPlugin extends ImprovedChatPlugin {
        @Override public boolean isGameFilterEnabled() { return false; }
        @Override public boolean isBossKcFilterEnabled() { return false; }
    }

    @SuppressWarnings("unchecked")
    @Test public void realClearMenuPreservesOtherFilteredOverlays() throws Exception {
        TestPlugin plugin = new TestPlugin();
        Field configField = ImprovedChatPlugin.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(plugin, new ImprovedChatConfig() {
            @Override public boolean collapseDuplicates() { return true; }
        });
        Field historyField = ImprovedChatPlugin.class.getDeclaredField("messages");
        historyField.setAccessible(true);
        List<OverlayMessage> history = (List<OverlayMessage>) historyField.get(plugin);
        history.add(chat("hello"));
        history.add(chat("another message"));
        OverlayConfig a = OverlayConfig.createDefault("A", EnumSet.of(ChatMessageType.PUBLICCHAT), true);
        a.setFilterMode(OverlayFilterMode.SHOW_ONLY_MATCHES);
        a.setFilteredWords("hello");
        OverlayConfig b = OverlayConfig.createDefault("B", EnumSet.of(ChatMessageType.PUBLICCHAT), true);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (p, m, args) -> m.getReturnType() == boolean.class ? false : null);
        DynamicChatOverlay overlay = new DynamicChatOverlay(plugin, null, client, null, null, a);
        plugin.onOverlayMenuClicked(new OverlayMenuClicked(
                new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "A"), overlay));
        assertEquals(1, plugin.getMessagesForOverlay(a).size());
        plugin.onOverlayMenuClicked(new OverlayMenuClicked(
                new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear", "A history"), overlay));
        assertTrue(plugin.getMessagesForOverlay(a).isEmpty());
        assertEquals(2, plugin.getMessagesForOverlay(b).size());
        assertEquals(2, history.size());
        history.add(chat("hello"));
        assertEquals(1, plugin.getMessagesForOverlay(a).size());
        assertEquals(1, plugin.getMessagesForOverlay(a).get(0).getCount());
        assertEquals(3, plugin.getMessagesForOverlay(b).size());
    }

    @SuppressWarnings("unchecked")
    @Test public void unchangedFramesReuseSelectionAndEditsFiltersAndClearsInvalidateIt() throws Exception {
        TestPlugin plugin = new TestPlugin();
        Field configField = ImprovedChatPlugin.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(plugin, new ImprovedChatConfig() {});
        Field historyField = ImprovedChatPlugin.class.getDeclaredField("messages");
        historyField.setAccessible(true);
        List<OverlayMessage> history = (List<OverlayMessage>) historyField.get(plugin);
        OverlayConfig overlay = OverlayConfig.createDefault("Cache", EnumSet.of(ChatMessageType.PUBLICCHAT), true);
        overlay.setFadeOutDuration(0);
        history.add(chat("first"));
        List<OverlayMessage> first = plugin.getMessagesForOverlay(overlay);
        assertSame(first, plugin.getMessagesForOverlay(overlay));
        history.set(0, history.get(0).withBody("rewritten"));
        List<OverlayMessage> rewritten = plugin.getMessagesForOverlay(overlay);
        assertNotSame(first, rewritten);
        assertEquals("rewritten", rewritten.get(0).getMessage());
        overlay.setFilterMode(OverlayFilterMode.SHOW_ONLY_MATCHES);
        overlay.setFilteredWords("missing");
        assertTrue(plugin.getMessagesForOverlay(overlay).isEmpty());
        overlay.setFilteredWords("rewritten");
        assertEquals(1, plugin.getMessagesForOverlay(overlay).size());
        plugin.clearAllMessages();
        assertTrue(plugin.getMessagesForOverlay(overlay).isEmpty());
        history.add(chat("rewritten"));
        assertEquals(1, plugin.getMessagesForOverlay(overlay).size());
    }

    private OverlayMessage chat(String text) {
        return OverlayMessage.senderMessage("Alice", null, text, System.currentTimeMillis(), ChatMessageType.PUBLICCHAT, false);
    }
}
