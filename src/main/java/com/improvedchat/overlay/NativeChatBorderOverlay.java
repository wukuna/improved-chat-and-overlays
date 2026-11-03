package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.resize.internal.Widgets;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public final class NativeChatBorderOverlay extends Overlay {
    private final Client client;
    private final ImprovedChatConfig config;
    private final OverlayConfig style = new OverlayConfig();

    @Inject
    public NativeChatBorderOverlay(Client client, ImprovedChatConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.CHATBOX);
        style.setBorderEnabled(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.chatboxBorder() || client.getGameState() != GameState.LOGGED_IN
                || client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337) return null;
        Widget area = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        Widget messages = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (area == null || area.isHidden() || messages == null || messages.isHidden()) return null;
        Rectangle bounds = Widgets.liveBounds(area);
        if (bounds.width <= 1 || bounds.height <= 1) return null;

        style.setBorderColour(config.chatboxBorderColor());
        style.setBorderThickness(config.chatboxBorderThickness());
        style.setRgbBorder(config.chatboxBorderRgb());
        style.setRgbCycleSeconds(config.chatboxBorderRgbSeconds());
        style.setRoundedCorners(config.chatboxBorderRounded());
        Graphics2D frame = (Graphics2D) graphics.create();
        try {
            frame.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
            frame.translate(bounds.x, bounds.y);
            AttentionEngine.drawBorder(frame, style, bounds.width, bounds.height, System.currentTimeMillis());
        } finally {
            frame.dispose();
        }
        return null;
    }
}
