package com.improvedchat.overlay;

public enum OverlayFilterMode
{
    OFF("Off"),
    HIDE_MATCHES("Hide Matches"),
    SHOW_ONLY_MATCHES("Show Only Matches");

    private final String label;

    OverlayFilterMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
