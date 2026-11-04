package com.improvedchat.overlay;

import com.improvedchat.model.AttentionSpeed;
import com.improvedchat.model.AnimatedTextStyle;
import com.improvedchat.model.AttentionStopMode;
import com.improvedchat.model.AttentionTrigger;
import com.improvedchat.model.BorderThickness;
import com.improvedchat.model.FontSize;
import com.improvedchat.model.MessageCategory;
import com.improvedchat.model.PlacementMode;
import com.improvedchat.model.TextAlignment;
import com.improvedchat.model.TimestampStyle;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.Notification;

public final class OverlayConfig {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private String id = UUID.randomUUID().toString();
    private String name = "Custom Overlay";
    private Set<ChatMessageType> messageTypes = EnumSet.noneOf(ChatMessageType.class);
    private boolean useGlobalChatFilter;
    private OverlayFilterMode filterMode = OverlayFilterMode.OFF;
    private String filteredWords = "";
    private String filteredRegex = "";
    private String filteredNames = "";
    private boolean messageReplacerEnabled;
    private volatile java.util.List<OverlayTextReplacement> textReplacements = java.util.Collections.emptyList();

    private boolean showClanRankIcons = true;
    private Boolean showChannelNames;
    private boolean showOfflineStatus;
    private boolean showOfflineIcon = true;
    private boolean colorOfflineNames = true;
    private Color offlineColor = Color.DARK_GRAY;

    private int maxMessages = 10;
    private boolean scrollHistory;
    private int historyLimit = 1000;
    private int fadeOutDuration;
    private int widgetWidth = 512;

    private int paddingHorizontal = 4;
    private int paddingVertical = 3;
    private int offsetX;
    private int offsetY;
    private PlacementMode placementMode = PlacementMode.FREE;
    private TextAlignment textAlignment;

    private boolean overlayColorOverrideEnabled;
    private Color overlayTextColor = Color.WHITE;

    private boolean dynamicHeight;
    private boolean hideDuplicateCount;
    private boolean contextualColours = true;
    private boolean showTimestamp;
    private TimestampStyle timestampStyle = TimestampStyle.CLOCK_TIME;
    private boolean showInputPreview;
    private boolean previewOnlyWhenTyping;
    private boolean show = true;
    private int toggleKeyCode;
    private int toggleKeyModifiers;
    private boolean alwaysVisible;
    private FontSize fontSize = FontSize.REGULAR;
    private String fontFamily = "RuneScape";
    private boolean boldText;
    private Color backgroundColour = TRANSPARENT;
    private String backgroundImage;
    private String backgroundImageLabel;

    private boolean borderEnabled;
    private Color borderColour = Color.WHITE;
    private BorderThickness borderThickness = BorderThickness.NORMAL;
    private boolean rgbBorder;
    private boolean roundedCorners;
    private AnimatedTextStyle animatedTextStyle = AnimatedTextStyle.NORMAL;
    private int rgbCycleSeconds = 8;
    private boolean customRgbCycle;
    private Color rgbColor1 = new Color(255, 102, 122);
    private Color rgbColor2 = new Color(106, 203, 255);
    private OverlayTextSettings textSettings;
    private OverlayColorSettings borderColors;
    private OverlayColorSettings textColors;
    private OverlayColorSettings timestampColors;
    private Boolean timestampColorOverride;

    private volatile Notification notification = Notification.OFF;
    private AttentionTrigger notificationTrigger = AttentionTrigger.ALL_NEW_MESSAGES;

    private boolean flashEnabled;
    private AttentionTrigger attentionTrigger = AttentionTrigger.FLASH_RULES_ONLY;
    private boolean flashWhileFocused;
    private AttentionStopMode attentionStopMode = AttentionStopMode.TIMER_OR_REFOCUS;
    private int flashDurationSeconds = 5;
    private AttentionSpeed attentionSpeed = AttentionSpeed.NORMAL;
    private boolean flashMessage = true;
    private boolean flashBorder;
    private boolean flashBackground;

    public OverlayConfig() {
    }

    public static OverlayConfig createDefault(String name, Set<ChatMessageType> types, boolean alwaysVisible) {
        OverlayConfig config = new OverlayConfig();
        config.name = name;
        config.messageTypes = types == null || types.isEmpty()
                ? EnumSet.noneOf(ChatMessageType.class)
                : EnumSet.copyOf(types);
        config.alwaysVisible = alwaysVisible;
        return config;
    }

    public static OverlayConfig defaultAllOverlay() {
        OverlayConfig config = preset("Game Alerts", "game");
        config.showInputPreview = true;
        config.dynamicHeight = true;
        return config;
    }

    public static OverlayConfig defaultPrivateOverlay() {
        EnumSet<ChatMessageType> types = EnumSet.noneOf(ChatMessageType.class);
        types.addAll(MessageCategory.PRIVATE.getTypes());
        OverlayConfig config = createDefault("Private Chat", types, true);
        config.contextualColours = true;
        return config;
    }

    public static OverlayConfig preset(String name, String template) {
        EnumSet<ChatMessageType> types = EnumSet.noneOf(ChatMessageType.class);
        if ("game".equals(template)) {
            types.addAll(MessageCategory.GAME.getTypes());
            types.addAll(MessageCategory.GAME_CLAN.getTypes());
        } else if ("private".equals(template)) {
            types.addAll(MessageCategory.PRIVATE.getTypes());
        } else if ("clan".equals(template)) {
            types.addAll(MessageCategory.CLAN_CHAT.getTypes());
            types.addAll(MessageCategory.GUEST_CLAN_CHAT.getTypes());
            types.addAll(MessageCategory.GIM_CLAN_CHAT.getTypes());
        } else if ("all".equals(template)) {
            for (MessageCategory category : MessageCategory.values()) types.addAll(category.getTypes());
        }
        OverlayConfig config = createDefault(name, types, "private".equals(template));
        config.setDynamicHeight("game".equals(template));
        return config;
    }

    public void renewId() { id = UUID.randomUUID().toString(); }

    public void normalize() {
        setNotification(notification);
        setName(name);
        setMaxMessages(maxMessages);
        setHistoryLimit(historyLimit);
        setFadeOutDuration(fadeOutDuration);
        setWidgetWidth(widgetWidth);
        setPaddingHorizontal(paddingHorizontal);
        setPaddingVertical(paddingVertical);
        setOffsetX(offsetX);
        setOffsetY(offsetY);
        getMessageTypes().remove(null);
        initializeColors();
    }

    public String getId() {
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public String getName() { return name; }
    public void setName(String value) {
        name = value == null || value.trim().isEmpty() ? "Untitled Overlay" : value.trim();
    }

    public Set<ChatMessageType> getMessageTypes() {
        if (messageTypes == null) {
            messageTypes = EnumSet.noneOf(ChatMessageType.class);
        }
        return messageTypes;
    }

    public void setMessageTypes(Set<ChatMessageType> value) {
        messageTypes = value == null || value.isEmpty()
                ? EnumSet.noneOf(ChatMessageType.class)
                : EnumSet.copyOf(value);
    }

    public boolean isUseGlobalChatFilter() { return useGlobalChatFilter; }
    public boolean isMessageReplacerEnabled() { return messageReplacerEnabled; }
    public void setMessageReplacerEnabled(boolean value) { messageReplacerEnabled = value; }
    public java.util.List<OverlayTextReplacement> getTextReplacements() {
        java.util.List<OverlayTextReplacement> current = textReplacements;
        return current == null ? java.util.Collections.emptyList() : java.util.Collections.unmodifiableList(current);
    }
    public void setTextReplacements(java.util.List<OverlayTextReplacement> value) {
        java.util.List<OverlayTextReplacement> copy = new java.util.ArrayList<>();
        if (value != null) for (OverlayTextReplacement rule : value) if (rule != null) copy.add(rule);
        textReplacements = copy;
    }
    public void setUseGlobalChatFilter(boolean value) { useGlobalChatFilter = value; }

    public OverlayFilterMode getFilterMode() {
        return filterMode == null ? OverlayFilterMode.OFF : filterMode;
    }
    public void setFilterMode(OverlayFilterMode value) {
        filterMode = value == null ? OverlayFilterMode.OFF : value;
    }

    public String getFilteredWords() { return filteredWords == null ? "" : filteredWords; }
    public void setFilteredWords(String value) { filteredWords = value == null ? "" : value; }

    public String getFilteredRegex() { return filteredRegex == null ? "" : filteredRegex; }
    public void setFilteredRegex(String value) { filteredRegex = value == null ? "" : value; }

    public String getFilteredNames() { return filteredNames == null ? "" : filteredNames; }
    public void setFilteredNames(String value) { filteredNames = value == null ? "" : value; }

    public boolean isShowClanRankIcons() { return showClanRankIcons; }
    public void setShowClanRankIcons(boolean value) { showClanRankIcons = value; }

    public boolean isShowChannelNames(boolean globalDefault) { return showChannelNames == null ? globalDefault : showChannelNames; }
    public void setShowChannelNames(boolean value) { showChannelNames = value; }

    public boolean isShowOfflineStatus() { return showOfflineStatus; }
    public void setShowOfflineStatus(boolean value) { showOfflineStatus = value; }

    public boolean isShowOfflineIcon() { return showOfflineIcon; }
    public void setShowOfflineIcon(boolean value) { showOfflineIcon = value; }

    public boolean isColorOfflineNames() { return colorOfflineNames; }
    public void setColorOfflineNames(boolean value) { colorOfflineNames = value; }

    public Color getOfflineColor() { return offlineColor == null ? Color.DARK_GRAY : offlineColor; }
    public void setOfflineColor(Color value) { offlineColor = value == null ? Color.DARK_GRAY : value; }

    public boolean isScrollHistory() { return scrollHistory; }
    public void setScrollHistory(boolean value) { scrollHistory = value; }
    public int getHistoryLimit() { return clamp(historyLimit, 200, 5000); }
    public void setHistoryLimit(int value) { historyLimit = clamp(value, 200, 5000); }

    public int getMaxMessages() { return maxMessages; }
    public void setMaxMessages(int value) { maxMessages = clamp(value, 1, 20); }

    public int getFadeOutDuration() { return fadeOutDuration; }
    public void setFadeOutDuration(int value) { fadeOutDuration = clamp(value, 0, 300); }

    public int getWidgetWidth() { return widgetWidth; }
    public void setWidgetWidth(int value) { widgetWidth = clamp(value, 150, 1024); }

    public int getPaddingHorizontal() { return paddingHorizontal; }
    public void setPaddingHorizontal(int value) { paddingHorizontal = clamp(value, 0, 50); }

    public int getPaddingVertical() { return paddingVertical; }
    public void setPaddingVertical(int value) { paddingVertical = clamp(value, 0, 50); }

    public int getOffsetX() { return offsetX; }
    public void setOffsetX(int value) { offsetX = clamp(value, -500, 500); }

    public int getOffsetY() { return offsetY; }
    public void setOffsetY(int value) { offsetY = clamp(value, -500, 500); }

    public PlacementMode getPlacementMode() {
        return placementMode == null ? PlacementMode.FREE : placementMode;
    }

    public void setPlacementMode(PlacementMode mode) {
        placementMode = mode == null ? PlacementMode.FREE : mode;
    }

    public TextAlignment getTextAlignment() {
        if (textAlignment != null) {
            return textAlignment;
        }
        return getPlacementMode() == PlacementMode.FREE ? TextAlignment.LEFT : TextAlignment.CENTER;
    }

    public void setTextAlignment(TextAlignment value) {
        textAlignment = value;
    }

    public boolean isOverlayColorOverrideEnabled() { return overlayColorOverrideEnabled; }
    public void setOverlayColorOverrideEnabled(boolean enabled) { overlayColorOverrideEnabled = enabled; }

    public Color getOverlayTextColor() { return textColors == null ? legacyTextColor() : textColors.getPrimary(); }
    private Color legacyTextColor() { return overlayTextColor == null ? Color.WHITE : overlayTextColor; }
    public void setOverlayTextColor(Color color) {
        overlayTextColor = color == null ? Color.WHITE : color;
        if (textColors != null) textColors.setPrimary(overlayTextColor);
    }

    public boolean isDynamicHeight() { return dynamicHeight; }
    public void setDynamicHeight(boolean value) { dynamicHeight = value; }

    public boolean isHideDuplicateCount() { return hideDuplicateCount; }
    public void setHideDuplicateCount(boolean value) { hideDuplicateCount = value; }

    public boolean isContextualColours() { return !overlayColorOverrideEnabled && contextualColours; }
    public boolean isRetainMessageColors() { return contextualColours; }
    public void setContextualColours(boolean value) { contextualColours = value; }

    public boolean isShowTimestamp() { return showTimestamp; }
    public void setShowTimestamp(boolean value) { showTimestamp = value; }
    public TimestampStyle getTimestampStyle() { return timestampStyle == null ? TimestampStyle.CLOCK_TIME : timestampStyle; }
    public void setTimestampStyle(TimestampStyle value) { timestampStyle = value == null ? TimestampStyle.CLOCK_TIME : value; }

    public boolean isShowInputPreview() { return showInputPreview; }
    public void setShowInputPreview(boolean value) { showInputPreview = value; }

    public boolean isPreviewOnlyWhenTyping() { return previewOnlyWhenTyping; }
    public void setPreviewOnlyWhenTyping(boolean value) { previewOnlyWhenTyping = value; }

    public net.runelite.client.config.Keybind getToggleKeybind() {
        return toggleKeyCode <= 0 ? net.runelite.client.config.Keybind.NOT_SET
            : new net.runelite.client.config.Keybind(toggleKeyCode, toggleKeyModifiers);
    }
    public void setToggleKeybind(net.runelite.client.config.Keybind key) {
        toggleKeyCode = key == null ? 0 : key.getKeyCode();
        toggleKeyModifiers = key == null ? 0 : key.getModifiers();
    }
    public boolean isShow() { return show; }
    public void setShow(boolean value) { show = value; }

    public boolean isAlwaysVisible() { return alwaysVisible; }
    public void setAlwaysVisible(boolean value) { alwaysVisible = value; }

    public FontSize getFontSize() { return fontSize == null ? FontSize.REGULAR : fontSize; }
    public void setFontSize(FontSize value) { fontSize = value == null ? FontSize.REGULAR : value; }

    public String getFontFamily() {
        return fontFamily == null || fontFamily.trim().isEmpty() ? "RuneScape" : fontFamily;
    }

    public void setFontFamily(String value) {
        fontFamily = value == null || value.trim().isEmpty() ? "RuneScape" : value.trim();
    }

    public boolean isBoldText() { return boldText; }
    public void setBoldText(boolean value) { boldText = value; }

    public Color getBackgroundColour() { return backgroundColour == null ? TRANSPARENT : backgroundColour; }
    public void setBackgroundColour(Color value) { backgroundColour = value == null ? TRANSPARENT : value; }
    public String getBackgroundImage() { return OverlayBackgroundImages.validName(backgroundImage) ? backgroundImage : ""; }
    public String getBackgroundImageLabel() {
        return backgroundImageLabel == null ? "Background image" : backgroundImageLabel.substring(0, Math.min(80, backgroundImageLabel.length()));
    }
    public void setBackgroundImage(String name, String label) {
        backgroundImage = OverlayBackgroundImages.validName(name) ? name : null;
        backgroundImageLabel = label == null ? null : label.substring(0, Math.min(80, label.length()));
    }

    public boolean isBackgroundEnabled() { return getBackgroundColour().getAlpha() > 0; }

    public void setBackgroundEnabled(boolean enabled) {
        Color current = getBackgroundColour();
        if (enabled && current.getAlpha() == 0) {
            backgroundColour = new Color(current.getRed(), current.getGreen(), current.getBlue(), 150);
        } else if (!enabled && current.getAlpha() > 0) {
            backgroundColour = new Color(current.getRed(), current.getGreen(), current.getBlue(), 0);
        }
    }

    public Color getRenderBackgroundColour() {
        return AttentionEngine.adjustBackground(this, getBackgroundColour(), System.currentTimeMillis());
    }

    public boolean isBorderEnabled() { return borderEnabled; }
    public void setBorderEnabled(boolean value) { borderEnabled = value; }

    public Color getBorderColour() {
        return borderColors == null ? (borderColour == null ? Color.WHITE : borderColour) : borderColors.getPrimary();
    }

    private synchronized void initializeColors() {
        AnimatedTextStyle legacy = animatedTextStyle == null ? AnimatedTextStyle.NORMAL : animatedTextStyle;
        AnimatedTextStyle mode = legacy == AnimatedTextStyle.RGB_CYCLE && customRgbCycle ? AnimatedTextStyle.DUO_CYCLE : legacy;
        if (borderColors == null) borderColors = colors(rgbBorder ? AnimatedTextStyle.RGB_WAVE : AnimatedTextStyle.NORMAL, getBorderColour());
        if (textColors == null) textColors = colors(mode, mode == AnimatedTextStyle.DUO_CYCLE ? getRgbColor1() : legacyTextColor());
        if (timestampColors == null) timestampColors = colors(mode, mode == AnimatedTextStyle.DUO_CYCLE ? getRgbColor1()
                : overlayColorOverrideEnabled ? legacyTextColor() : Color.WHITE);
        if (timestampColorOverride == null) timestampColorOverride = overlayColorOverrideEnabled || contextualColours;
    }

    private OverlayColorSettings colors(AnimatedTextStyle mode, Color primary) {
        OverlayColorSettings result = new OverlayColorSettings();
        result.setMode(mode);
        result.setPrimary(primary);
        result.setSecondary(getRgbColor2());
        result.setCycleSeconds(getRgbCycleSeconds());
        return result;
    }

    public OverlayColorSettings getBorderColors() { initializeColors(); return borderColors; }
    public OverlayColorSettings getTextColors() { initializeColors(); return textColors; }
    public OverlayColorSettings getTimestampColors() { initializeColors(); return timestampColors; }
    public boolean isTimestampColorOverride() { initializeColors(); return Boolean.TRUE.equals(timestampColorOverride); }
    public void setTimestampColorOverride(boolean value) { timestampColorOverride = value; }
    public boolean migrateTextSettings(com.improvedchat.ImprovedChatConfig legacy) {
        if (textSettings != null) return false;
        textSettings = OverlayTextSettings.copyOf(legacy);
        return true;
    }
    public boolean hasTextSettings() { return textSettings != null; }
    public OverlayTextSettings getTextSettings() {
        if (textSettings == null) textSettings = new OverlayTextSettings();
        return textSettings;
    }
    public boolean isCustomRgbCycle() { return customRgbCycle; }
    public void clearLegacyHighlightCycle() { customRgbCycle = false; }
    public void setCustomRgbCycle(boolean value) {
        customRgbCycle = value;
        if (textColors != null && (textColors.getMode() == AnimatedTextStyle.RGB_CYCLE || textColors.getMode() == AnimatedTextStyle.DUO_CYCLE)) {
            textColors.setMode(value ? AnimatedTextStyle.DUO_CYCLE : AnimatedTextStyle.RGB_CYCLE);
            if (value) textColors.setPrimary(getRgbColor1());
        }
    }
    public Color getRgbColor1() { return rgbColor1 == null ? new Color(255,102,122) : rgbColor1; }
    public Color getRgbColor2() { return rgbColor2 == null ? new Color(106,203,255) : rgbColor2; }
    public void setRgbColor1(Color value) { rgbColor1 = value; }
    public void setRgbColor2(Color value) { rgbColor2 = value; }
    public boolean isRoundedCorners() { return roundedCorners; }
    public void setRoundedCorners(boolean value) { roundedCorners = value; }
    public boolean isRgbBorder() { return borderColors == null ? rgbBorder : borderColors.getMode() == AnimatedTextStyle.RGB_WAVE; }
    public void setRgbBorder(boolean value) {
        rgbBorder = value;
        if (borderColors != null) borderColors.setMode(value ? AnimatedTextStyle.RGB_WAVE : AnimatedTextStyle.NORMAL);
    }
    public AnimatedTextStyle getAnimatedTextStyle() {
        return textColors == null ? (animatedTextStyle == null ? AnimatedTextStyle.NORMAL : animatedTextStyle) : textColors.getMode();
    }
    public void setAnimatedTextStyle(AnimatedTextStyle value) {
        animatedTextStyle = value;
        if (textColors != null) textColors.setMode(value == AnimatedTextStyle.RGB_CYCLE && customRgbCycle ? AnimatedTextStyle.DUO_CYCLE : value);
    }
    public int getRgbCycleSeconds() { return Math.max(2, Math.min(30, rgbCycleSeconds)); }
    public void setRgbCycleSeconds(int value) {
        rgbCycleSeconds = Math.max(2, Math.min(30, value));
        if (borderColors != null) borderColors.setCycleSeconds(value);
        if (textColors != null) textColors.setCycleSeconds(value);
        if (timestampColors != null) timestampColors.setCycleSeconds(value);
    }
    public void setBorderColour(Color value) {
        borderColour = value == null ? Color.WHITE : value;
        if (borderColors != null) borderColors.setPrimary(borderColour);
    }

    public BorderThickness getBorderThickness() {
        return borderThickness == null ? BorderThickness.NORMAL : borderThickness;
    }
    public void setBorderThickness(BorderThickness value) {
        borderThickness = value == null ? BorderThickness.NORMAL : value;
    }

    public boolean isFlashEnabled() { return flashEnabled; }
    public void setFlashEnabled(boolean value) { flashEnabled = value; }

    public AttentionTrigger getAttentionTrigger() {
        return attentionTrigger == null ? AttentionTrigger.FLASH_RULES_ONLY : attentionTrigger;
    }
    public void setAttentionTrigger(AttentionTrigger value) {
        attentionTrigger = value == null ? AttentionTrigger.FLASH_RULES_ONLY : value;
    }

    public boolean isFlashWhileFocused() { return flashWhileFocused; }
    public void setFlashWhileFocused(boolean value) { flashWhileFocused = value; }

    public AttentionStopMode getAttentionStopMode() {
        return attentionStopMode == null ? AttentionStopMode.TIMER_OR_REFOCUS : attentionStopMode;
    }
    public void setAttentionStopMode(AttentionStopMode value) {
        attentionStopMode = value == null ? AttentionStopMode.TIMER_OR_REFOCUS : value;
    }

    public int getFlashDurationSeconds() { return clamp(flashDurationSeconds, 1, 60); }
    public void setFlashDurationSeconds(int value) { flashDurationSeconds = clamp(value, 1, 60); }

    public AttentionSpeed getAttentionSpeed() {
        return attentionSpeed == null ? AttentionSpeed.NORMAL : attentionSpeed;
    }
    public void setAttentionSpeed(AttentionSpeed value) {
        attentionSpeed = value == null ? AttentionSpeed.NORMAL : value;
    }

    public boolean isFlashMessage() { return flashMessage; }
    public void setFlashMessage(boolean value) { flashMessage = value; }

    public boolean isFlashBorder() { return flashBorder; }
    public void setFlashBorder(boolean value) { flashBorder = value; }

    public boolean isFlashBackground() { return flashBackground; }
    public void setFlashBackground(boolean value) { flashBackground = value; }

    public Notification getNotification() { return notification == null ? Notification.OFF : notification; }
    public void setNotification(Notification value) { notification = OverlayNotifications.normalize(value); }
    public AttentionTrigger getNotificationTrigger() {
        return notificationTrigger == null ? AttentionTrigger.ALL_NEW_MESSAGES : notificationTrigger;
    }
    public void setNotificationTrigger(AttentionTrigger value) {
        notificationTrigger = value == null ? AttentionTrigger.ALL_NEW_MESSAGES : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
