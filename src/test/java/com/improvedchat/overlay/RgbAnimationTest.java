package com.improvedchat.overlay;

import com.google.gson.Gson;
import com.improvedchat.model.AnimatedTextStyle;
import com.improvedchat.model.BorderThickness;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class RgbAnimationTest {
    @Test public void roundedBordersAnimateWithoutSquareCornersAndPersist() {
        for(BorderThickness thickness:BorderThickness.values())for(boolean rgb:new boolean[]{false,true}) {
            OverlayConfig cfg=new OverlayConfig();cfg.setBorderEnabled(true);cfg.setRoundedCorners(true);cfg.setRgbBorder(rgb);cfg.setBorderThickness(thickness);
            BufferedImage a=border(cfg,180,80,0),b=border(cfg,180,80,2000);
            assertEquals(0,a.getRGB(0,0));assertEquals(0,a.getRGB(179,79));assertEquals(0,a.getRGB(90,40));
            int inset=thickness.getPixels()/2;assertTrue((a.getRGB(90,inset)>>>24)>0);
            if(rgb)assertNotEquals(a.getRGB(90,inset),b.getRGB(90,inset));
            cfg=new Gson().fromJson(new Gson().toJson(cfg),OverlayConfig.class);assertTrue(cfg.isRoundedCorners());
        }
        assertFalse(new Gson().fromJson("{}",OverlayConfig.class).isRoundedCorners());
    }
    @Test public void fullOverlayCombinationsKeepLayoutWhenAnimationChanges() {
        net.runelite.api.Client client = (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
                net.runelite.api.Client.class.getClassLoader(), new Class[]{net.runelite.api.Client.class},
                (p, m, a) -> m.getName().equals("getGameState") ? net.runelite.api.GameState.LOGGED_IN
                        : m.getName().equals("getModIcons") ? new net.runelite.api.IndexedSprite[0]
                        : m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null);
        com.improvedchat.ImprovedChatPlugin plugin = new com.improvedchat.ImprovedChatPlugin() {
            @Override public java.util.List<com.improvedchat.model.OverlayMessage> getMessagesForOverlay(OverlayConfig config) {
                return java.util.Collections.singletonList(com.improvedchat.model.OverlayMessage.senderMessage(
                        "Alice", "Channel", "<u><shad=00ff00>Long Unicode \uD83D\uDE00 text <img=999> with wrapping and native styles</shad></u>",
                        System.currentTimeMillis(), net.runelite.api.ChatMessageType.PUBLICCHAT, false));
            }
        };
        for (int bits = 0; bits < 16; bits++) {
            final int options = bits;
            com.improvedchat.ImprovedChatConfig global = new com.improvedchat.ImprovedChatConfig() {
                @Override public boolean wrapText() { return (options & 1) != 0; }
                @Override public boolean textShadow() { return (options & 2) != 0; }
            };
            OverlayConfig cfg = OverlayConfig.preset("RGB", "all");
            cfg.setAlwaysVisible(true); cfg.setWidgetWidth(180);
            cfg.setDynamicHeight((bits & 4) != 0); cfg.setBorderEnabled((bits & 8) != 0);
            cfg.setFlashEnabled(true); cfg.setFlashBorder(true); cfg.setFlashMessage(true);
            cfg.setFlashWhileFocused(true); cfg.setAttentionTrigger(com.improvedchat.model.AttentionTrigger.ALL_NEW_MESSAGES);
            DynamicChatOverlay overlay = new DynamicChatOverlay(plugin, global, client,
                    null, new com.improvedchat.chatbox.offline.ClanChatPresentationService(), cfg);
            java.awt.Dimension baseline = null;
            for (AnimatedTextStyle style : AnimatedTextStyle.values()) {
                cfg.setAnimatedTextStyle(style); cfg.setRgbBorder(style != AnimatedTextStyle.NORMAL);
                Graphics2D g = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB).createGraphics();
                try {
                    java.awt.Dimension size = overlay.render(g);
                    assertNotNull(size);
                    if (baseline == null) baseline = size;
                    else assertEquals("Animation must not move or resize text", baseline, size);
                } finally { g.dispose(); }
            }
        }
    }
    @Test public void redTravelsClockwiseAndRepeatsAtEveryThickness() {
        for (BorderThickness thickness : BorderThickness.values()) {
            OverlayConfig cfg = new OverlayConfig();
            cfg.setBorderEnabled(true);
            cfg.setRgbBorder(true);
            cfg.setBorderThickness(thickness);
            cfg.setRgbCycleSeconds(8);
            int inset = thickness.getPixels() / 2;
            int side = 81 + inset * 2;
            int[][] points = {{inset, inset}, {inset + 80, inset},
                    {inset + 80, inset + 80}, {inset, inset + 80}};
            for (int quarter = 0; quarter < 4; quarter++) {
                BufferedImage image = border(cfg, side, side, quarter * 2000L);
                Color c = new Color(image.getRGB(points[quarter][0], points[quarter][1]), true);
                assertTrue("Clockwise red at quarter " + quarter, c.getRed() > 240 && c.getGreen() < 20 && c.getBlue() < 20);
                assertEquals(image.getRGB(points[quarter][0], points[quarter][1]),
                        border(cfg, side, side, quarter * 2000L + 8000).getRGB(points[quarter][0], points[quarter][1]));
            }
        }
    }

    @Test public void rectangularCornersAreContinuousAndInteriorIsEmpty() {
        OverlayConfig cfg = new OverlayConfig();
        cfg.setBorderEnabled(true); cfg.setRgbBorder(true);
        cfg.setBorderThickness(BorderThickness.THIN);
        BufferedImage image = border(cfg, 161, 41, 1300);
        assertEquals(0, image.getRGB(80, 20));
        Color a = new Color(image.getRGB(159, 0), true);
        Color b = new Color(image.getRGB(160, 1), true);
        assertTrue(Math.abs(a.getRed() - b.getRed()) < 20);
        assertTrue(Math.abs(a.getGreen() - b.getGreen()) < 20);
        assertTrue(Math.abs(a.getBlue() - b.getBlue()) < 20);
    }

    @Test public void disabledAndTransparentBordersStayInvisibleAndTinySizesAreSafe() {
        OverlayConfig cfg = new OverlayConfig(); cfg.setRgbBorder(true);
        assertEquals(0, border(cfg, 30, 30, 0).getRGB(0, 0));
        cfg.setBorderEnabled(true); cfg.setBorderColour(new Color(255, 255, 255, 0));
        assertEquals(0, border(cfg, 30, 30, 0).getRGB(0, 0));
        for (int size = 1; size < 8; size++) border(cfg, size, size, Long.MIN_VALUE);
    }

    @Test public void textAnimationKeepsGlyphGeometryAndFadeAlpha() {
        for (AnimatedTextStyle style : new AnimatedTextStyle[]{AnimatedTextStyle.RGB_CYCLE, AnimatedTextStyle.RGB_WAVE,
                AnimatedTextStyle.DUO_CYCLE, AnimatedTextStyle.DUO_WAVE}) {
            OverlayConfig cfg = new OverlayConfig(); cfg.setAnimatedTextStyle(style);
            BufferedImage first = text(cfg, 0, 255), later = text(cfg, 2000, 255), faded = text(cfg, 0, 70);
            boolean colorChanged = false;
            for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
                int a = first.getRGB(x, y), b = later.getRGB(x, y), f = faded.getRGB(x, y);
                assertEquals(a >>> 24, b >>> 24);
                assertTrue((f >>> 24) <= 70);
                colorChanged |= a != b;
            }
            assertTrue(colorChanged);
        }
    }

    @Test public void drawingRestoresPaintAndStroke() {
        Graphics2D g = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            Paint paint = new GradientPaint(0, 0, Color.WHITE, 10, 10, Color.BLACK);
            g.setPaint(paint); Stroke stroke = g.getStroke();
            OverlayConfig cfg = new OverlayConfig(); cfg.setBorderEnabled(true); cfg.setRgbBorder(true);
            AttentionEngine.drawBorder(g, cfg, 200, 80, 0);
            assertSame(paint, g.getPaint()); assertSame(stroke, g.getStroke());
            cfg.setAnimatedTextStyle(AnimatedTextStyle.RGB_WAVE);
            RgbAnimation.drawText(g, "Hello", 255, 0, 20, true, g.getFontMetrics(), cfg, 0, 0);
            assertSame(paint, g.getPaint());
        } finally { g.dispose(); }
    }

    @Test public void savedSettingsRoundTripAndOldSettingsKeepEffectsOff() {
        Gson gson = new Gson();
        OverlayConfig old = gson.fromJson("{\"name\":\"Existing\"}", OverlayConfig.class);
        assertFalse(old.isRgbBorder()); assertEquals(AnimatedTextStyle.NORMAL, old.getAnimatedTextStyle());
        assertEquals(8, old.getRgbCycleSeconds());
        old.setRgbBorder(true); old.setAnimatedTextStyle(AnimatedTextStyle.RGB_WAVE); old.setRgbCycleSeconds(13);
        OverlayConfig restored = gson.fromJson(gson.toJson(old), OverlayConfig.class);
        assertTrue(restored.isRgbBorder()); assertEquals(AnimatedTextStyle.RGB_WAVE, restored.getAnimatedTextStyle());
        assertEquals(13, restored.getRgbCycleSeconds());
        restored.setAnimatedTextStyle(null); restored.setRgbCycleSeconds(0);
        assertEquals(AnimatedTextStyle.NORMAL, restored.getAnimatedTextStyle()); assertEquals(2, restored.getRgbCycleSeconds());
        restored.setRgbCycleSeconds(100); assertEquals(30, restored.getRgbCycleSeconds());
    }

    private BufferedImage border(OverlayConfig cfg, int width, int height, long now) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { AttentionEngine.drawBorder(g, cfg, width, height, now); } finally { g.dispose(); }
        return image;
    }

    private BufferedImage text(OverlayConfig cfg, long now, int alpha) {
        BufferedImage image = new BufferedImage(300, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            String text = "AV Unicode \uD83D\uDE00 text";
            assertEquals(g.getFontMetrics().stringWidth(text),
                    RgbAnimation.drawText(g, text, alpha, 10, 30, false, g.getFontMetrics(), cfg, now, 0));
        } finally { g.dispose(); }
        return image;
    }
}
