package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.resize.internal.Widgets;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.*;

/** Repaints only the native background layer, before the native messages and input. */
@Singleton
public class NativeChatBackgroundOverlay extends Overlay {
    private final Client client;
    private final ImprovedChatConfig config;
    private final SpriteManager sprites;
    private final ClientThread clientThread;
    private final EventBus events;
    private final List<Widget> hiddenForFrame = new ArrayList<>();
    private List<Part> cachedParts;
    private Rectangle cachedClip;
    private int cachedInset;
    private BufferedImage cachedImage;
    private Rectangle paintBounds;
    private boolean prepared;
    private volatile boolean running;

    @Inject
    public NativeChatBackgroundOverlay(Client client, ImprovedChatConfig config, SpriteManager sprites,
            ClientThread clientThread, EventBus events) {
        this.client = client; this.config = config; this.sprites = sprites;
        this.clientThread = clientThread; this.events = events;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.MANUAL);
        drawAfterLayer(InterfaceID.Chatbox.CHAT_BACKGROUND);
    }

    public void startUp() { running = true; events.register(this); }
    public void shutDown() {
        running = false; events.unregister(this);
        clientThread.invoke(() -> { if (!running) { restore(); cachedImage = null; cachedParts = null; } });
    }

    // Run after the resize/opacity modules have settled the native background for this frame.
    @Subscribe(priority = -100)
    public void onBeforeRender(BeforeRender event) {
        restore();
        if (!running || !config.chatboxBorder() || !config.chatboxBorderRounded()
                || client.getGameState() != GameState.LOGGED_IN
                || client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337) return;
        Widget area = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        Widget messages = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (area == null || area.isHidden() || messages == null || messages.isHidden()
                || background == null || background.isHidden()) return;
        Rectangle bounds = Widgets.liveBounds(area);
        if (bounds.width < 2 || bounds.height < 2 || bounds.width > 4096 || bounds.height > 4096) return;
        Rectangle clip = Widgets.liveBounds(background).intersection(bounds);
        if (clip.isEmpty()) return;
        clip.translate(-bounds.x, -bounds.y);
        List<Widget> children = new ArrayList<>();
        if (background.getDynamicChildren() != null) children.addAll(Arrays.asList(background.getDynamicChildren()));
        if (background.getStaticChildren() != null) children.addAll(Arrays.asList(background.getStaticChildren()));
        List<Part> parts = new ArrayList<>();
        List<Widget> visible = new ArrayList<>();
        for (Widget child : children) {
            if (child == null || child.isHidden()) continue;
            Rectangle childBounds = Widgets.liveBounds(child);
            childBounds.translate(-bounds.x, -bounds.y);
            if (!childBounds.intersects(clip)) continue;
            BufferedImage sprite = null;
            if (child.getType() == WidgetType.GRAPHIC) {
                // Preserve overrides supplied by RuneLite or another plugin.
                SpritePixels override = client.getWidgetSpriteOverrides().get(child.getId());
                if (override == null) override = client.getSpriteOverrides().get(child.getSpriteId());
                sprite = override == null ? sprites.getSprite(child.getSpriteId(), 0) : override.toBufferedImage();
                if (sprite == null || child.getBorderType() != 0
                        || child.getSpriteTiling() && (child.isFlippedHorizontally() || child.isFlippedVertically())) return;
            } else if (child.getType() != WidgetType.RECTANGLE || !child.isFilled()) {
                return; // Leave unfamiliar native/dialogue artwork entirely under game ownership.
            }
            parts.add(new Part(childBounds, child.getTextColor(), child.getOpacity(), sprite,
                    child.getSpriteTiling(), child.isFlippedHorizontally(), child.isFlippedVertically()));
            visible.add(child);
        }
        if (parts.isEmpty()) return;
        int inset = config.chatboxBorderThickness().getPixels() / 2;
        if (cachedImage == null || cachedImage.getWidth() != bounds.width || cachedImage.getHeight() != bounds.height
                || !parts.equals(cachedParts) || !clip.equals(cachedClip) || inset != cachedInset) {
            cachedImage = paint(parts, clip, bounds.width, bounds.height, inset);
            cachedParts = parts; cachedClip = clip; cachedInset = inset;
        }
        paintBounds = bounds;
        // Suppression lasts only until this layer has drawn. No widget dimensions, hitboxes or opacity change.
        hiddenForFrame.addAll(visible);
        for (Widget child : visible) child.setHidden(true);
        prepared = true;
    }

    @Override public Dimension render(Graphics2D graphics) {
        try {
            if (prepared && running) graphics.drawImage(cachedImage, paintBounds.x, paintBounds.y, null);
        } finally { restore(); }
        return null;
    }

    // If the layer was skipped, restore before any game script can set a new native hidden state.
    @Subscribe public void onScriptPreFired(ScriptPreFired event) { restore(); }
    @Subscribe public void onGameStateChanged(GameStateChanged event) { restore(); }

    private void restore() {
        for (Widget child : hiddenForFrame) child.setHidden(false);
        hiddenForFrame.clear(); prepared = false;
    }

    private static BufferedImage paint(List<Part> parts, Rectangle clip, int width, int height, int inset) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setClip(clip);
            for (Part part : parts) {
                Rectangle b = part.bounds;
                g.setComposite(AlphaComposite.SrcOver.derive((256 - Math.max(0, Math.min(255, part.opacity))) / 256f));
                if (part.sprite == null) { g.setColor(new Color(part.color)); g.fillRect(b.x, b.y, b.width, b.height); }
                else if (part.tiled && !part.flipX && !part.flipY) {
                    g.setPaint(new TexturePaint(part.sprite, new Rectangle(b.x, b.y, part.sprite.getWidth(), part.sprite.getHeight())));
                    g.fillRect(b.x, b.y, b.width, b.height);
                } else {
                    g.drawImage(part.sprite, b.x, b.y, b.x + b.width, b.y + b.height,
                            part.flipX ? part.sprite.getWidth() : 0, part.flipY ? part.sprite.getHeight() : 0,
                            part.flipX ? 0 : part.sprite.getWidth(), part.flipY ? 0 : part.sprite.getHeight(), null);
                }
            }
            g.setClip(null);
            BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D shape = mask.createGraphics();
            try {
                shape.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                shape.setColor(Color.WHITE);
                shape.fill(new RoundRectangle2D.Double(inset, inset, Math.max(0, width - 1 - inset * 2),
                        Math.max(0, height - 1 - inset * 2), 16, 16));
            } finally { shape.dispose(); }
            g.setComposite(AlphaComposite.DstIn); g.drawImage(mask, 0, 0, null);
        } finally { g.dispose(); }
        return image;
    }

    private static final class Part {
        final Rectangle bounds; final int color, opacity; final BufferedImage sprite;
        final boolean tiled, flipX, flipY;
        Part(Rectangle bounds, int color, int opacity, BufferedImage sprite, boolean tiled, boolean flipX, boolean flipY) {
            this.bounds = bounds; this.color = color; this.opacity = opacity; this.sprite = sprite;
            this.tiled = tiled; this.flipX = flipX; this.flipY = flipY;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Part)) return false;
            Part p = (Part) other;
            return bounds.equals(p.bounds) && color == p.color && opacity == p.opacity && sprite == p.sprite
                    && tiled == p.tiled && flipX == p.flipX && flipY == p.flipY;
        }
        @Override public int hashCode() { return Objects.hash(bounds, color, opacity, sprite, tiled, flipX, flipY); }
    }
}
