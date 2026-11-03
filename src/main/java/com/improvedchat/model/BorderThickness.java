package com.improvedchat.model;

public enum BorderThickness {
    THIN("Thin", 1),
    NORMAL("Normal", 2),
    THICK("Thick", 3),
    EXTRA_THICK("Extra Thick", 4);

    private final String label;
    private final int pixels;

    BorderThickness(String label, int pixels) {
        this.label = label;
        this.pixels = pixels;
    }

    public int getPixels() { return pixels; }

    @Override
    public String toString() { return label; }
}
