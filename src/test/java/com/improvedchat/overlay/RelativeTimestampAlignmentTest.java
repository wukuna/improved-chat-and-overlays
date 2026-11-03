package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.model.AnimatedTextStyle;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.model.TextAlignment;
import com.improvedchat.model.TimestampStyle;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class RelativeTimestampAlignmentTest {
    private static final long SENT = 1_700_000_000_000L;
    private static final long[] AGES = {0, 1, 9, 10, 59, 60, 599, 600, 3599, 3600, 86400, 356400, 359999, 360000, 720000};

    @Test public void paintedMessageStaysInPlaceAcrossUnitsFontsAlignmentAndRgb() throws Exception {
        OverlayConfig config = config();
        OverlayMessage message = OverlayMessage.senderMessage("Alice", null, "Same message", SENT, ChatMessageType.PUBLICCHAT, false);
        DynamicChatOverlay overlay = new DynamicChatOverlay(mock(ImprovedChatPlugin.class), new ImprovedChatConfig(){},
                mock(Client.class), null, new ClanChatPresentationService(), config);
        Method draw = DynamicChatOverlay.class.getDeclaredMethod("drawSegments", Graphics2D.class, List.class,
                int.class, int.class, int.class, int.class, FontSize.class, FontMetrics.class, IndexedSprite[].class, boolean.class, long.class);
        draw.setAccessible(true);
        for (String family : new String[]{"RuneScape", "Arial", "Georgia", "Courier New"}) {
            config.setFontFamily(family);
            for (FontSize size : new FontSize[]{FontSize.REGULAR, FontSize.EXTRA_LARGE}) {
                config.setFontSize(size);
                for (AnimatedTextStyle style : AnimatedTextStyle.values()) {
                    config.setAnimatedTextStyle(style);
                    for (TextAlignment alignment : TextAlignment.values()) {
                        config.setTextAlignment(alignment);
                        BufferedImage reference = null;
                        int expectedWidth = -1;
                        for (long age : AGES) {
                            BufferedImage image = new BufferedImage(500, 60, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = image.createGraphics();
                            try {
                                FontMetrics metrics = FontStyler.setupGraphics(g, size, config);
                                List<TextSegment> segments = lines(message, config, metrics, age, false, 500).get(0).segments;
                                int width = segments.stream().mapToInt(s -> s.width).sum();
                                if (expectedWidth < 0) expectedWidth = width;
                                assertEquals("Line width at age " + age + " in " + family + " / " + style, expectedWidth, width);
                                draw.invoke(overlay, g, segments, 255, 40, 500, 4, size, metrics, null, false, SENT);
                                int column = 0;
                                for (TextSegment segment : segments) {
                                    column += segment.width;
                                    if (segment.iconId == TextSegment.SPACER) break;
                                }
                                int bodyX = DynamicChatOverlay.calculateAlignedX(alignment, 4, 490, width) + column + 1;
                                if (reference != null) {
                                    int painted = 0;
                                    for (int y = 0; y < 60; y++) for (int x = bodyX; x < 500; x++) {
                                        int alpha = image.getRGB(x, y) >>> 24;
                                        assertEquals("Message moved at age " + age + " in " + family + " / " + style,
                                                reference.getRGB(x, y) >>> 24, alpha);
                                        if (alpha != 0) painted++;
                                    }
                                    assertTrue("Must compare actual message pixels", painted > 20);
                                }
                                reference = image;
                            } finally { g.dispose(); }
                        }
                    }
                }
            }
        }
    }

    @Test public void wrappingAndRetainedTextSurviveAgeChangesAndTimestampExpiry() {
        OverlayConfig config = config();
        OverlayMessage message = OverlayMessage.senderMessage("Alice", "Clan", "A longer message with words that wrap across several lines", SENT, ChatMessageType.CLAN_CHAT, false);
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            FontMetrics metrics = FontStyler.setupGraphics(g, FontSize.REGULAR, config);
            List<String> expected = null;
            for (long age : AGES) {
                List<RenderLine> lines = lines(message, config, metrics, age, true, 160);
                assertTrue(lines.size() > 1);
                List<String> text = lines.stream().map(line -> line.segments.stream()
                        .filter(s -> s.iconId != TextSegment.SPACER && !s.text.matches("\\d{2}[smh] "))
                        .map(s -> s.text).collect(Collectors.joining())).collect(Collectors.toList());
                if (expected != null) assertEquals(expected, text);
                expected = text;
            }
            assertNull(ChatRenderUtils.formatTimeAgo(SENT, SENT + 360000000));
            assertEquals("A longer message with words that wrap across several lines", message.getMessage());
            config.setShowTimestamp(false);
            assertFalse(lines(message, config, metrics, 0, false, 500).get(0).segments.stream()
                    .anyMatch(s -> s.iconId == TextSegment.SPACER));
        } finally { g.dispose(); }
    }

    private static OverlayConfig config() {
        OverlayConfig config = new OverlayConfig();
        config.setShowTimestamp(true);
        config.setTimestampStyle(TimestampStyle.TIME_AGO);
        return config;
    }

    private static List<RenderLine> lines(OverlayMessage message, OverlayConfig config, FontMetrics metrics, long age, boolean wrap, int width) {
        return ChatRenderUtils.buildMessageLines(message, metrics, width, SENT + age * 1000, 0, wrap, Color.WHITE,
                false, false, config.getFontSize(), null, config.isShowTimestamp(), "[HH:mm]", true, null,
                Collections.emptyList(), null, config);
    }
}
