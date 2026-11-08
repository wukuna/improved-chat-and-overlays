package com.improvedchat.input;

import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.overlay.DynamicChatOverlay;
import com.improvedchat.overlay.OverlayConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.ChatMessageType;
import net.runelite.api.gameval.VarClientID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InputDockRenderTest {
    private DynamicChatOverlay overlay(ChatInputDockTest.Fixture f, boolean empty) {
        OverlayConfig config = OverlayConfig.preset("Chat", "all");
        f.settings.target = config.getId();
        config.setAlwaysVisible(true); config.setShow(true); config.setShowInputPreview(false);
        config.setShowTimestamp(false); config.setDynamicHeight(true); config.setWidgetWidth(365);
        config.setBorderEnabled(true); config.setBorderColour(new Color(168, 148, 110));
        config.setBackgroundColour(new Color(27, 30, 34, 245)); config.setRoundedCorners(true);
        config.setFadeOutDuration(0);
        ImprovedChatPlugin plugin = new ImprovedChatPlugin() {
            @Override public ChatInputDock getInputDock() { return f.dock; }
            @Override public List<OverlayMessage> getMessagesForOverlay(OverlayConfig cfg) {
                return empty ? Collections.emptyList() : Arrays.asList(
                        OverlayMessage.senderMessage("Morgan", null, "Anyone up for a raid?", System.currentTimeMillis(), ChatMessageType.PUBLICCHAT, false),
                        OverlayMessage.senderMessage("Rich", null, "I'll be ready in a minute.", System.currentTimeMillis(), ChatMessageType.PUBLICCHAT, false));
            }
        };
        return new DynamicChatOverlay(plugin, f.settings, f.client, null, new ClanChatPresentationService(), config);
    }

    @Test public void actualFramesIncludeDockAndRemainStableAfterResizing() {
        for (InputDockPlacement placement : new InputDockPlacement[]{InputDockPlacement.LEFT, InputDockPlacement.RIGHT, InputDockPlacement.BOTTOM}) {
            ChatInputDockTest.Fixture f = new ChatInputDockTest.Fixture(); f.settings.placement = placement;
            DynamicChatOverlay overlay = overlay(f, true);
            Graphics2D g = new BufferedImage(1200, 500, BufferedImage.TYPE_INT_ARGB).createGraphics();
            g.setClip(0, 0, 1200, 500);
            Dimension first = overlay.render(g); assertNotNull(first);
            overlay.getBounds().setSize(first);
            overlay.setPreferredSize(first);
            Dimension second = overlay.render(g); assertEquals(first.width, second.width);
            assertEquals(second, overlay.render(g));
            overlay.getOverlayConfig().setShow(false); assertNull(overlay.render(g));
            g.dispose();
        }
    }

    @Test public void renderProductionDockAndAllAttachments() throws Exception {
        BufferedImage image = new BufferedImage(1120, 680, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(17, 20, 24)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(229, 229, 225)); g.setFont(new Font("Arial", Font.BOLD, 22));
        g.drawString("Chat input dock", 28, 36);
        g.setFont(new Font("Arial", Font.PLAIN, 14)); g.setColor(new Color(159, 169, 180));
        g.drawString("Production renderer · selectable buttons · 60–200% size", 28, 62);
        for (int i = 0; i < 4; i++) {
            ChatInputDockTest.Fixture f = new ChatInputDockTest.Fixture();
            f.settings.placement = InputDockPlacement.values()[i];
            when(f.client.getVarcIntValue(VarClientID.CHATBOX_MODE)).thenReturn(1);
            int x = i % 2 == 0 ? 28 : 570;
            int y = i < 2 ? 100 : 365;
            g.setColor(new Color(206, 187, 145)); g.drawString(f.settings.placement.toString(), x, y);
            Graphics2D target = (Graphics2D) g.create(); target.translate(x, y + 18); target.setClip(new Rectangle(0, 0, 520, 220));
            if (i == 0) {
                InputDockLayout layout = f.dock.layout(target, false); f.dock.paint(target, layout, true);
                target.translate(0, 75); f.settings.clan = false; f.settings.gim = false;
                f.settings.scale = 140; layout = f.dock.layout(target, false); f.dock.paint(target, layout, true);
            } else overlay(f, false).render(target);
            target.dispose();
        }
        g.dispose(); ImageIO.write(image, "png", new File("build/chat-input-dock-preview.png"));
    }
}
