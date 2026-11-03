package com.improvedchat.overlay;

import com.improvedchat.ChatColorSource;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.OverlayMessage;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import com.google.gson.Gson;
import net.runelite.client.config.ChatColorConfig;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayPresentationTest {
    @Test public void allMessageStaticRgbOverridesSavedSolidRuleOnlyWhenRequested() {
        OverlayConfig overlay = new OverlayConfig();
        overlay.setAnimatedTextStyle(com.improvedchat.model.AnimatedTextStyle.RGB_STATIC);
        overlay.getTextSettings().setSolidEnabled(true);
        overlay.getTextSettings().setIncludePlayer(true);
        overlay.getTextSettings().setSolidRules("Hello::1");
        overlay.getTextSettings().setPalette(0, Color.MAGENTA);
        overlay.getTextSettings().setHighlightsOverrideDefaults(true);
        assertEquals(Color.MAGENTA, colorOf(lines(overlay, null, false), "Hello"));
        overlay.getTextSettings().setHighlightsOverrideDefaults(false);
        List<RenderLine> rainbow = lines(overlay, null, false);
        assertNotEquals(Color.MAGENTA, colorOf(rainbow, "Hello"));
        assertFalse(rainbow.stream().flatMap(l -> l.segments.stream()).anyMatch(s -> "Hello".equals(s.text) && Color.MAGENTA.equals(s.color)));
        assertEquals("Hello::1", overlay.getTextSettings().messageColorRules());
    }
    @Test public void smallFontsAreSmallerForEveryOfferedFamily() {
        Graphics2D g = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            for (String family : new String[]{"Arial", "Verdana", "Tahoma", "Segoe UI", "Georgia", "Courier New"}) {
                OverlayConfig overlay = new OverlayConfig(); overlay.setFontFamily(family);
                for (boolean bold : new boolean[]{false, true}) {
                    overlay.setBoldText(bold);
                    FontStyler.setupGraphics(g, FontSize.REGULAR, overlay);
                    Font regular = g.getFont();
                    int regularWidth = g.getFontMetrics().stringWidth("Clan channel: Sample message");
                    FontStyler.setupGraphics(g, FontSize.SMALL, overlay);
                    assertTrue(family, g.getFont().getSize2D() < regular.getSize2D());
                    assertTrue(family, g.getFontMetrics().stringWidth("Clan channel: Sample message") < regularWidth);
                    assertEquals(bold, g.getFont().isBold());
                }
            }
        } finally { g.dispose(); }
    }

    @Test public void clanHeadersUseSelectedRuneLitePalette() {
        OverlayConfig overlay = new OverlayConfig(); overlay.getTextSettings().setSource(ChatColorSource.OPAQUE);
        ChatColorConfig colors = mock(ChatColorConfig.class);
        when(colors.opaqueClanChatMessage()).thenReturn(Color.GREEN);
        when(colors.opaqueClanChatUsernames()).thenReturn(Color.ORANGE);
        when(colors.opaqueClanChannelName()).thenReturn(Color.CYAN);
        List<RenderLine> lines = lines(overlay, colors, true);
        assertEquals(Color.GREEN, colorOf(lines, "Hello"));
        assertEquals(Color.ORANGE, colorOf(lines, "Alice"));
        assertEquals(Color.CYAN, colorOf(lines, "My clan"));
    }

    @Test public void automaticPaletteTracksActualChatboxModeAndExplicitChoicesStayFixed() {
        Client client = mock(Client.class);
        assertEquals(ChatColorSource.OPAQUE, OverlayChatColors.resolveSource(ChatColorSource.AUTOMATIC, client));
        when(client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY)).thenReturn(1);
        assertEquals(ChatColorSource.OPAQUE, OverlayChatColors.resolveSource(ChatColorSource.AUTOMATIC, client));
        when(client.isResized()).thenReturn(true);
        assertEquals(ChatColorSource.TRANSPARENT, OverlayChatColors.resolveSource(ChatColorSource.AUTOMATIC, client));
        when(client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY)).thenReturn(0);
        assertEquals(ChatColorSource.OPAQUE, OverlayChatColors.resolveSource(ChatColorSource.AUTOMATIC, client));
        assertEquals(ChatColorSource.TRANSPARENT, OverlayChatColors.resolveSource(ChatColorSource.TRANSPARENT, client));
        assertEquals(ChatColorSource.WIDGET, OverlayChatColors.resolveSource(ChatColorSource.WIDGET, client));
    }

    @Test public void hidingChannelKeepsSenderBodyAndOldPreferences() {
        OverlayConfig overlay = new Gson().fromJson("{\"name\":\"Existing\"}", OverlayConfig.class);
        assertTrue(overlay.isShowChannelNames(true));
        assertFalse(overlay.isShowChannelNames(false));
        overlay.setShowChannelNames(false);
        OverlayConfig restored = new Gson().fromJson(new Gson().toJson(overlay), OverlayConfig.class);
        List<RenderLine> lines = lines(restored, null, true);
        assertEquals(Color.WHITE, colorOf(lines, "Alice"));
        assertEquals(Color.WHITE, colorOf(lines, "Hello"));
        assertFalse(lines.stream().flatMap(l -> l.segments.stream()).anyMatch(s -> s.text.contains("My clan")));
        restored.setShowChannelNames(true);
        assertNotNull(colorOf(lines(restored, null, false), "My clan"));
        assertTrue(new OverlayConfig().isShowChannelNames(true));
    }

    @Test public void transparentClanGuestFriendsAndPrivateNamesUseTheirOwnSettings() {
        ChatColorConfig colors = mock(ChatColorConfig.class);
        when(colors.transparentClanChatGuestUsernames()).thenReturn(Color.RED);
        when(colors.transparentFriendsChatUsernames()).thenReturn(Color.GREEN);
        when(colors.transparentPrivateUsernames()).thenReturn(Color.BLUE);
        when(colors.transparentClanChannelGuestName()).thenReturn(Color.YELLOW);
        when(colors.transparentFriendsChatChannelName()).thenReturn(Color.PINK);
        assertEquals(Color.RED, OverlayChatColors.sender(ChatMessageType.CLAN_GUEST_CHAT, colors, ChatColorSource.TRANSPARENT));
        assertEquals(Color.GREEN, OverlayChatColors.sender(ChatMessageType.FRIENDSCHAT, colors, ChatColorSource.TRANSPARENT));
        assertEquals(Color.BLUE, OverlayChatColors.sender(ChatMessageType.PRIVATECHATOUT, colors, ChatColorSource.TRANSPARENT));
        assertEquals(Color.YELLOW, OverlayChatColors.channel(ChatMessageType.CLAN_GUEST_CHAT, colors, ChatColorSource.TRANSPARENT));
        assertEquals(Color.PINK, OverlayChatColors.channel(ChatMessageType.FRIENDSCHAT, colors, ChatColorSource.TRANSPARENT));
        assertEquals(Color.WHITE, OverlayChatColors.sender(ChatMessageType.CLAN_GUEST_CHAT, colors, ChatColorSource.WIDGET));
    }

    @Test public void paletteChangesRefreshExistingMessagesAndKeepExplicitOverrides() {
        OverlayConfig overlay = new OverlayConfig();
        Client client = mock(Client.class);
        ChatColorConfig colors = mock(ChatColorConfig.class);
        when(colors.opaqueClanChatMessage()).thenReturn(Color.GREEN);
        when(colors.opaqueClanChatMessageHighlight()).thenReturn(Color.YELLOW);
        when(colors.transparentClanChatMessage()).thenReturn(Color.BLUE);
        when(colors.transparentClanChatMessageHighlight()).thenReturn(Color.CYAN);
        Graphics2D g = new BufferedImage(600, 100, BufferedImage.TYPE_INT_ARGB).createGraphics();
        OverlayMessage message = OverlayMessage.senderMessage("Alice", "My clan", "<colNORMAL>Hello<colHIGHLIGHT>World</col>",
                0, ChatMessageType.CLAN_CHAT, false);
        try {
            for (boolean transparent : new boolean[]{false, true}) {
                when(client.isResized()).thenReturn(transparent);
                when(client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY)).thenReturn(1);
                ChatColorSource source = OverlayChatColors.resolveSource(overlay.getTextSettings().chatColorSource(), client);
                List<RenderLine> lines = ChatRenderUtils.buildMessageLines(message, g.getFontMetrics(), 500, 0, 0,
                        false, Color.MAGENTA, true, false, FontSize.REGULAR, null, false, "", true,
                        colors, Collections.emptyList(), null, overlay, source);
                assertEquals(transparent ? Color.BLUE : Color.GREEN, colorOf(lines, "Hello"));
                assertEquals(transparent ? Color.CYAN : Color.YELLOW, colorOf(lines, "World"));
            }
            overlay.setOverlayColorOverrideEnabled(true);
            List<RenderLine> lines = ChatRenderUtils.buildMessageLines(message, g.getFontMetrics(), 500, 0, 0,
                    false, Color.MAGENTA, overlay.isContextualColours(), false, FontSize.REGULAR, null, false, "", true,
                    colors, Collections.emptyList(), Color.RED, overlay, ChatColorSource.OPAQUE);
            assertEquals(Color.MAGENTA, colorOf(lines, "Hello"));
            assertEquals(Color.MAGENTA, colorOf(lines, "World"));
            assertEquals(Color.RED, colorOf(lines, "Alice"));
        } finally { g.dispose(); }
    }

    static List<RenderLine> lines(OverlayConfig overlay, ChatColorConfig colors, boolean channel) {
        Graphics2D g = new BufferedImage(600, 100, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return ChatRenderUtils.buildMessageLines(OverlayMessage.senderMessage("Alice", "My clan", "Hello", 0,
                    ChatMessageType.CLAN_CHAT, false), g.getFontMetrics(), 500, 0, 0, false,
                    Color.WHITE, overlay.isContextualColours(), false, FontSize.REGULAR, null, false, "", channel,
                    colors, Collections.emptyList(), null, overlay);
        } finally { g.dispose(); }
    }

    static Color colorOf(List<RenderLine> lines, String text) {
        return lines.stream().flatMap(line -> line.segments.stream()).filter(s -> s.text.equals(text))
                .map(s -> s.color).findFirst().orElseThrow(() -> new AssertionError("Missing " + text));
    }
}
