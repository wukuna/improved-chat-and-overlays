package com.improvedchat.overlay;

import java.awt.Color;

public final class TextSegment {
    public static final int LINE_BREAK = -2;
    public static final int SPACER = -3;

    public com.improvedchat.model.AnimatedTextStyle textStyle;
    public boolean timestamp;
    public final String text;
    public final int iconId;
    public final int width;
    public final Color color;
    public final Color underline, strikethrough, shadow;

    public TextSegment(String text, int iconId, int width, Color color) {
        this(text, iconId, width, color, null, null, null);
    }

    public TextSegment(String text, int iconId, int width, Color color,
            Color underline, Color strikethrough, Color shadow) {
        this.underline = underline;
        this.strikethrough = strikethrough;
        this.shadow = shadow;
        this.text = text;
        this.iconId = iconId;
        this.width = width;
        this.color = color;
    }
    public TextSegment copy(String text, int width, Color color) {
        TextSegment result = new TextSegment(text, iconId, width, color, underline, strikethrough, shadow);
        result.textStyle = textStyle;
        result.timestamp = timestamp;
        return result;
    }
}
