package com.improvedchat.overlay;

import com.google.gson.Gson;
import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.model.AnimatedTextStyle;
import com.improvedchat.model.BorderThickness;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.OverlayMessage;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class OverlayColorsTest {
    @Test public void oldDuoHighlightKeepsItsPairUntilTheHighlightEffectChanges() {
        OverlayConfig config = new OverlayConfig();
        config.setCustomRgbCycle(true); config.setRgbColor1(Color.RED); config.setRgbColor2(Color.BLUE);
        config.getTextColors().setMode(AnimatedTextStyle.DUO_CYCLE);
        config.getTextColors().setPrimary(Color.GREEN);
        for (int step = 0; step < 2; step++) {
            BufferedImage image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            RgbAnimation.drawText(g, "Highlight", 255, 2, 20, false, g.getFontMetrics(),
                    config, step * 4000L, 0, AnimatedTextStyle.RGB_CYCLE);
            g.dispose();
            int expected = step == 0 ? Color.RED.getRGB() : Color.BLUE.getRGB();
            boolean found = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                if (image.getRGB(x, y) >>> 24 == 255) { assertEquals(expected, image.getRGB(x, y)); found = true; }
            assertTrue(found);
        }
        config.clearLegacyHighlightCycle();
        assertFalse(config.isCustomRgbCycle());
        assertEquals(AnimatedTextStyle.DUO_CYCLE, config.getTextColors().getMode());
        assertEquals(Color.GREEN, config.getTextColors().getPrimary());
    }

    @Test public void legacyPairMigratesWithoutLinkingTextTimestampOrBorder() {
        Gson gson = new Gson();
        OverlayConfig original = new OverlayConfig();
        original.setAnimatedTextStyle(AnimatedTextStyle.RGB_CYCLE);
        original.setCustomRgbCycle(true);
        original.setRgbColor1(Color.RED);
        original.setRgbColor2(Color.BLUE);
        original.setRgbCycleSeconds(14);
        original.setRgbBorder(true);
        original.setBorderColour(new Color(12, 34, 56, 90));
        OverlayConfig restored = OverlayConfigStore.read(gson, "[" + gson.toJson(original) + "]").get(0);
        assertEquals(AnimatedTextStyle.DUO_CYCLE, restored.getTextColors().getMode());
        assertEquals(AnimatedTextStyle.DUO_CYCLE, restored.getTimestampColors().getMode());
        assertEquals(AnimatedTextStyle.RGB_WAVE, restored.getBorderColors().getMode());
        assertEquals(Color.RED, restored.getTextColors().getPrimary());
        assertEquals(Color.BLUE, restored.getTimestampColors().getSecondary());
        assertEquals(90, restored.getBorderColors().getPrimary().getAlpha());
        restored.getTextColors().setPrimary(Color.GREEN);
        restored.getTextColors().setCycleSeconds(3);
        restored.getTextColors().setPulse(true);
        assertEquals(Color.RED, restored.getTimestampColors().getPrimary());
        assertEquals(14, restored.getTimestampColors().getCycleSeconds());
        assertEquals(14, restored.getBorderColors().getCycleSeconds());
        assertFalse(restored.getTimestampColors().isPulse());
        assertFalse(restored.getBorderColors().isPulse());
        OverlayConfig copy = OverlayConfigStore.read(gson, "[" + gson.toJson(restored) + "]").get(0);
        assertEquals(gson.toJson(restored), gson.toJson(copy));
        copy.getTextColors().setPrimary(Color.YELLOW);
        assertEquals(Color.GREEN, restored.getTextColors().getPrimary());
    }

    @Test public void duoCycleUsesBothEndpointsAndRgbCycleUsesTheSpectrum() {
        OverlayColorSettings settings = new OverlayColorSettings();
        settings.setPrimary(Color.RED); settings.setSecondary(Color.BLUE);
        assertEquals(new Color(255, 0, 0, 71), RgbAnimation.cycleColor(settings, AnimatedTextStyle.DUO_CYCLE, 0, 71));
        assertEquals(new Color(0, 0, 255, 71), RgbAnimation.cycleColor(settings, AnimatedTextStyle.DUO_CYCLE, 4000, 71));
        assertEquals(new Color(128, 0, 127, 71), RgbAnimation.cycleColor(settings, AnimatedTextStyle.DUO_CYCLE, 2000, 71));
        assertEquals(RgbAnimation.cycleColor(settings, AnimatedTextStyle.DUO_CYCLE, 0, 71),
                RgbAnimation.cycleColor(settings, AnimatedTextStyle.DUO_CYCLE, 8000, 71));
        assertTrue(RgbAnimation.cycleColor(settings, AnimatedTextStyle.RGB_CYCLE, 4000, 71).getGreen() > 200);
    }

    @Test public void borderModesPulseAndKeepTheirGeometryWithRoundedCorners() {
        for (boolean rounded : new boolean[]{false, true}) for (AnimatedTextStyle mode : AnimatedTextStyle.values()) {
            OverlayConfig cfg = new OverlayConfig(); cfg.setBorderEnabled(true);
            cfg.setRoundedCorners(rounded); cfg.setBorderThickness(BorderThickness.THIN);
            OverlayColorSettings colors = cfg.getBorderColors();
            colors.setMode(mode); colors.setPrimary(Color.RED); colors.setSecondary(Color.BLUE);
            BufferedImage before = border(cfg, 0), after = border(cfg, 2000);
            assertTrue(count(before, 0, 161) > 100);
            assertEquals(count(before, 0, 161), count(after, 0, 161));
            assertEquals(0, before.getRGB(80, 40));
            if (mode == AnimatedTextStyle.DUO_CYCLE || mode == AnimatedTextStyle.RGB_CYCLE) {
                assertEquals(before.getRGB(40, 0), before.getRGB(120, 0));
                assertNotEquals(before.getRGB(40, 0), after.getRGB(40, 0));
            }
            if (mode == AnimatedTextStyle.DUO_WAVE) {
                assertNotEquals(before.getRGB(40, 0), before.getRGB(120, 0));
                assertNotEquals(before.getRGB(40, 0), after.getRGB(40, 0));
                for (int x = 15; x < 145; x++) assertEquals(0, new Color(before.getRGB(x, 0), true).getGreen());
            }
            colors.setPulse(true);
            assertEquals(0, count(border(cfg, 2000), 0, 161));
            assertEquals(count(before, 0, 161), count(border(cfg, 4000), 0, 161));
        }
    }

    @Test public void timestampColorsAndPulseDoNotAffectMessagePixelsOrWidth() throws Exception {
        OverlayConfig cfg = new OverlayConfig(); cfg.setShowTimestamp(true);
        cfg.setOverlayColorOverrideEnabled(true); cfg.setOverlayTextColor(Color.RED);
        cfg.getTimestampColors().setPrimary(Color.GREEN); cfg.setTimestampColorOverride(true);
        BufferedImage both = text(cfg, 2000);
        assertTrue(countColor(both, Color.GREEN) > 0); assertTrue(countColor(both, Color.RED) > 0);
        cfg.getTimestampColors().setPulse(true);
        BufferedImage timestampHidden = text(cfg, 2000);
        assertEquals(0, countColor(timestampHidden, Color.GREEN));
        assertEquals(countColor(both, Color.RED), countColor(timestampHidden, Color.RED));
        cfg.getTimestampColors().setPulse(false); cfg.getTextColors().setPulse(true);
        BufferedImage textHidden = text(cfg, 2000);
        assertEquals(0, countColor(textHidden, Color.RED));
        assertEquals(countColor(both, Color.GREEN), countColor(textHidden, Color.GREEN));
        cfg.getTextColors().setPulse(false);
        for (AnimatedTextStyle mode : AnimatedTextStyle.values()) {
            cfg.getTextColors().setMode(mode);
            BufferedImage changedText = text(cfg, 2000);
            for (int y = 0; y < both.getHeight(); y++) for (int x = 0; x < both.getWidth(); x++) {
                if (both.getRGB(x, y) == Color.GREEN.getRGB()) assertEquals(both.getRGB(x, y), changedText.getRGB(x, y));
            }
        }
    }

    @Test public void pulseRespectsFadeAndMalformedSavedDurations() {
        OverlayColorSettings colors = new Gson().fromJson("{\"cycleSeconds\":0,\"pulseSeconds\":-99,\"mode\":null,\"primary\":null,\"pulse\":true}", OverlayColorSettings.class);
        assertEquals(2, colors.getCycleSeconds()); assertEquals(1, colors.getPulseSeconds());
        assertEquals(AnimatedTextStyle.NORMAL, colors.getMode()); assertEquals(Color.WHITE, colors.getPrimary());
        assertEquals(70, colors.pulseAlpha(70, 0)); assertEquals(35, colors.pulseAlpha(70, 250));
        assertEquals(0, colors.pulseAlpha(70, 500)); assertEquals(0, colors.pulseAlpha(0, Long.MIN_VALUE));
        colors.setPulseSeconds(100); assertEquals(60, colors.getPulseSeconds());
    }

    private static BufferedImage text(OverlayConfig cfg, long now) throws Exception {
        BufferedImage image = new BufferedImage(400, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            FontMetrics metrics = graphics.getFontMetrics();
            OverlayMessage message = OverlayMessage.gameMessage("Same message", 0, ChatMessageType.GAMEMESSAGE, false);
            List<TextSegment> segments = ChatRenderUtils.buildMessageLines(message, metrics, 400, now, 0, false,
                    cfg.getOverlayTextColor(), false, false, FontSize.REGULAR, null, true, "[HH:mm]", false, null,
                    Collections.emptyList(), null, cfg).get(0).segments;
            DynamicChatOverlay overlay = new DynamicChatOverlay(mock(ImprovedChatPlugin.class), new ImprovedChatConfig(){},
                    mock(Client.class), null, new ClanChatPresentationService(), cfg);
            Method draw = DynamicChatOverlay.class.getDeclaredMethod("drawSegmentsAt", Graphics2D.class, List.class,
                    int.class, int.class, int.class, int.class, FontSize.class, FontMetrics.class, IndexedSprite[].class, boolean.class, long.class);
            draw.setAccessible(true);
            draw.invoke(overlay, graphics, segments, 255, 0, 30, 0, FontSize.REGULAR, metrics, null, false, now);
        } finally { graphics.dispose(); }
        return image;
    }

    private static BufferedImage border(OverlayConfig cfg, long now) {
        BufferedImage image = new BufferedImage(161, 81, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { AttentionEngine.drawBorder(graphics, cfg, 161, 81, now); } finally { graphics.dispose(); }
        return image;
    }

    private static int count(BufferedImage image, int left, int right) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = left; x < right; x++) if ((image.getRGB(x, y) >>> 24) != 0) count++;
        return count;
    }

    private static int countColor(BufferedImage image, Color color) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == color.getRGB()) count++;
        return count;
    }
}
