package com.improvedchat.model;

public enum TextAlignment {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right");

    private final String label;

    TextAlignment(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
