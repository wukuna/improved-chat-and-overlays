package com.improvedchat.model;

public enum AnimatedTextStyle {
    NORMAL("Static color"), DUO_CYCLE("Duo Cycle"), DUO_WAVE("Duo Wave"),
    RGB_CYCLE("RGB cycle"), RGB_WAVE("RGB wave"), RGB_STATIC("RGB Static");

    private final String label;

    AnimatedTextStyle(String label) { this.label = label; }

    @Override public String toString() { return label; }
}
