package com.improvedchat.input;

import com.improvedchat.ImprovedChatConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class ChatInputDockOverlay extends Overlay {
    private final ChatInputDock dock;
    private final ImprovedChatConfig config;
    private final ConfigManager configManager;
    private volatile Dimension paintedSize = new Dimension();
    private volatile int paintedScale = 100;

    @Inject
    public ChatInputDockOverlay(ChatInputDock dock, ImprovedChatConfig config, ConfigManager configManager) {
        this.dock = dock; this.config = config; this.configManager = configManager;
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setResizable(true);
        setMinimumSize(20);
    }

    @Override public String getName() { return "Improved Chat Input Dock"; }

    @Override public Dimension render(Graphics2D graphics) {
        if (config.inputDockPlacement() != InputDockPlacement.FLOATING || !dock.canSwitch()) return null;
        InputDockLayout layout = dock.layout(graphics, false);
        if (layout == null) return null;
        dock.paint(graphics, layout, true);
        paintedSize = new Dimension(layout.size);
        paintedScale = config.inputDockScale();
        return layout.size;
    }

    @Override public void setPreferredSize(Dimension size) {
        Dimension previous = paintedSize;
        if (configManager == null || size == null || previous == null || previous.width <= 0 || previous.height <= 0) {
            super.setPreferredSize(size);
            return;
        }
        double width = size.width / (double) previous.width;
        double height = size.height / (double) previous.height;
        double ratio = Math.abs(width - 1) > Math.abs(height - 1) ? width : height;
        int scale = Math.max(60, Math.min(200, (int) Math.round(paintedScale * ratio)));
        if (scale != config.inputDockScale()) configManager.setConfiguration(ImprovedChatConfig.GROUP, "inputDockScale", scale);
        super.setPreferredSize(null);
    }
}
