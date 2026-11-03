package com.improvedchat.overlay;

import java.awt.Color;

public final class OverlayStyleEngine {
    private OverlayStyleEngine() {
    }

    public static Color resolveTextColor(OverlayConfig config, Color inherited) {
        if (config != null && config.isOverlayColorOverrideEnabled()) {
            return config.getOverlayTextColor();
        }
        return inherited == null ? Color.WHITE : inherited;
    }
}
