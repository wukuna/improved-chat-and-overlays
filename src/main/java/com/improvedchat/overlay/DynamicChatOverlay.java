package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.MessageCategory;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.model.PlacementMode;
import com.improvedchat.model.TextAlignment;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * One configurable Improved Chat overlay.
 *
 * <p>Free overlays use RuneLite's normal movable/resizable overlay system. Player-attached
 * overlays are positioned from the local player's canvas point with explicit X/Y offsets.
 */
public class DynamicChatOverlay extends Overlay {
    private static final int MIN_ZOOM = -22;
    private static final int MAX_ZOOM = 1400;
    private static final int BELOW_OFFSET_MIN_ZOOM = 40;
    private static final int BELOW_OFFSET_MAX_ZOOM = 150;
    private static final int ABOVE_OFFSET_MIN_ZOOM = -40;
    private static final int ABOVE_OFFSET_MAX_ZOOM = -120;

    private final ImprovedChatPlugin plugin;
    private final ImprovedChatConfig globalConfig;
    private final Client client;
    private final ChatColorConfig chatColorConfig;
    private final ClanChatPresentationService clanPresentationService;
    private OverlayConfig overlayConfig;
    private PlacementMode appliedPlacementMode;
    private final OverlayScrollback scrollback = new OverlayScrollback();
    private static final java.util.concurrent.atomic.AtomicLong PAINT_ORDER = new java.util.concurrent.atomic.AtomicLong();
    private volatile long scrollPaintOrder;
    private volatile java.awt.Rectangle scrollBounds = new java.awt.Rectangle();
    private volatile long lastPaint;
    private volatile boolean scrollVisible;
    private volatile long previewUntil;
    private List<OverlayMessage> previewMessages = new ArrayList<>();

    public DynamicChatOverlay(ImprovedChatPlugin plugin, ImprovedChatConfig globalConfig, Client client,
            ChatColorConfig chatColorConfig, ClanChatPresentationService clanPresentationService,
            OverlayConfig overlayConfig) {
        super(plugin);
        this.plugin = plugin;
        this.globalConfig = globalConfig;
        this.client = client;
        this.chatColorConfig = chatColorConfig;
        this.clanPresentationService = clanPresentationService;
        this.overlayConfig = overlayConfig;

        setPosition(client.isResized() ? OverlayPosition.ABOVE_CHATBOX_RIGHT : OverlayPosition.BOTTOM_LEFT);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setMinimumSize(150);
        applyPlacementPolicy(true);
        rebuildMenuEntries();
    }

    @Override
    public String getName() {
        // Keep the legacy overlay name: RuneLite uses it to restore saved positions.
        return "Improved Chat " + (overlayConfig == null ? "Overlay" : overlayConfig.getId());
    }

    public OverlayConfig getOverlayConfig() {
        return overlayConfig;
    }

    public int getEffectiveWidth() {
        Dimension size = getPreferredSize();
        return size != null && size.width > 0 ? Math.max(150, size.width) : overlayConfig.getWidgetWidth();
    }

    public void setWidth(int width) {
        overlayConfig.setWidgetWidth(width);
        Dimension size = getPreferredSize();
        setPreferredSize(new Dimension(overlayConfig.getWidgetWidth(), size == null ? 0 : size.height));
    }

    public void preview() {
        long now = System.currentTimeMillis();
        previewMessages = java.util.Arrays.asList(
                OverlayMessage.gameMessage("[Preview] Sample game message", now, ChatMessageType.GAMEMESSAGE, false),
                OverlayMessage.senderMessage("Example player", null, "[Preview] A longer sample chat message to check wrapping and readability.",
                        now, ChatMessageType.PUBLICCHAT, false));
        previewUntil = now + 10000;
        scrollBounds = new java.awt.Rectangle();
    }

    public void setOverlayConfig(OverlayConfig overlayConfig) {
        if (!overlayConfig.isShow()) previewUntil = 0;
        this.overlayConfig = overlayConfig;
        scrollBounds = new java.awt.Rectangle();
        applyPlacementPolicy(false);
        rebuildMenuEntries();
    }

    private void rebuildMenuEntries() {
        getMenuEntries().clear();
        if (overlayConfig.isScrollHistory()) getMenuEntries().add(new OverlayMenuEntry(
                MenuAction.RUNELITE_OVERLAY, "Latest", overlayConfig.getName()));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear",
                overlayConfig.getName() + " history"));
    }

    private void applyPlacementPolicy(boolean initial) {
        PlacementMode mode = overlayConfig == null ? PlacementMode.FREE : overlayConfig.getPlacementMode();
        if (!initial && mode == appliedPlacementMode) {
            return;
        }
        appliedPlacementMode = mode;

        setResizable(true);
        if (mode == PlacementMode.FREE) {
            setMovable(true);
            setSnappable(true);
            // Do not clear preferredLocation here. RuneLite owns and persists free-overlay placement.
        } else {
            setMovable(false);
            setSnappable(false);
            setPreferredPosition(null);
            setPreferredLocation(null);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        applyPlacementPolicy(false);
        updateScrollInputState();
        if (!shouldRender()) {
            scrollBounds = new java.awt.Rectangle();
            return null;
        }

        FontSize fontSize = overlayConfig.getFontSize();
        FontMetrics metrics = FontStyler.setupGraphics(graphics, fontSize, overlayConfig);
        IndexedSprite[] modIcons = client.getModIcons();

        int widgetWidth = getEffectiveWidth();

        // Reserve border space even between attention flashes, so the text does not jump.
        int borderInset = overlayConfig.isBorderEnabled()
                || (overlayConfig.isFlashEnabled() && overlayConfig.isFlashBorder())
                ? overlayConfig.getBorderThickness().getPixels() : 0;
        int minimumGap = borderInset > 0 || overlayConfig.isRoundedCorners() ? 3 : 0;
        int paddingX = borderInset + Math.max(minimumGap, overlayConfig.getPaddingHorizontal());
        int paddingY = borderInset + Math.max(minimumGap, overlayConfig.getPaddingVertical());
        // Text is painted one pixel right of its origin, with a further pixel for shadows.
        int contentWidth = Math.max(1, widgetWidth - paddingX * 2 - 2);
        long currentTime = System.currentTimeMillis();
        boolean drawShadow = globalConfig.textShadow();
        boolean wrapText = globalConfig.wrapText();
        com.improvedchat.ChatColorSource colorSource = OverlayChatColors.resolveSource(
                overlayConfig.getTextSettings().chatColorSource(), client);
        PlacementMode placementMode = overlayConfig.getPlacementMode();
        boolean followPlayer = placementMode != PlacementMode.FREE;
        boolean useDynamicHeight = followPlayer || overlayConfig.isDynamicHeight();

        int fadeOutDuration = currentTime < previewUntil ? 0 : overlayConfig.getFadeOutDuration();
        long fadeOutMs = fadeOutDuration * 1000L;
        long fadeOutThreshold = fadeOutMs + 5000L;
        int maxMessages = overlayConfig.getMaxMessages();

        boolean historyMode = overlayConfig.isScrollHistory() && currentTime >= previewUntil;
        if (!historyMode) scrollBounds = new java.awt.Rectangle();
        List<OverlayMessage> messages = currentTime < previewUntil ? previewMessages
                : historyMode ? plugin.getHistoryForOverlay(overlayConfig) : plugin.getMessagesForOverlay(overlayConfig);
        int startIndex = Math.max(0, messages.size() - maxMessages);
        List<RenderLine> renderableLines = new ArrayList<>();

        if (historyMode) {
            renderableLines = scrollback.select(messages, maxMessages, msg -> messageLines(msg, metrics,
                    fontSize, modIcons, contentWidth, currentTime, 0, wrapText, colorSource));
        } else {
            scrollback.latest();
            for (int i = startIndex; i < messages.size(); i++) {
                OverlayMessage msg = messages.get(i);
                int msgMaxFade = msg.getMaxFadeSeconds();
                long threshold = msgMaxFade > 0 ? msgMaxFade * 1000L + 5000L : fadeOutThreshold;
                if ((fadeOutDuration == 0 && msgMaxFade == 0) || currentTime - msg.getTimestamp() < threshold) {
                    for (RenderLine line : messageLines(msg, metrics, fontSize, modIcons, contentWidth,
                            currentTime, fadeOutMs, wrapText, colorSource)) if (line.alpha > 0) renderableLines.add(line);
                }
            }
        }

        InputPreview inputSegments = buildInputPreviewSegments(metrics, fontSize, modIcons);
        if (renderableLines.isEmpty() && inputSegments == null) {
            scrollBounds = new java.awt.Rectangle();
            return null;
        }

        // The RuneScape font can paint accents one pixel above its reported ascent.
        int lineAscent = metrics.getAscent() + 1;
        for (RenderLine line : renderableLines) {
            lineAscent = Math.max(lineAscent, iconAscent(line.segments, modIcons, fontSize, metrics));
        }
        if (inputSegments != null) {
            lineAscent = Math.max(lineAscent, iconAscent(inputSegments.name, modIcons, fontSize, metrics));
            lineAscent = Math.max(lineAscent, iconAscent(inputSegments.text, modIcons, fontSize, metrics));
        }
        int lineDescent = Math.max(2, metrics.getDescent() + 1); // Shadows and underlines need the extra pixel.
        int lineHeight = lineAscent + lineDescent + metrics.getLeading();
        int inputExtra = inputSegments == null ? 0 : lineHeight;
        int contentHeight = useDynamicHeight && !historyMode
                ? renderableLines.size() * lineHeight + inputExtra
                : maxMessages * lineHeight + inputExtra;
        int widgetHeight = Math.max(lineHeight + paddingY * 2, contentHeight + paddingY * 2);

        if (followPlayer) {
            translateToPlayer(graphics, placementMode, widgetWidth, widgetHeight);
        }

        int paintHeight = widgetHeight;
        OverlayPosition anchor = getPreferredPosition() == null ? getPosition() : getPreferredPosition();
        if (!followPlayer && useDynamicHeight && !historyMode && getPreferredLocation() == null
                && (anchor == OverlayPosition.BOTTOM_LEFT || anchor == OverlayPosition.BOTTOM_RIGHT
                    || anchor == OverlayPosition.ABOVE_CHATBOX_RIGHT) && getBounds().height > 0) {
            // RuneLite places bottom-anchored overlays using the previous frame's bounds.
            paintHeight = getBounds().height;
        }
        Shape originalClip = graphics.getClip();
        graphics.clipRect(0, 0, widgetWidth, paintHeight);
        if (overlayConfig.isRoundedCorners()) graphics.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, widgetWidth, paintHeight, 16, 16));

        Color background = overlayConfig.getRenderBackgroundColour();
        if (background.getAlpha() > 0) {
            graphics.setColor(background);
            graphics.fillRect(0, 0, widgetWidth, paintHeight);
        }

        if (historyMode) {
            java.awt.Rectangle hit = new java.awt.Rectangle(0, 0, widgetWidth, paintHeight);
            if (graphics.getClipBounds() != null) hit = hit.intersection(graphics.getClipBounds());
            scrollBounds = graphics.getTransform().createTransformedShape(hit).getBounds();
            lastPaint = System.currentTimeMillis();
            scrollPaintOrder = PAINT_ORDER.incrementAndGet();
        }
        graphics.clipRect(paddingX, paddingY, Math.max(0, widgetWidth - paddingX * 2),
                Math.max(0, paintHeight - paddingY * 2));
        int y = paintHeight - paddingY - lineDescent;
        if (inputSegments != null) {
            drawInputPreview(graphics, inputSegments, y, widgetWidth,
                    paddingX, lineAscent, lineHeight, fontSize, metrics, modIcons, drawShadow, currentTime);
            y -= lineHeight;
        }

        for (int i = renderableLines.size() - 1; i >= 0; i--) {
            // Wrapped messages and the previous frame's bounds can leave less than a full row.
            if (y - lineAscent < paddingY) break;
            RenderLine line = renderableLines.get(i);
            if (line.alpha <= 0) {
                continue;
            }
            drawSegments(graphics, line.segments, line.alpha, y, widgetWidth,
                    paddingX, fontSize, metrics, modIcons, drawShadow, currentTime);
            y -= lineHeight;
        }

        graphics.setClip(originalClip);
        AttentionEngine.drawBorder(graphics, overlayConfig, widgetWidth, paintHeight, currentTime);

        return followPlayer ? null : new Dimension(widgetWidth, widgetHeight);
    }


    private List<RenderLine> messageLines(OverlayMessage msg, FontMetrics metrics, FontSize fontSize,
            IndexedSprite[] modIcons, int contentWidth, long currentTime, long fadeOutMs,
            boolean wrapText, com.improvedchat.ChatColorSource colorSource) {
        Color inherited = getCategoryColour(msg.getType());
        Color msgColor = OverlayStyleEngine.resolveTextColor(overlayConfig, inherited);
        List<TextSegment> senderDecorations = new ArrayList<>();
        Color senderColorOverride = null;
        ClanChatPresentationService.Presentation clanPresentation =
                clanPresentationService.resolve(msg.getSender(), msg.getType());

        if (clanPresentation.isClanMessage()) {
            if (overlayConfig.isShowClanRankIcons()
                    && clanPresentation.getRankIconId() >= 0
                    && !containsIconTag(msg.getSender(), clanPresentation.getRankIconId())) {
                int iconId = clanPresentation.getRankIconId();
                senderDecorations.add(new TextSegment("", iconId,
                        ChatRenderUtils.calculateIconWidth(modIcons, iconId, fontSize), Color.WHITE));
            }

            if (overlayConfig.isShowOfflineStatus() && clanPresentation.isOffline()) {
                if (overlayConfig.isShowOfflineIcon()
                        && clanPresentation.getOfflineIconId() >= 0
                        && !containsIconTag(msg.getSender(), clanPresentation.getOfflineIconId())) {
                    int iconId = clanPresentation.getOfflineIconId();
                    senderDecorations.add(new TextSegment("", iconId,
                            ChatRenderUtils.calculateIconWidth(modIcons, iconId, fontSize), Color.WHITE));
                }
                if (overlayConfig.isColorOfflineNames()) {
                    senderColorOverride = overlayConfig.getOfflineColor();
                }
            }
        }

        return ChatRenderUtils.buildMessageLines(
                msg,
                metrics,
                contentWidth,
                currentTime,
                fadeOutMs,
                wrapText,
                msgColor,
                overlayConfig.isContextualColours(),
                overlayConfig.isHideDuplicateCount(),
                fontSize,
                modIcons,
                overlayConfig.isShowTimestamp(),
                globalConfig.timestampFormat(),
                globalConfig.showChannelName(),
                chatColorConfig,
                senderDecorations,
                senderColorOverride, overlayConfig, colorSource);
    }

    public void showLatest() {
        scrollback.latest();
        scrollBounds = new java.awt.Rectangle();
    }

    public boolean scrollAt(java.awt.Point point, int wheelRotation) {
        if (wheelRotation == 0 || !canScrollAt(point)) return false;
        scrollback.scroll(-Math.max(-100, Math.min(100, wheelRotation)) * 3);
        return true;
    }

    public boolean canScrollAt(java.awt.Point point) {
        return scrollVisible && overlayConfig.isScrollHistory() && overlayConfig.isShow()
                && System.currentTimeMillis() >= previewUntil
                && System.currentTimeMillis() - lastPaint <= 500 && scrollBounds.contains(point);
    }

    /** Called only on the client/render thread. Mouse callbacks must never read widgets. */
    public void updateScrollInputState() {
        scrollVisible = overlayConfig.isScrollHistory() && shouldRender() && System.currentTimeMillis() >= previewUntil;
        if (!scrollVisible) scrollBounds = new java.awt.Rectangle();
    }

    public long getScrollPaintOrder() { return scrollPaintOrder; }

    private void translateToPlayer(Graphics2D graphics, PlacementMode placementMode,
            int widgetWidth, int widgetHeight) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        Point playerPoint;
        if (placementMode == PlacementMode.BELOW_PLAYER) {
            playerPoint = localPlayer.getCanvasTextLocation(graphics, "", 0);
        } else {
            playerPoint = localPlayer.getCanvasTextLocation(graphics, "", localPlayer.getLogicalHeight());
        }
        if (playerPoint == null) {
            return;
        }

        int x = playerPoint.getX() - widgetWidth / 2 + overlayConfig.getOffsetX();
        int y = playerPoint.getY() + calculateZoomOffset(placementMode) - widgetHeight / 2
                + overlayConfig.getOffsetY();
        graphics.translate(x - getBounds().x, y - getBounds().y);
    }

    private boolean shouldRender() {
        if (System.currentTimeMillis() < previewUntil) return client.getGameState() == GameState.LOGGED_IN;
        if (!overlayConfig.isShow() || client.getGameState() != GameState.LOGGED_IN) {
            return false;
        }
        return overlayConfig.isAlwaysVisible() || plugin.isChatboxHidden();
    }

    private int calculateZoomOffset(PlacementMode placementMode) {
        int zoom = client.get3dZoom();
        double normalizedZoom = (double) (zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM);
        normalizedZoom = Math.max(0, Math.min(1, normalizedZoom));

        int minOffset;
        int maxOffset;
        if (placementMode == PlacementMode.BELOW_PLAYER) {
            minOffset = BELOW_OFFSET_MIN_ZOOM;
            maxOffset = BELOW_OFFSET_MAX_ZOOM;
        } else {
            minOffset = ABOVE_OFFSET_MIN_ZOOM;
            maxOffset = ABOVE_OFFSET_MAX_ZOOM;
        }
        return (int) (minOffset + normalizedZoom * (maxOffset - minOffset));
    }

    private Color getCategoryColour(ChatMessageType type) {
        if (type == ChatMessageType.DIDYOUKNOW) {
            return overlayConfig.getTextSettings().didYouKnowColour();
        }
        if (type == ChatMessageType.BROADCAST) {
            return overlayConfig.getTextSettings().broadcastColour();
        }

        switch (MessageCategory.fromType(type)) {
            case GAME:
            case GAME_CLAN:
                return overlayConfig.getTextSettings().gameColour();
            case TRADE:
                return overlayConfig.getTextSettings().tradeColour();
            case CHALLENGE:
                return overlayConfig.getTextSettings().challengeColour();
            case PUBLIC_CHAT:
                return overlayConfig.getTextSettings().publicColour();
            case AUTO:
                return overlayConfig.getTextSettings().autoColour();
            case FRIENDS_CHAT:
                return overlayConfig.getTextSettings().friendsColour();
            case CLAN_CHAT:
                return overlayConfig.getTextSettings().clanColour();
            case GUEST_CLAN_CHAT:
                return overlayConfig.getTextSettings().guestClanColour();
            case GIM_CLAN_CHAT:
                return overlayConfig.getTextSettings().gimClanColour();
            case PRIVATE:
                return overlayConfig.getTextSettings().privateColour();
            default:
                return overlayConfig.getTextSettings().gameColour();
        }
    }

    static boolean containsIconTag(String text, int iconId) {
        return text != null && iconId >= 0 && text.contains("<img=" + iconId + ">");
    }

    private int calculateLineWidth(List<TextSegment> segments, FontMetrics metrics) {
        int width = 0;
        for (TextSegment segment : segments) {
            width += segment.iconId >= 0 ? segment.width : metrics.stringWidth(segment.text);
        }
        return width;
    }

    static int calculateAlignedX(TextAlignment alignment, int paddingX, int contentWidth, int lineWidth) {
        int remaining = Math.max(0, contentWidth - lineWidth);
        if (alignment == TextAlignment.RIGHT) {
            return paddingX + remaining;
        }
        if (alignment == TextAlignment.CENTER) {
            return paddingX + remaining / 2;
        }
        return paddingX;
    }

    private static int iconAscent(List<TextSegment> segments, IndexedSprite[] modIcons,
            FontSize fontSize, FontMetrics metrics) {
        int ascent = 0;
        for (TextSegment segment : segments) {
            if (segment.iconId < 0) continue;
            BufferedImage icon = ChatRenderUtils.getModIconImage(segment.iconId, modIcons);
            if (icon != null) ascent = Math.max(ascent, ChatRenderUtils.iconAscent(icon, fontSize, metrics));
        }
        return ascent;
    }

    private void drawSegments(Graphics2D graphics, List<TextSegment> segments, int alpha, int y,
            int widgetWidth, int paddingX, FontSize fontSize,
            FontMetrics metrics, IndexedSprite[] modIcons, boolean drawShadow, long now) {
        int lineWidth = calculateLineWidth(segments, metrics);
        int contentWidth = Math.max(0, widgetWidth - paddingX * 2 - 2);
        int x = calculateAlignedX(overlayConfig.getTextAlignment(), paddingX, contentWidth, lineWidth);
        drawSegmentsAt(graphics, segments, alpha, x, y, paddingX, fontSize, metrics, modIcons, drawShadow, now);
    }

    private void drawInputPreview(Graphics2D graphics, InputPreview input, int y,
            int widgetWidth, int paddingX, int lineAscent, int lineHeight, FontSize fontSize,
            FontMetrics metrics, IndexedSprite[] modIcons, boolean drawShadow, long now) {
        int nameWidth = calculateLineWidth(input.name, metrics);
        int textWidth = calculateLineWidth(input.text, metrics);
        int contentWidth = Math.max(0, widgetWidth - paddingX * 2);
        // Leave room for the text's one-pixel offset and shadow at the right edge.
        int x = calculateAlignedX(overlayConfig.getTextAlignment(), paddingX, contentWidth,
                nameWidth + textWidth + 2);
        // Even an oversized name must leave a little room for the newest input.
        int gap = nameWidth > 0 && nameWidth + textWidth + 2 > contentWidth ? metrics.charWidth(' ') : 0;
        int fixedWidth = Math.min(nameWidth + gap, Math.max(0, contentWidth - Math.min(32, textWidth + 2)));
        int textLeft = x + fixedWidth;
        int textSpace = Math.max(0, widgetWidth - paddingX - textLeft);
        int scroll = Math.max(0, textWidth + 2 - textSpace);
        Graphics2D preview = (Graphics2D) graphics.create();
        try {
            Shape clip = preview.getClip();
            preview.clipRect(x, y - lineAscent, fixedWidth, lineHeight);
            drawSegmentsAt(preview, input.name, 255, x, y, paddingX, fontSize, metrics, modIcons, drawShadow, now);
            preview.setClip(clip);
            // Clip at the username boundary even with a transparent background.
            preview.clipRect(textLeft, y - lineAscent, textSpace, lineHeight);
            drawSegmentsAt(preview, input.text, 255, textLeft - scroll, y, paddingX,
                    fontSize, metrics, modIcons, drawShadow, now);
        } finally {
            preview.dispose();
        }
    }

    private void drawSegmentsAt(Graphics2D graphics, List<TextSegment> segments, int alpha,
            int x, int y, int paddingX, FontSize fontSize,
            FontMetrics metrics, IndexedSprite[] modIcons, boolean drawShadow, long now) {

        for (TextSegment segment : segments) {
            if (segment.iconId >= 0) {
                BufferedImage img = ChatRenderUtils.getModIconImage(segment.iconId, modIcons);
                if (img != null) {
                    x += ChatRenderUtils.drawIcon(graphics, img, fontSize, metrics, x, y);
                } else {
                    x += segment.width;
                }
            } else {
                Color segmentColor = segment.color == null ? Color.WHITE : segment.color;
                if (segment.shadow != null) {
                    graphics.setColor(ChatRenderUtils.withAlpha(segment.shadow, alpha));
                    graphics.drawString(segment.text, x + 2, y + 1);
                }
                com.improvedchat.model.AnimatedTextStyle style = segment.textStyle == null
                        ? overlayConfig.getAnimatedTextStyle() : segment.textStyle;
                int width = style == com.improvedchat.model.AnimatedTextStyle.NORMAL || style == com.improvedchat.model.AnimatedTextStyle.RGB_STATIC
                        ? ChatRenderUtils.drawText(graphics, segment.text, segmentColor, alpha, x, y,
                                segment.shadow == null && drawShadow, metrics)
                        : RgbAnimation.drawText(graphics, segment.text, alpha, x, y,
                                segment.shadow == null && drawShadow, metrics, overlayConfig, now, paddingX, style);
                if (segment.underline != null) {
                    graphics.setColor(ChatRenderUtils.withAlpha(segment.underline, alpha));
                    graphics.drawLine(x + 1, y + 1, x + width, y + 1);
                }
                if (segment.strikethrough != null) {
                    graphics.setColor(ChatRenderUtils.withAlpha(segment.strikethrough, alpha));
                    int strikeY = y - metrics.getAscent() + (int) (metrics.getAscent() * 0.7);
                    graphics.drawLine(x + 1, strikeY, x + width, strikeY);
                }
                x += width;
            }
        }
    }

    private InputPreview buildInputPreviewSegments(FontMetrics metrics, FontSize fontSize,
            IndexedSprite[] modIcons) {
        if (!overlayConfig.isShowInputPreview()) {
            return null;
        }

        String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
        String text = resolveInputText(typed);
        if (text == null || text.isEmpty()) {
            return null;
        }

        boolean hasTyped = typed != null && !typed.isEmpty();
        if (overlayConfig.isPreviewOnlyWhenTyping() && !hasTyped) {
            return null;
        }

        Color inherited = hasTyped ? overlayConfig.getTextSettings().publicColour() : Color.WHITE;
        Color messageColor = OverlayStyleEngine.resolveTextColor(overlayConfig, inherited);
        Color nameColor = overlayConfig.isOverlayColorOverrideEnabled() ? messageColor : Color.WHITE;

        List<TextSegment> name = new ArrayList<>();
        List<TextSegment> body;
        int colonIdx = text.indexOf(':');
        if (colonIdx >= 0) {
            name.addAll(ChatRenderUtils.parseTextWithColoursAndIcons(
                    text.substring(0, colonIdx + 1), metrics, modIcons, false,
                    nameColor, fontSize, chatColorConfig));
            body = ChatRenderUtils.parseTextWithColoursAndIcons(
                    text.substring(colonIdx + 1), metrics, modIcons, false,
                    messageColor, fontSize, chatColorConfig);
        } else {
            body = ChatRenderUtils.parseTextWithColoursAndIcons(
                    text, metrics, modIcons, false, messageColor, fontSize, chatColorConfig);
        }
        if (overlayConfig.getAnimatedTextStyle() == com.improvedchat.model.AnimatedTextStyle.RGB_STATIC) {
            int[] offset = {0};
            name = ChatRenderUtils.rainbowSegments(name, overlayConfig.getTextSettings().rainbowStyle(), metrics, offset);
            body = ChatRenderUtils.rainbowSegments(body, overlayConfig.getTextSettings().rainbowStyle(), metrics, offset);
        }
        return new InputPreview(name, body);
    }

    private static final class InputPreview {
        final List<TextSegment> name, text;
        InputPreview(List<TextSegment> name, List<TextSegment> text) {
            this.name = name;
            this.text = text;
        }
    }

    private String resolveInputText(String typed) {
        Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (input != null) {
            String text = input.getText();
            if (text != null && !text.isEmpty()) {
                return text;
            }
        }

        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null) {
            return null;
        }
        return local.getName() + ": " + (typed == null ? "" : typed) + "*";
    }
}
