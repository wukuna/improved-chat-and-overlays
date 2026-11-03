package com.improvedchat.model;

public enum TimestampStyle {
    CLOCK_TIME("Clock time"),
    TIME_AGO("Time ago (20m)");

    private final String label;
    TimestampStyle(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
