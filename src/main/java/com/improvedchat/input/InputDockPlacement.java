package com.improvedchat.input;

public enum InputDockPlacement {
    FLOATING("Floating"), LEFT("Left side"), RIGHT("Right side"), BOTTOM("Bottom");

    private final String label;
    InputDockPlacement(String label) { this.label = label; }
    public boolean isVertical() { return this == LEFT || this == RIGHT; }
    @Override public String toString() { return label; }
}
