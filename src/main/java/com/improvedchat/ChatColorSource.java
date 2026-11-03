package com.improvedchat;

public enum ChatColorSource {
    AUTOMATIC("RuneLite (automatic)"),
    TRANSPARENT("RuneLite transparent"),
    OPAQUE("RuneLite opaque"),
    WIDGET("Overlay palette");

    private final String label;

    ChatColorSource(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
