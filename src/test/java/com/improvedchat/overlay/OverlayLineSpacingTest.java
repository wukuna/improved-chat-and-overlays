package com.improvedchat.overlay;

import com.google.gson.Gson;
import com.improvedchat.model.FontSize;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.IndexedSprite;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarClientID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static com.improvedchat.overlay.OverlayPaddingTest.textPixels;

public class OverlayLineSpacingTest {
    @Test public void existingOverlaysKeepTheirSpacingAndEachOverlaySavesItsOwnChoice() {
        Gson gson = new Gson();
        List<OverlayConfig> overlays = OverlayConfigStore.read(gson, "[{\"name\":\"Existing\"}, {\"name\":\"Other\"}]");
        assertEquals(2, overlays.get(0).getLineSpacing());
        overlays.get(0).setLineSpacing(0);
        List<OverlayConfig> restored = OverlayConfigStore.read(gson, gson.toJson(overlays));
        assertEquals(0, restored.get(0).getLineSpacing());
        assertEquals(2, restored.get(1).getLineSpacing());
        assertEquals(overlays.get(0).getId(), restored.get(0).getId());
    }

    @Test public void importedSpacingIsBounded() {
        List<OverlayConfig> overlays = OverlayConfigStore.read(new Gson(),
                "[{\"lineSpacing\":-100},{\"lineSpacing\":100}]");
        assertEquals(0, overlays.get(0).getLineSpacing());
        assertEquals(12, overlays.get(1).getLineSpacing());
    }

    @Test public void compactRowsPreserveEveryGlyphAcrossFontsAndSizes() {
        OverlayPaddingTest.Fixture f = new OverlayPaddingTest.Fixture();
        f.config.setMaxMessages(3);
        for (String font : new String[]{"RuneScape", "Arial", "Courier New"}) {
            f.config.setFontFamily(font);
            for (FontSize size : FontSize.values()) for (boolean bold : new boolean[]{false, true}) {
                f.config.setFontSize(size);
                f.config.setBoldText(bold);
                f.config.setLineSpacing(2);
                OverlayPaddingTest.Render standard = f.render("HÉgjpqy", "HÉgjpqy", "HÉgjpqy");
                f.config.setLineSpacing(0);
                OverlayPaddingTest.Render compact = f.render("HÉgjpqy", "HÉgjpqy", "HÉgjpqy");
                assertEquals(font + size + bold, textPixels(standard.image), textPixels(compact.image));
                assertEquals(standard.size.height - 4, compact.size.height);
            }
        }
    }

    @Test public void spacingChangesOnlyTheGapsBetweenRows() {
        OverlayPaddingTest.Fixture f = new OverlayPaddingTest.Fixture();
        f.config.setLineSpacing(0);
        OverlayPaddingTest.Render single = f.render("Text");
        OverlayPaddingTest.Render compact = f.render("Text", "Text", "Text");
        f.config.setLineSpacing(12);
        assertEquals(single.size, f.render("Text").size);
        OverlayPaddingTest.Render spacious = f.render("Text", "Text", "Text");
        assertEquals(compact.size.height + 24, spacious.size.height);
        assertEquals(textPixels(compact.image), textPixels(spacious.image));
    }

    @Test public void wrappedMessagesUseTheSameSpacingAsSeparateMessages() {
        OverlayPaddingTest.Fixture f = new OverlayPaddingTest.Fixture();
        for (int gap : new int[]{0, 2, 12}) {
            f.config.setLineSpacing(gap);
            OverlayPaddingTest.Render separate = f.render("First", "Second", "Third");
            OverlayPaddingTest.Render wrapped = f.render("First<br>Second<br>Third");
            assertEquals(separate.size, wrapped.size);
            assertEquals(textPixels(separate.image), textPixels(wrapped.image));
        }
    }

    @Test public void historyAndFixedHeightRetainTheirVisibleRowCount() {
        OverlayPaddingTest.Fixture f = new OverlayPaddingTest.Fixture();
        f.config.setMaxMessages(3);
        f.config.setDynamicHeight(false);
        for (boolean history : new boolean[]{false, true}) {
            f.config.setScrollHistory(history);
            f.config.setLineSpacing(2);
            OverlayPaddingTest.Render standard = f.render("First", "Second", "Third");
            f.config.setLineSpacing(0);
            OverlayPaddingTest.Render compact = f.render("First", "Second", "Third");
            assertEquals(standard.size.height - 4, compact.size.height);
            assertEquals(textPixels(standard.image), textPixels(compact.image));
        }
    }

    @Test public void compactTallBadgesAndInputPreviewRemainComplete() {
        OverlayPaddingTest.Fixture f = new OverlayPaddingTest.Fixture();
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
        f.config.setShowInputPreview(true);
        for (FontSize size : FontSize.values()) {
            f.config.setFontSize(size);
            f.config.setLineSpacing(2);
            OverlayPaddingTest.Render standard = f.render("<img=0>HÉgjpqy", "<img=0>HÉgjpqy");
            f.config.setLineSpacing(0);
            OverlayPaddingTest.Render compact = f.render("<img=0>HÉgjpqy", "<img=0>HÉgjpqy");
            assertEquals(standard.size.height - 4, compact.size.height);
            assertEquals(textPixels(standard.image), textPixels(compact.image));
        }
    }
}
