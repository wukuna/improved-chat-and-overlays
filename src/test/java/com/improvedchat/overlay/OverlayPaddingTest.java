package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.model.BorderThickness;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.model.TextAlignment;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarClientID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayPaddingTest {
    @Test public void allFontsKeepCompleteGlyphsAndSpaceInsideEveryBorder() {
        Fixture f = new Fixture();
        for (String font : new String[]{"RuneScape", "Arial", "Courier New"}) {
            f.config.setFontFamily(font);
            for (FontSize size : FontSize.values()) for (TextAlignment alignment : TextAlignment.values())
                    for (boolean bold : new boolean[]{false, true}) {
                f.config.setFontSize(size);
                f.config.setTextAlignment(alignment);
                f.config.setBoldText(bold);
                f.config.setPaddingHorizontal(12);
                f.config.setPaddingVertical(12);
                Render reference = f.render("HÉgjpqy");
                int completeTextPixels = textPixels(reference.image);
                for (BorderThickness border : BorderThickness.values()) for (boolean rounded : new boolean[]{false, true}) {
                    f.config.setBorderThickness(border);
                    f.config.setRoundedCorners(rounded);
                    f.config.setPaddingHorizontal(0);
                    f.config.setPaddingVertical(0);
                    Render actual = f.render("HÉgjpqy");
                    String context = font + " " + size + " " + alignment + " " + border
                            + " rounded=" + rounded + " bold=" + bold;
                    assertEquals(context + " must not clip glyphs", completeTextPixels, textPixels(actual.image));
                    Rectangle ink = textBounds(actual.image);
                    int clearance = border.getPixels() + 3;
                    assertTrue(context + " left gap", ink.x >= clearance);
                    assertTrue(context + " top gap", ink.y >= clearance);
                    assertTrue(context + " right gap", ink.x + ink.width <= actual.size.width - clearance);
                    assertTrue(context + " bottom gap", ink.y + ink.height <= actual.size.height - clearance);
                }
            }
        }
    }

    @Test public void additionalRowsDoNotClipTheFirstRowOrPaintAboveThePadding() {
        Fixture f = new Fixture();
        f.config.setPaddingHorizontal(3);
        f.config.setPaddingVertical(3);
        for (FontSize size : FontSize.values()) {
            f.config.setMaxMessages(20);
            f.config.setFontSize(size);
            Render first = f.render("HÉgjpqy"), three = f.render("HÉgjpqy", "HÉgjpqy", "HÉgjpqy");
            int single = textPixels(first.image);
            assertEquals(size + " Each row must retain the entire glyph", single * 3, textPixels(three.image));
            f.config.setDynamicHeight(false);
            f.config.setMaxMessages(1);

            assertEquals(single, textPixels(f.render("HÉgjpqy<br>HÉgjpqy<br>HÉgjpqy").image));
            f.config.setDynamicHeight(true);
        }
    }

    @Test public void tallIconsInputAndHistoryKeepTheSameCompleteContentAtMinimumPadding() {
        Fixture f = new Fixture();
        IndexedSprite sprite = mock(IndexedSprite.class);
        when(sprite.getWidth()).thenReturn(12);
        when(sprite.getHeight()).thenReturn(24);
        byte[] pixels = new byte[12 * 24];
        Arrays.fill(pixels, (byte) 1);
        when(sprite.getPixels()).thenReturn(pixels);
        when(sprite.getPalette()).thenReturn(new int[]{0, 0xffffff});
        when(f.client.getModIcons()).thenReturn(new IndexedSprite[]{sprite});
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(f.client.getLocalPlayer()).thenReturn(player);
        when(f.client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn("HÉgjpqy");
        f.config.setBorderThickness(BorderThickness.EXTRA_THICK);
        f.config.setRoundedCorners(true);
        f.config.setMaxMessages(2);
        for (FontSize size : FontSize.values()) for (boolean history : new boolean[]{false, true}) {
            f.config.setFontSize(size);
            f.config.setScrollHistory(history);
            f.config.setShowInputPreview(true);
            f.config.setPaddingHorizontal(12);
            f.config.setPaddingVertical(12);
            int complete = textPixels(f.render("<img=0>HÉgjpqy", "HÉgjpqy").image);
            f.config.setPaddingHorizontal(0);
            f.config.setPaddingVertical(0);
            Render actual = f.render("<img=0>HÉgjpqy", "HÉgjpqy");
            assertEquals(size + " history=" + history, complete, textPixels(actual.image));
            assertTrue(textBounds(actual.image).y >= 7);
        }
    }

    @Test public void renderPreview() throws Exception {
        Fixture f = new Fixture();
        f.config.setBorderColour(new Color(90, 180, 230));
        f.config.setRoundedCorners(true);
        BufferedImage preview = new BufferedImage(620, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setColor(new Color(28, 31, 35));
        g.fillRect(0, 0, 620, 300);
        for (int i = 0; i < 4; i++) {
            f.config.setBorderThickness(BorderThickness.values()[i]);
            f.config.setFontSize(i < 2 ? FontSize.REGULAR : FontSize.LARGE);
            int x = 15 + (i % 2) * 305, y = 25 + (i / 2) * 140;
            g.setColor(Color.WHITE);
            g.drawString(BorderThickness.values()[i] + " border / " + f.config.getFontSize(), x, y);
            g.drawImage(f.render("A little space on every side.", "Top and bottom stay readable.").image,
                    x, y + 12, null);
        }
        g.dispose();
        javax.imageio.ImageIO.write(preview, "png", new java.io.File("build/overlay-padding.png"));
    }

    static final class Fixture {
        final OverlayConfig config = new OverlayConfig();
        final Client client = mock(Client.class);
        final ImprovedChatPlugin plugin = mock(ImprovedChatPlugin.class);
        final DynamicChatOverlay overlay;

        Fixture() {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            config.setAlwaysVisible(true);
            config.setDynamicHeight(true);
            config.setWidgetWidth(280);
            config.setShowTimestamp(false);
            config.setShowInputPreview(false);
            config.setFadeOutDuration(0);
            config.setBorderEnabled(true);
            config.setBorderColour(Color.RED);
            config.setBackgroundEnabled(false);
            config.setOverlayColorOverrideEnabled(true);
            config.setOverlayTextColor(Color.WHITE);
            overlay = new DynamicChatOverlay(plugin, new ImprovedChatConfig(){}, client, null,
                    new ClanChatPresentationService(), config);
        }

        Render render(String... text) {
            List<OverlayMessage> messages = new java.util.ArrayList<>();
            for (String line : text) messages.add(OverlayMessage.gameMessage(line,
                    System.currentTimeMillis(), ChatMessageType.GAMEMESSAGE, false));
            when(plugin.getMessagesForOverlay(config)).thenReturn(messages);
            when(plugin.getHistoryForOverlay(config)).thenReturn(messages);
            BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            Rectangle clip = new Rectangle(0, 0, image.getWidth(), image.getHeight());
            g.setClip(clip);
            try {
                Dimension size = overlay.render(g);
                assertEquals("Renderer must restore the caller's clip", clip, g.getClip().getBounds());
                return new Render(image, size);
            } finally { g.dispose(); }
        }
    }

    static final class Render {
        final BufferedImage image;
        final Dimension size;
        Render(BufferedImage image, Dimension size) { this.image = image; this.size = size; }
    }

    private static boolean isText(int pixel) {
        return (pixel >>> 24) > 0 && (pixel & 0xffffff) == 0xffffff;
    }

    static int textPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (isText(image.getRGB(x, y))) count++;
        return count;
    }

    static Rectangle textBounds(BufferedImage image) {
        Rectangle bounds = null;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (isText(image.getRGB(x, y))) {
                Rectangle pixel = new Rectangle(x, y, 1, 1);
                bounds = bounds == null ? pixel : bounds.union(pixel);
            }
        }
        assertNotNull("Text must be visible", bounds);
        return bounds;
    }
}
