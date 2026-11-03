package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ChatColorSource;
import com.improvedchat.RainbowStyle;
import com.improvedchat.model.AnimatedTextStyle;
import java.awt.Color;

public final class OverlayTextSettings implements ImprovedChatConfig {
    private static final Color[] DEFAULT_PALETTE = {new Color(239,16,32), new Color(255,149,0), new Color(255,214,10), new Color(52,199,89), new Color(50,215,255), new Color(10,132,255), new Color(94,92,230), new Color(191,90,242), Color.WHITE};
    private static final Color[] DEFAULT_CHANNEL_COLORS = {Color.WHITE, new Color(148,148,255), Color.CYAN, new Color(239,80,80), new Color(127,0,0), new Color(0,211,0), new Color(127,0,0), new Color(223,32,255), new Color(255,32,223), Color.YELLOW, Color.WHITE, new Color(64,64,255)};
    private boolean solidEnabled;
    private String solidRules = new ImprovedChatConfig() { }.messageColorRules();
    private boolean includePlayer;
    private Color[] palette;
    private boolean rainbowEnabled;
    private String rainbowRules = new ImprovedChatConfig() { }.overlayRainbowRules();
    private RainbowStyle spread = RainbowStyle.PER_WORD;
    private AnimatedTextStyle highlightStyle = AnimatedTextStyle.RGB_STATIC;
    private boolean highlightsOverrideDefaults = true;
    private ChatColorSource source = ChatColorSource.AUTOMATIC;
    private Color[] channelColors;

    public static OverlayTextSettings copyOf(ImprovedChatConfig old) {
        OverlayTextSettings result = new OverlayTextSettings();
        result.solidEnabled = old.enableMessageColorRules();
        result.solidRules = old.messageColorRules();
        result.includePlayer = old.includePlayerChatInColorRules();
        result.rainbowEnabled = old.enableOverlayRainbowRules();
        result.rainbowRules = old.overlayRainbowRules();
        result.spread = old.rainbowStyle();
        result.source = old.chatColorSource();

        result.highlightsOverrideDefaults = false;
        result.palette = new Color[] {old.messageRuleColor1(), old.messageRuleColor2(), old.messageRuleColor3(), old.messageRuleColor4(), old.messageRuleColor5(), old.messageRuleColor6(), old.messageRuleColor7(), old.messageRuleColor8(), old.messageRuleColor9()};
        result.channelColors = new Color[] {old.gameColour(), old.publicColour(), old.privateColour(), old.friendsColour(), old.clanColour(), old.guestClanColour(), old.gimClanColour(), old.tradeColour(), old.challengeColour(), old.didYouKnowColour(), old.broadcastColour(), old.autoColour()};
        return result;
    }
    public boolean enableMessageColorRules() { return solidEnabled; }
    public void setSolidEnabled(boolean value) { solidEnabled = value; }
    public String messageColorRules() { return solidRules == null ? "" : solidRules; }
    public void setSolidRules(String value) { solidRules = value; }
    public boolean includePlayerChatInColorRules() { return includePlayer; }
    public void setIncludePlayer(boolean value) { includePlayer = value; }
    public boolean enableOverlayRainbowRules() { return rainbowEnabled; }
    public void setRainbowEnabled(boolean value) { rainbowEnabled = value; }
    public String overlayRainbowRules() { return rainbowRules == null ? "" : rainbowRules; }
    public void setRainbowRules(String value) { rainbowRules = value; }
    public RainbowStyle rainbowStyle() { return spread == null ? RainbowStyle.PER_WORD : spread; }
    public void setSpread(RainbowStyle value) { spread = value; }
    public AnimatedTextStyle getHighlightStyle() { return highlightStyle == null ? AnimatedTextStyle.RGB_STATIC : highlightStyle; }
    public void setHighlightStyle(AnimatedTextStyle value) { highlightStyle = value; }
    public boolean isHighlightsOverrideDefaults() { return highlightsOverrideDefaults; }
    public void setHighlightsOverrideDefaults(boolean value) { highlightsOverrideDefaults = value; }
    public ChatColorSource chatColorSource() { return source == null ? ChatColorSource.AUTOMATIC : source; }
    public void setSource(ChatColorSource value) { source = value; }
    public Color getPalette(int index) {
        return palette != null && palette.length == 9 && palette[index] != null ? palette[index] : DEFAULT_PALETTE[index];
    }
    public void setPalette(int index, Color value) {
        Color[] next = new Color[9];
        for (int i=0;i<9;i++) next[i]=getPalette(i);
        next[index]=value; palette=next;
    }
    public Color getChannelColor(int index) {
        return channelColors != null && channelColors.length == 12 && channelColors[index] != null ? channelColors[index] : DEFAULT_CHANNEL_COLORS[index];
    }
    public void setChannelColor(int index, Color value) {
        Color[] next = new Color[12];
        for (int i=0;i<12;i++) next[i]=getChannelColor(i);
        next[index]=value; channelColors=next;
    }
    public Color messageRuleColor1() { return getPalette(0); }
    public Color messageRuleColor2() { return getPalette(1); }
    public Color messageRuleColor3() { return getPalette(2); }
    public Color messageRuleColor4() { return getPalette(3); }
    public Color messageRuleColor5() { return getPalette(4); }
    public Color messageRuleColor6() { return getPalette(5); }
    public Color messageRuleColor7() { return getPalette(6); }
    public Color messageRuleColor8() { return getPalette(7); }
    public Color messageRuleColor9() { return getPalette(8); }
    public Color gameColour() { return getChannelColor(0); }
    public Color publicColour() { return getChannelColor(1); }
    public Color privateColour() { return getChannelColor(2); }
    public Color friendsColour() { return getChannelColor(3); }
    public Color clanColour() { return getChannelColor(4); }
    public Color guestClanColour() { return getChannelColor(5); }
    public Color gimClanColour() { return getChannelColor(6); }
    public Color tradeColour() { return getChannelColor(7); }
    public Color challengeColour() { return getChannelColor(8); }
    public Color didYouKnowColour() { return getChannelColor(9); }
    public Color broadcastColour() { return getChannelColor(10); }
    public Color autoColour() { return getChannelColor(11); }
}
