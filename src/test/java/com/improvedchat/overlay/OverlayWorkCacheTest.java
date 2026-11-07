package com.improvedchat.overlay;

import com.improvedchat.model.OverlayMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayWorkCacheTest {
    @Test public void rendererKeepsCachedTextWhileFadingAndRefreshesElapsedTimestamps() throws Exception {
        com.improvedchat.ImprovedChatPlugin plugin = org.mockito.Mockito.mock(com.improvedchat.ImprovedChatPlugin.class);
        OverlayConfig config = new OverlayConfig();
        config.setShowTimestamp(true);
        config.setTimestampStyle(com.improvedchat.model.TimestampStyle.TIME_AGO);
        DynamicChatOverlay overlay = new DynamicChatOverlay(plugin, new com.improvedchat.ImprovedChatConfig() {},
                org.mockito.Mockito.mock(net.runelite.api.Client.class), null,
                new com.improvedchat.chatbox.offline.ClanChatPresentationService(), config);
        java.lang.reflect.Method method = DynamicChatOverlay.class.getDeclaredMethod("messageLines", OverlayMessage.class,
                java.awt.FontMetrics.class, com.improvedchat.model.FontSize.class, net.runelite.api.IndexedSprite[].class,
                int.class, long.class, long.class, boolean.class, com.improvedchat.ChatColorSource.class);
        method.setAccessible(true);
        java.awt.Graphics2D graphics = new java.awt.image.BufferedImage(300, 100, 2).createGraphics();
        try {
            java.awt.FontMetrics metrics = FontStyler.setupGraphics(graphics, com.improvedchat.model.FontSize.REGULAR, config);
            OverlayMessage msg = OverlayMessage.gameMessage("Visible text", 1000, ChatMessageType.GAMEMESSAGE, false);
            Object[] args = {msg, metrics, com.improvedchat.model.FontSize.REGULAR, null, 280, 2000L, 1000L, true,
                    com.improvedchat.ChatColorSource.OPAQUE};
            List<RenderLine> first = (List<RenderLine>) method.invoke(overlay, args);
            args[5] = 2500L;
            List<RenderLine> faded = (List<RenderLine>) method.invoke(overlay, args);
            assertSame(first.get(0).segments, faded.get(0).segments);
            assertTrue(faded.get(0).alpha < first.get(0).alpha);
            args[5] = 3000L;
            List<RenderLine> nextSecond = (List<RenderLine>) method.invoke(overlay, args);
            assertNotSame(first.get(0).segments, nextSecond.get(0).segments);
            assertTrue(nextSecond.get(0).segments.stream().anyMatch(s -> "02s ".equals(s.text)));
            overlay.setOverlayConfig(config);
            List<RenderLine> refreshed = (List<RenderLine>) method.invoke(overlay, args);
            assertNotSame(nextSecond.get(0).segments, refreshed.get(0).segments);
        } finally { graphics.dispose(); }
    }

    private static OverlayMessage message(String body) {
        return OverlayMessage.gameMessage(body, 100, ChatMessageType.GAMEMESSAGE, false);
    }

    @Test public void snapshotIsReusedUntilAnyPoolMutationAndOldReadersStayStable() {
        OverlayMessageStore store = new OverlayMessageStore();
        store.add(message("first"));
        List<OverlayMessage> first = store.snapshot();
        assertSame(first, store.snapshot());
        store.set(0, first.get(0).withBody("updated"));
        assertNotSame(first, store.snapshot());
        assertEquals("first", first.get(0).getMessage());
        store.add(message("second"));
        List<OverlayMessage> two = store.snapshot();
        store.removeIf(m -> m.getMessage().equals("updated"));
        assertEquals(2, two.size());
        assertEquals(1, store.snapshot().size());
        store.clear();
        assertTrue(store.snapshot().isEmpty());
    }

    @Test public void selectionCacheInvalidatesOnSourceFiltersExpiryAndClockRollback() {
        OverlaySelectionCache cache = new OverlaySelectionCache();
        List<OverlayMessage> source = Arrays.asList(message("first"));
        List<OverlayMessage> selected = cache.put(source, "filter", 100, 200, new ArrayList<>(source));
        assertSame(selected, cache.get(source, "filter", 199));
        assertNull(cache.get(source, "different filter", 199));
        assertNull(cache.get(new ArrayList<>(source), "filter", 199));
        assertNull(cache.get(source, "filter", 200));
        assertNull(cache.get(source, "filter", 99));
    }

    @Test public void shortViewStopsBeforeOldHistoryButFinishesOldestVisibleDuplicateGroup() {
        List<OverlayMessage> history = new ArrayList<>();
        for (int i = 0; i < 5000; i++) history.add(message("message " + i));
        AtomicInteger inspected = new AtomicInteger();
        List<OverlayMessage> selected = OverlayHistory.select(history, 0, 10, false,
                m -> { inspected.incrementAndGet(); return true; });
        assertEquals(10, inspected.get());
        assertEquals("message 4990", selected.get(0).getMessage());
        OverlayMessage first = message("duplicate"), last = message("<col=ff0000>duplicate</col>");
        history.add(first);
        history.add(last);
        selected = OverlayHistory.select(history, 0, 1, true);
        assertEquals(2, selected.get(0).getCount());
        assertEquals(first.getSequence(), selected.get(0).getFirstSequence());
        assertEquals(last.getSequence(), selected.get(0).getSequence());
        assertEquals(last.getMessage(), selected.get(0).getMessage());
    }

    @Test public void layoutReusesSegmentsWhileFadeChangesAndEvictsOldEntries() {
        OverlayLayoutCache cache = new OverlayLayoutCache();
        AtomicInteger layouts = new AtomicInteger();
        java.util.function.Supplier<List<RenderLine>> build = () -> {
            layouts.incrementAndGet();
            return Collections.singletonList(new RenderLine(Collections.singletonList(
                    new TextSegment("text", -1, 20, java.awt.Color.WHITE)), 25));
        };
        List<RenderLine> full = cache.layout(1, "width100", 255, build);
        List<RenderLine> faded = cache.layout(1, "width100", 70, build);
        assertSame(full.get(0).segments, faded.get(0).segments);
        assertEquals(255, full.get(0).alpha);
        assertEquals(70, faded.get(0).alpha);
        assertEquals(1, layouts.get());
        cache.layout(1, "width200", 255, build);
        assertEquals(2, layouts.get());
        for (int i = 2; i <= 258; i++) cache.layout(i, "width200", 255, build);
        int before = layouts.get();
        cache.layout(1, "width200", 255, build);
        assertEquals(before + 1, layouts.get());
        cache.clear();
        cache.layout(1, "width200", 255, build);
        assertEquals(before + 2, layouts.get());
    }
}
