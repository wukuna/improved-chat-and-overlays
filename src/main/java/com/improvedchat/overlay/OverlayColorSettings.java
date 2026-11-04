package com.improvedchat.overlay;

import com.improvedchat.model.AnimatedTextStyle;
import java.awt.Color;

public final class OverlayColorSettings {
    private AnimatedTextStyle mode = AnimatedTextStyle.NORMAL;
    private Color primary = Color.WHITE;
    private Color secondary = new Color(106, 203, 255);
    private int cycleSeconds = 8;
    private boolean pulse;
    private int pulseSeconds = 4;

    public AnimatedTextStyle getMode() { return mode == null ? AnimatedTextStyle.NORMAL : mode; }
    public void setMode(AnimatedTextStyle value) { mode = value; }
    public Color getPrimary() { return primary == null ? Color.WHITE : primary; }
    public void setPrimary(Color value) { primary = value; }
    public Color getSecondary() { return secondary == null ? new Color(106, 203, 255) : secondary; }
    public void setSecondary(Color value) { secondary = value; }
    public int getCycleSeconds() { return Math.max(2, Math.min(30, cycleSeconds)); }
    public void setCycleSeconds(int value) { cycleSeconds = Math.max(2, Math.min(30, value)); }
    public boolean isPulse() { return pulse; }
    public void setPulse(boolean value) { pulse = value; }
    public int getPulseSeconds() { return Math.max(1, Math.min(60, pulseSeconds)); }
    public void setPulseSeconds(int value) { pulseSeconds = Math.max(1, Math.min(60, value)); }

    public boolean isDuo() {
        return getMode() == AnimatedTextStyle.DUO_CYCLE || getMode() == AnimatedTextStyle.DUO_WAVE;
    }

    public boolean isAnimated() {
        return getMode() != AnimatedTextStyle.NORMAL && getMode() != AnimatedTextStyle.RGB_STATIC;
    }

    public int pulseAlpha(int alpha, long now) {
        int bounded = Math.max(0, Math.min(255, alpha));
        if (!pulse) return bounded;
        long period = getPulseSeconds() * 1000L;
        double phase = Math.floorMod(now, period) / (double) period;
        return (int) Math.round(bounded * (1 + Math.cos(phase * 2 * Math.PI)) / 2);
    }
}
