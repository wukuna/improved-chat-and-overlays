package com.improvedchat.overlay;

import com.improvedchat.ChatColorSource;
import com.improvedchat.MessageColorRuleEngine;
import com.improvedchat.RainbowStyle;
import com.improvedchat.OverlayColorRuleEngine;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.OverlayMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.api.IndexedSprite;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatRenderUtils {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final Pattern RAINBOW_TOKEN_PATTERN = Pattern.compile("\\s+|\\S+");
    private static final int MAX_MESSAGE_LENGTH = 500;

    private static IndexedSprite[] cachedModIconsRef;
    private static final Map<Integer, BufferedImage> iconImageCache = new HashMap<>();

    private static final Set<ChatMessageType> SENDER_PREFIX_TYPES = EnumSet.of(
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT,
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.MODCHAT,
            ChatMessageType.AUTOTYPER,
            ChatMessageType.MODAUTOTYPER,
            ChatMessageType.FRIENDSCHAT,
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT
    );

    private static final Set<ChatMessageType> PM_TYPES = EnumSet.of(
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT
    );

    private ChatRenderUtils() {
    }

    public static FontMetrics setupGraphics(Graphics2D graphics, FontSize fontSize) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Font baseFont;
        if (fontSize == FontSize.SMALL) {
            baseFont = FontManager.getRunescapeSmallFont();
        } else {
            baseFont = FontManager.getRunescapeFont();
            if (fontSize == FontSize.LARGE || fontSize == FontSize.EXTRA_LARGE) {
                baseFont = baseFont.deriveFont(baseFont.getSize2D() * fontSize.getScale());
            }
        }
        graphics.setFont(baseFont);
        return graphics.getFontMetrics();
    }

    public static int drawIcon(Graphics2D graphics, BufferedImage img, FontSize fontSize,
            FontMetrics metrics, int x, int y) {
        if (img == null) {
            return 0;
        }
        float scale = fontSize == null ? 1.0f : fontSize.getScale();
        int iconWidth = Math.max(1, Math.round(img.getWidth() * scale));
        int iconHeight = Math.max(1, Math.round(img.getHeight() * scale));

        int iconY = y - iconAscent(img, fontSize, metrics);

        graphics.drawImage(img, x + 1, iconY, iconWidth, iconHeight, null);
        return iconWidth + 2;
    }

    static int iconAscent(BufferedImage img, FontSize fontSize, FontMetrics metrics) {
        float scale = fontSize == null ? 1.0f : fontSize.getScale();
        return Math.max(1, Math.round(img.getHeight() * scale)) - metrics.getDescent()
                + (fontSize == FontSize.SMALL ? 2 : 4);
    }

    public static int drawText(Graphics2D graphics, String text, Color color, int alpha, int x, int y,
            boolean drawShadow, FontMetrics metrics) {
        if (drawShadow) {
            graphics.setColor(withAlpha(Color.BLACK, alpha));
            graphics.drawString(text, x + 2, y + 1);
        }
        graphics.setColor(withAlpha(color, alpha));
        graphics.drawString(text, x + 1, y);
        return metrics.stringWidth(text);
    }

    public static int calculateAlpha(OverlayMessage msg, long currentTime, long fadeOutMs) {
        if (fadeOutMs <= 0) {
            return 255;
        }
        long age = currentTime - msg.getTimestamp();
        if (age <= fadeOutMs) {
            return 255;
        }
        double fadeProgress = Math.min(1.0, (age - fadeOutMs) / 1200.0);
        return (int) (255 * (1.0 - fadeProgress));
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static BufferedImage getModIconImage(int iconId, IndexedSprite[] modIcons) {
        if (modIcons == null || iconId < 0 || iconId >= modIcons.length) {
            return null;
        }

        if (modIcons != cachedModIconsRef) {
            iconImageCache.clear();
            cachedModIconsRef = modIcons;
        }

        if (iconImageCache.containsKey(iconId)) {
            return iconImageCache.get(iconId);
        }

        BufferedImage img = indexedSpriteToImage(modIcons[iconId]);
        iconImageCache.put(iconId, img);
        return img;
    }

    public static BufferedImage indexedSpriteToImage(IndexedSprite sprite) {
        if (sprite == null) {
            return null;
        }
        try {
            int width = sprite.getWidth();
            int height = sprite.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            byte[] pixels = sprite.getPixels();
            int[] palette = sprite.getPalette();
            if (pixels == null || palette == null || palette.length == 0) {
                return null;
            }
            int totalPixels = Math.min(pixels.length, width * height);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] imgPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < totalPixels; i++) {
                int index = pixels[i] & 0xFF;
                if (index != 0 && index < palette.length) {
                    imgPixels[i] = palette[index] | 0xFF000000;
                }
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatTimestamp(long timestamp, String format) {
        try {
            return new SimpleDateFormat(format).format(new Date(timestamp));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String formatTimeAgo(long timestamp, long currentTime) {
        long seconds = currentTime <= timestamp ? 0 : (currentTime - timestamp) / 1000;
        if (seconds >= 360000) return null;
        long value = seconds < 60 ? seconds : seconds < 3600 ? seconds / 60 : seconds / 3600;
        String unit = seconds < 60 ? "s" : seconds < 3600 ? "m" : "h";
        return (value < 10 ? "0" : "") + value + unit;
    }

    private static int timeAgoColumnWidth(FontMetrics metrics) {
        int digitWidth = 0;
        for (char digit = '0'; digit <= '9'; digit++) {
            digitWidth = Math.max(digitWidth, metrics.charWidth(digit));
        }
        int unitWidth = Math.max(metrics.charWidth('s'), Math.max(metrics.charWidth('m'), metrics.charWidth('h')));
        return 2 * digitWidth + unitWidth + metrics.charWidth(' ');
    }

    public static List<RenderLine> buildMessageLines(OverlayMessage msg, FontMetrics metrics,
            int widgetWidth, long currentTime, long fadeOutMs, boolean wrapText, Color textColor,
            boolean retainContextualColours, boolean hideDuplicateCount,
            FontSize fontSize, IndexedSprite[] modIcons, boolean showTimestamp, String timestampFormat,
            boolean showChannelName, ChatColorConfig chatColorConfig) {
        return buildMessageLines(msg, metrics, widgetWidth, currentTime, fadeOutMs, wrapText,
                textColor, retainContextualColours, hideDuplicateCount, fontSize, modIcons,
                showTimestamp, timestampFormat, showChannelName, chatColorConfig,
                Collections.emptyList(), null);
    }

    public static List<RenderLine> buildMessageLines(OverlayMessage msg, FontMetrics metrics,
            int widgetWidth, long currentTime, long fadeOutMs, boolean wrapText, Color textColor,
            boolean retainContextualColours, boolean hideDuplicateCount,
            FontSize fontSize, IndexedSprite[] modIcons, boolean showTimestamp, String timestampFormat,
            boolean showChannelName, ChatColorConfig chatColorConfig,
            List<TextSegment> senderDecorations, Color senderColorOverride) {
        return buildMessageLines(msg, metrics, widgetWidth, currentTime, fadeOutMs, wrapText,
                textColor, retainContextualColours, hideDuplicateCount, fontSize, modIcons,
                showTimestamp, timestampFormat, showChannelName, chatColorConfig,
                senderDecorations, senderColorOverride, null);
    }

    public static List<RenderLine> buildMessageLines(OverlayMessage msg, FontMetrics metrics,
            int widgetWidth, long currentTime, long fadeOutMs, boolean wrapText, Color textColor,
            boolean retainContextualColours, boolean hideDuplicateCount,
            FontSize fontSize, IndexedSprite[] modIcons, boolean showTimestamp, String timestampFormat,
            boolean showChannelName, ChatColorConfig chatColorConfig,
            List<TextSegment> senderDecorations, Color senderColorOverride, OverlayConfig overlay) {
        return buildMessageLines(msg, metrics, widgetWidth, currentTime, fadeOutMs, wrapText,
                textColor, retainContextualColours, hideDuplicateCount, fontSize, modIcons,
                showTimestamp, timestampFormat, showChannelName, chatColorConfig, senderDecorations,
                senderColorOverride, overlay, overlay == null ? OverlayColorRuleEngine.chatColorSource()
                        : overlay.getTextSettings().chatColorSource());
    }

    public static List<RenderLine> buildMessageLines(OverlayMessage msg, FontMetrics metrics,
            int widgetWidth, long currentTime, long fadeOutMs, boolean wrapText, Color textColor,
            boolean retainContextualColours, boolean hideDuplicateCount,
            FontSize fontSize, IndexedSprite[] modIcons, boolean showTimestamp, String timestampFormat,
            boolean showChannelName, ChatColorConfig chatColorConfig,
            List<TextSegment> senderDecorations, Color senderColorOverride, OverlayConfig overlay,
            ChatColorSource colorSource) {
        com.improvedchat.ImprovedChatConfig rules = overlay == null ? null : overlay.getTextSettings();
        boolean rulesActive = retainContextualColours || (overlay != null && overlay.getTextSettings().isHighlightsOverrideDefaults());

        List<RenderLine> lines = new ArrayList<>();
        int alpha = AttentionEngine.adjustAlpha(msg, currentTime, calculateAlpha(msg, currentTime, fadeOutMs));

        ChatMessageType type = msg.getType();
        boolean hasSenderPrefix = SENDER_PREFIX_TYPES.contains(type);
        boolean isPm = PM_TYPES.contains(type);
        boolean isLoginNotification = type == ChatMessageType.LOGINLOGOUTNOTIFICATION;

        Color bodyBaseColor = textColor;
        if (retainContextualColours) {
            Color configured = getConfiguredChatColor(type, false, chatColorConfig, colorSource);
            if (configured != null) {
                bodyBaseColor = configured;
            }
        }

        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (showTimestamp) {
            boolean relative = overlay != null && overlay.getTimestampStyle() == com.improvedchat.model.TimestampStyle.TIME_AGO;
            String ts = relative ? formatTimeAgo(msg.getTimestamp(), currentTime)
                    : timestampFormat == null || timestampFormat.isEmpty() ? null : formatTimestamp(msg.getTimestamp(), timestampFormat);
            if (ts != null) {
                ts += " ";
                int width = metrics.stringWidth(ts);
                Color tsColor = retainContextualColours ? Color.WHITE : textColor;
                if (overlay != null && overlay.isTimestampColorOverride()) tsColor = overlay.getTimestampColors().getPrimary();
                List<TextSegment> timestampSegments = new ArrayList<>();
                timestampSegments.add(new TextSegment(ts, -1, width, tsColor));
                if (overlay != null && overlay.getTimestampColors().getMode() == com.improvedchat.model.AnimatedTextStyle.RGB_STATIC)
                    timestampSegments = rainbowSegments(timestampSegments, overlay.getTextSettings().rainbowStyle(), metrics, new int[]{0});
                for (TextSegment segment : timestampSegments) segment.timestamp = true;
                headerSegments.addAll(timestampSegments);
                headerWidth += width;
            }
            if (relative) {

                int columnWidth = timeAgoColumnWidth(metrics);
                headerSegments.add(new TextSegment("", TextSegment.SPACER,
                        Math.max(0, columnWidth - headerWidth), null));
                headerWidth = Math.max(headerWidth, columnWidth);
            }
        }

        if (hasSenderPrefix && !isLoginNotification && msg.getSender() != null) {
            Color nameColor = senderColorOverride != null
                    ? senderColorOverride
                    : (retainContextualColours ? OverlayChatColors.sender(type, chatColorConfig, colorSource) : textColor);
            if (isPm) {
                String prefix = msg.isOutgoing() ? "To " : "From ";
                headerSegments.add(new TextSegment(prefix, -1, metrics.stringWidth(prefix), bodyBaseColor));
                headerWidth += metrics.stringWidth(prefix);
            }

            boolean showChannel = overlay == null ? showChannelName : overlay.isShowChannelNames(showChannelName);
            if (showChannel && msg.getChannelName() != null && !msg.getChannelName().isEmpty()) {
                Color bracketColor = retainContextualColours ? Color.WHITE : textColor;
                Color channelTextColor = retainContextualColours ? OverlayChatColors.channel(type, chatColorConfig, colorSource) : textColor;

                String open = "[";
                headerSegments.add(new TextSegment(open, -1, metrics.stringWidth(open), bracketColor));
                headerWidth += metrics.stringWidth(open);

                String channelText = msg.getChannelName();
                headerSegments.add(new TextSegment(channelText, -1, metrics.stringWidth(channelText), channelTextColor));
                headerWidth += metrics.stringWidth(channelText);

                String close = "] ";
                headerSegments.add(new TextSegment(close, -1, metrics.stringWidth(close), bracketColor));
                headerWidth += metrics.stringWidth(close);
            }

            if (senderDecorations != null) {
                for (TextSegment decoration : senderDecorations) {
                    headerSegments.add(decoration);
                    headerWidth += decoration.width;
                }
            }

            List<TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics, modIcons,
                    nameColor, fontSize);
            for (TextSegment seg : senderSegments) {
                headerSegments.add(seg);
                headerWidth += seg.width;
            }

            headerSegments.add(new TextSegment(": ", -1, metrics.stringWidth(": "), nameColor));
            headerWidth += metrics.stringWidth(": ");
        }

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        if (msg.getCount() > 1 && !hideDuplicateCount) {
            messageText = messageText + " (" + msg.getCount() + ")";
        }

        OverlayColorRuleEngine.Decision decision = rulesActive
                ? (overlay == null ? OverlayColorRuleEngine.decide(messageText, type) : OverlayColorRuleEngine.decide(messageText, type, rules))
                : OverlayColorRuleEngine.Decision.none();
        com.improvedchat.model.AnimatedTextStyle matchedStyle = decision.kind == OverlayColorRuleEngine.Kind.RAINBOW
                ? (overlay == null ? com.improvedchat.model.AnimatedTextStyle.RGB_STATIC : overlay.getTextSettings().getHighlightStyle()) : null;
        if (matchedStyle == com.improvedchat.model.AnimatedTextStyle.NORMAL) matchedStyle = null;
        Color forceColor = rulesActive ? (overlay == null ? MessageColorRuleEngine.colorFor(messageText, type)
                : MessageColorRuleEngine.colorFor(messageText, type, rules)) : null;

        if (overlay != null && !overlay.getTextSettings().isHighlightsOverrideDefaults()
                && overlay.getAnimatedTextStyle() != com.improvedchat.model.AnimatedTextStyle.NORMAL) {
            matchedStyle = null;
            forceColor = null;
        }

        String display = OverlayTextReplacements.displayText(overlay, msg.getMessage());
        if (!java.util.Objects.equals(display, msg.getMessage())) {
            messageText = display;
            if (messageText.length() > MAX_MESSAGE_LENGTH) messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
            if (msg.getCount() > 1 && !hideDuplicateCount) messageText += " (" + msg.getCount() + ")";
        }
        List<TextSegment> messageSegments = parseTextWithColoursAndIcons(messageText, metrics, modIcons,
                retainContextualColours, bodyBaseColor, fontSize, chatColorConfig, type,
                colorSource);

        if (forceColor != null) messageSegments = applySolidColor(messageSegments, forceColor);
        boolean staticRainbow = matchedStyle == com.improvedchat.model.AnimatedTextStyle.RGB_STATIC;
        boolean defaultStatic = overlay != null && overlay.getAnimatedTextStyle() == com.improvedchat.model.AnimatedTextStyle.RGB_STATIC;
        if (defaultStatic) headerSegments = rainbowSegments(headerSegments, overlay.getTextSettings().rainbowStyle(), metrics, new int[]{0});

        if (!wrapText) {
            if (staticRainbow || (defaultStatic && matchedStyle == null && forceColor == null)) {
                messageSegments = rainbowSegments(messageSegments, (staticRainbow ? decision.rainbowStyle : overlay.getTextSettings().rainbowStyle()), metrics, new int[] {0});
            }
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics,
                    firstLineRemaining, widgetWidth, bodyBaseColor);
            if (staticRainbow || (defaultStatic && matchedStyle == null && forceColor == null)) {
                int[] rainbowIndex = new int[] {0};
                for (int lineIndex = 0; lineIndex < wrappedLines.size(); lineIndex++) {
                    wrappedLines.set(lineIndex, rainbowSegments(wrappedLines.get(lineIndex),
                            (staticRainbow ? decision.rainbowStyle : overlay.getTextSettings().rainbowStyle()), metrics, rainbowIndex));
                }
            }
            addWrappedLines(lines, alpha, headerSegments, wrappedLines);
        }

        if (overlay != null) {
            boolean override = overlay.getTextSettings().isHighlightsOverrideDefaults()
                    || overlay.getAnimatedTextStyle() == com.improvedchat.model.AnimatedTextStyle.NORMAL;
            com.improvedchat.model.AnimatedTextStyle bodyStyle = override
                    ? (matchedStyle != null ? matchedStyle : forceColor != null ? com.improvedchat.model.AnimatedTextStyle.NORMAL : null) : null;

            for (RenderLine line : lines) {
                for (TextSegment segment : line.segments) {
                    if (!headerSegments.contains(segment)) segment.textStyle = bodyStyle;
                }
            }
        }

        return lines;
    }

    public static void addWrappedLines(List<RenderLine> lines, int alpha, List<TextSegment> headerSegments,
            List<List<TextSegment>> wrappedLines) {
        if (wrappedLines.isEmpty()) {
            lines.add(new RenderLine(headerSegments, alpha));
        } else {
            List<TextSegment> firstLine = new ArrayList<>(headerSegments);
            firstLine.addAll(wrappedLines.get(0));
            lines.add(new RenderLine(firstLine, alpha));

            for (int i = 1; i < wrappedLines.size(); i++) {
                lines.add(new RenderLine(wrappedLines.get(i), alpha));
            }
        }
    }

    public static int calculateIconWidth(IndexedSprite[] modIcons, int iconId, FontSize fontSize) {
        int iconWidth = 13;
        float scale = fontSize == null ? 1.0f : fontSize.getScale();
        if (modIcons != null && iconId >= 0 && iconId < modIcons.length && modIcons[iconId] != null) {
            iconWidth = Math.max(1, Math.round(modIcons[iconId].getWidth() * scale)) + 2;
        } else {
            iconWidth = Math.max(1, Math.round(iconWidth * scale));
        }
        return iconWidth;
    }

    public static List<TextSegment> parseTextWithIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, Color textColor, FontSize fontSize) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        text = text.replace("<lt>", "<").replace("<gt>", ">").replace("<at>", "@");

        Matcher matcher = IMG_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                segments.add(new TextSegment(before, -1, metrics.stringWidth(before), textColor));
            }

            try {
                int iconId = Integer.parseInt(matcher.group(1));
                int iconWidth = calculateIconWidth(modIcons, iconId, fontSize);
                segments.add(new TextSegment("", iconId, iconWidth, textColor));
            } catch (NumberFormatException e) {
                String raw = matcher.group(0);
                segments.add(new TextSegment(raw, -1, metrics.stringWidth(raw), textColor));
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            segments.add(new TextSegment(after, -1, metrics.stringWidth(after), textColor));
        }

        return segments;
    }

    public static List<TextSegment> parseTextWithColoursAndIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, boolean retainContextualColours, Color textColor,
            FontSize fontSize, ChatColorConfig chatColorConfig) {
        return parseTextWithColoursAndIcons(text, metrics, modIcons, retainContextualColours,
                textColor, fontSize, chatColorConfig, null);
    }

    public static List<TextSegment> parseTextWithColoursAndIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, boolean retainContextualColours, Color textColor,
            FontSize fontSize, ChatColorConfig chatColorConfig, ChatMessageType messageType) {
        return parseTextWithColoursAndIcons(text, metrics, modIcons, retainContextualColours,
                textColor, fontSize, chatColorConfig, messageType, OverlayColorRuleEngine.chatColorSource());
    }

    public static List<TextSegment> parseTextWithColoursAndIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, boolean retainContextualColours, Color textColor,
            FontSize fontSize, ChatColorConfig chatColorConfig, ChatMessageType messageType, ChatColorSource source) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        text = com.improvedchat.ChatDisplayText.expandTemplates(text);
        Color currentColor = textColor;
        Color underline = null, strike = null, shadow = null;
        StringBuilder currentText = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) != '<') { currentText.append(text.charAt(i++)); continue; }
            int end = text.indexOf('>', i);
            if (end < 0) { currentText.append(text.charAt(i++)); continue; }
            String tag = text.substring(i + 1, end);
            i = end + 1;
            if (tag.equals("lt") || tag.equals("gt") || tag.equals("at") || tag.equals("nbh")) {
                currentText.append(tag.equals("lt") ? '<' : tag.equals("gt") ? '>' : tag.equals("at") ? '@' : '-');
                continue;
            }
            if (currentText.length() > 0) {
                String value = currentText.toString();
                segments.add(new TextSegment(value, -1, metrics.stringWidth(value), currentColor, underline, strike, shadow));
                currentText.setLength(0);
            }
            try {
                if (tag.equals("br") || tag.equals("n")) {
                    segments.add(new TextSegment("", TextSegment.LINE_BREAK, 0, currentColor));
                    currentColor = textColor; underline = null; strike = null; shadow = null;
                } else if (tag.startsWith("img=")) {
                    int icon = Integer.parseInt(tag.substring(4));
                    if (icon >= 0) segments.add(new TextSegment("", icon, calculateIconWidth(modIcons, icon, fontSize), currentColor));
                } else if (tag.equals("colNORMAL") || tag.equals("/col")) currentColor = textColor;
                else if (tag.equals("colHIGHLIGHT") && retainContextualColours) {
                    Color highlight = getConfiguredChatColor(messageType, true, chatColorConfig, source);
                    currentColor = highlight == null ? textColor : highlight;
                } else if (tag.startsWith("col=") && retainContextualColours) currentColor = tagColor(tag.substring(4));
                else if (tag.equals("u")) underline = Color.BLACK;
                else if (tag.startsWith("u=")) underline = tagColor(tag.substring(2));
                else if (tag.equals("/u")) underline = null;
                else if (tag.equals("str")) strike = new Color(0x800000);
                else if (tag.startsWith("str=")) strike = tagColor(tag.substring(4));
                else if (tag.equals("/str")) strike = null;
                else if (tag.equals("shad")) shadow = Color.BLACK;
                else if (tag.startsWith("shad=")) shadow = tagColor(tag.substring(5));
                else if (tag.equals("/shad")) shadow = null;

            } catch (NumberFormatException ignored) {

            }
        }
        if (currentText.length() > 0) {
            String value = currentText.toString();
            segments.add(new TextSegment(value, -1, metrics.stringWidth(value), currentColor, underline, strike, shadow));
        }

        return segments;
    }

    private static Color tagColor(String hex) {
        if (hex.isEmpty() || hex.length() > 6 || !hex.matches("[0-9a-fA-F]+")) throw new NumberFormatException();
        return new Color(Integer.parseInt(hex, 16));
    }

    private static Color getConfiguredChatColor(ChatMessageType type, boolean highlight,
            ChatColorConfig chatColorConfig, ChatColorSource source) {
        if (type == null || chatColorConfig == null || source == ChatColorSource.WIDGET) {
            return null;
        }
        return source == ChatColorSource.OPAQUE
                ? getOpaqueChatColor(type, highlight, chatColorConfig)
                : getTransparentChatColor(type, highlight, chatColorConfig);
    }

    private static Color getTransparentChatColor(ChatMessageType type, boolean highlight,
            ChatColorConfig config) {
        switch (type) {
            case PUBLICCHAT:
            case MODCHAT:
                return highlight ? config.transparentPublicChatHighlight() : config.transparentPublicChat();
            case PRIVATECHATOUT:
                return highlight ? config.transparentPrivateMessageSentHighlight() : config.transparentPrivateMessageSent();
            case PRIVATECHAT:
            case MODPRIVATECHAT:
                return highlight ? config.transparentPrivateMessageReceivedHighlight() : config.transparentPrivateMessageReceived();
            case FRIENDSCHATNOTIFICATION:
                return highlight ? config.transparentFriendsChatInfoHighlight() : config.transparentFriendsChatInfo();
            case FRIENDSCHAT:
                return highlight ? config.transparentFriendsChatMessageHighlight() : config.transparentFriendsChatMessage();
            case CLAN_MESSAGE:
            case CLAN_GIM_MESSAGE:
                return highlight ? config.transparentClanChatInfoHighlight() : config.transparentClanChatInfo();
            case CLAN_GUEST_MESSAGE:
                return highlight ? config.transparentClanChatGuestInfoHighlight() : config.transparentClanChatGuestInfo();
            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
                return highlight ? config.transparentClanChatMessageHighlight() : config.transparentClanChatMessage();
            case CLAN_GUEST_CHAT:
                return highlight ? config.transparentClanChatGuestMessageHighlight() : config.transparentClanChatGuestMessage();
            case AUTOTYPER:
            case MODAUTOTYPER:
                return highlight ? config.transparentAutochatMessageHighlight() : config.transparentAutochatMessage();
            case TRADE:
            case TRADE_SENT:
            case TRADEREQ:
                return highlight ? config.transparentTradeChatMessageHighlight() : config.transparentTradeChatMessage();
            case GAMEMESSAGE:
            case ENGINE:
            case BROADCAST:
            case WELCOME:
            case PLAYERRELATED:
            case LEVELUPMESSAGE:
            case TENSECTIMEOUT:
            case NPC_SAY:
            case MESBOX:
                return highlight ? config.transparentServerMessageHighlight() : config.transparentServerMessage();
            case CONSOLE:
                return highlight ? config.transparentGameMessageHighlight() : config.transparentGameMessage();
            case OBJECT_EXAMINE:
            case NPC_EXAMINE:
            case ITEM_EXAMINE:
                return highlight ? config.transparentExamineHighlight() : config.transparentExamine();
            case SPAM:
                return highlight ? config.transparentFilteredHighlight() : config.transparentFiltered();
            default:
                return null;
        }
    }

    private static Color getOpaqueChatColor(ChatMessageType type, boolean highlight,
            ChatColorConfig config) {
        switch (type) {
            case PUBLICCHAT:
            case MODCHAT:
                return highlight ? config.opaquePublicChatHighlight() : config.opaquePublicChat();
            case PRIVATECHATOUT:
                return highlight ? config.opaquePrivateMessageSentHighlight() : config.opaquePrivateMessageSent();
            case PRIVATECHAT:
            case MODPRIVATECHAT:
                return highlight ? config.opaquePrivateMessageReceivedHighlight() : config.opaquePrivateMessageReceived();
            case FRIENDSCHATNOTIFICATION:
                return highlight ? config.opaqueFriendsChatInfoHighlight() : config.opaqueFriendsChatInfo();
            case FRIENDSCHAT:
                return highlight ? config.opaqueFriendsChatMessageHighlight() : config.opaqueFriendsChatMessage();
            case CLAN_MESSAGE:
            case CLAN_GIM_MESSAGE:
                return highlight ? config.opaqueClanChatInfoHighlight() : config.opaqueClanChatInfo();
            case CLAN_GUEST_MESSAGE:
                return highlight ? config.opaqueClanChatGuestInfoHighlight() : config.opaqueClanChatGuestInfo();
            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
                return highlight ? config.opaqueClanChatMessageHighlight() : config.opaqueClanChatMessage();
            case CLAN_GUEST_CHAT:
                return highlight ? config.opaqueClanChatGuestMessageHighlight() : config.opaqueClanChatGuestMessage();
            case AUTOTYPER:
            case MODAUTOTYPER:
                return highlight ? config.opaqueAutochatMessageHighlight() : config.opaqueAutochatMessage();
            case TRADE:
            case TRADE_SENT:
            case TRADEREQ:
                return highlight ? config.opaqueTradeChatMessageHighlight() : config.opaqueTradeChatMessage();
            case GAMEMESSAGE:
            case ENGINE:
            case BROADCAST:
            case WELCOME:
            case PLAYERRELATED:
            case LEVELUPMESSAGE:
            case TENSECTIMEOUT:
            case NPC_SAY:
            case MESBOX:
                return highlight ? config.opaqueServerMessageHighlight() : config.opaqueServerMessage();
            case CONSOLE:
                return highlight ? config.opaqueGameMessageHighlight() : config.opaqueGameMessage();
            case OBJECT_EXAMINE:
            case NPC_EXAMINE:
            case ITEM_EXAMINE:
                return highlight ? config.opaqueExamineHighlight() : config.opaqueExamine();
            case SPAM:
                return highlight ? config.opaqueFilteredHighlight() : config.opaqueFiltered();
            default:
                return null;
        }
    }

    private static List<TextSegment> applySolidColor(List<TextSegment> input, Color color) {
        List<TextSegment> out = new ArrayList<>(input.size());
        for (TextSegment seg : input) {
            out.add(seg.copy(seg.text, seg.width, seg.iconId == TextSegment.LINE_BREAK ? seg.color : color));
        }
        return out;
    }

    private static final Color[] RAINBOW = new Color[] {
        new Color(255, 59, 48),
        new Color(255, 149, 0),
        new Color(255, 214, 10),
        new Color(52, 199, 89),
        new Color(50, 215, 255),
        new Color(10, 132, 255),
        new Color(94, 92, 230),
        new Color(191, 90, 242)
    };

    static List<TextSegment> rainbowSegments(List<TextSegment> input,
            RainbowStyle style, FontMetrics metrics, int[] colorIndexRef) {
        List<TextSegment> out = new ArrayList<>();
        int colorIndex = colorIndexRef[0];
        for (TextSegment seg : input) {
            if (seg.timestamp || seg.iconId >= 0 || seg.iconId == TextSegment.LINE_BREAK || seg.iconId == TextSegment.SPACER) {
                out.add(seg);
                continue;
            }
            if (style == RainbowStyle.PER_LETTER) {
                int offset = 0;
                while (offset < seg.text.length()) {
                    int cp = seg.text.codePointAt(offset);
                    String ch = new String(Character.toChars(cp));
                    Color c;
                    if (Character.isWhitespace(cp)) {
                        c = RAINBOW[(Math.max(0, colorIndex - 1)) % RAINBOW.length];
                    } else {
                        c = RAINBOW[colorIndex++ % RAINBOW.length];
                    }
                    out.add(seg.copy(ch, metrics.stringWidth(ch), c));
                    offset += Character.charCount(cp);
                }
            } else {
                Matcher tokenMatcher = RAINBOW_TOKEN_PATTERN.matcher(seg.text);
                while (tokenMatcher.find()) {
                    String token = tokenMatcher.group();
                    boolean whitespace = Character.isWhitespace(token.codePointAt(0));
                    Color c = whitespace
                            ? RAINBOW[(Math.max(0, colorIndex - 1)) % RAINBOW.length]
                            : RAINBOW[colorIndex++ % RAINBOW.length];
                    out.add(seg.copy(token, metrics.stringWidth(token), c));
                }
            }
        }
        colorIndexRef[0] = colorIndex;
        return out;
    }

    public static List<List<TextSegment>> wrapSegments(List<TextSegment> segments,
            FontMetrics metrics, int firstLineWidth, int subsequentLineWidth, Color textColor) {
        List<List<TextSegment>> lines = new ArrayList<>();
        List<TextSegment> currentLine = new ArrayList<>();
        int currentWidth = firstLineWidth;

        for (TextSegment segment : segments) {
            if (segment.iconId == TextSegment.LINE_BREAK) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine);
                    currentLine = new ArrayList<>();
                }
                currentWidth = subsequentLineWidth;
            } else if (segment.iconId >= 0 || segment.iconId == TextSegment.SPACER) {
                if (segment.width <= currentWidth) {
                    currentLine.add(segment);
                    currentWidth -= segment.width;
                } else {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = subsequentLineWidth;
                    }
                    currentLine.add(segment);
                    currentWidth -= segment.width;
                }
            } else {
                if (segment.text.trim().isEmpty()) {
                    int whitespaceWidth = metrics.stringWidth(segment.text);
                    if (whitespaceWidth <= currentWidth) {
                        currentLine.add(segment);
                        currentWidth -= whitespaceWidth;
                    } else if (!currentLine.isEmpty()) {
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = subsequentLineWidth;
                        currentLine.add(segment);
                        currentWidth -= whitespaceWidth;
                    }
                    continue;
                }
                String[] words = segment.text.split(" ", -1);
                for (int wi = 0; wi < words.length; wi++) {
                    String word = words[wi];
                    if (word.isEmpty() && wi < words.length - 1) {
                        int spaceWidth = metrics.stringWidth(" ");
                        if (spaceWidth <= currentWidth) {
                            currentLine.add(segment.copy(" ", spaceWidth, segment.color));
                            currentWidth -= spaceWidth;
                        }
                        continue;
                    }

                    int wordWidth = metrics.stringWidth(word);
                    int spaceWidth = metrics.stringWidth(" ");
                    boolean needsSpace = !currentLine.isEmpty() && wi > 0;
                    int neededWidth = wordWidth + (needsSpace ? spaceWidth : 0);

                    if (neededWidth <= currentWidth) {
                        if (needsSpace) {
                            currentLine.add(segment.copy(" ", spaceWidth, segment.color));
                            currentWidth -= spaceWidth;
                        }
                        currentLine.add(segment.copy(word, wordWidth, segment.color));
                        currentWidth -= wordWidth;
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentWidth = subsequentLineWidth;
                        }
                        currentLine.add(segment.copy(word, wordWidth, segment.color));
                        currentWidth -= wordWidth;
                    }
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        return lines;
    }
}
