package com.improvedchat.overlay;

import java.util.Locale;
import java.util.UUID;

public final class OverlayTextReplacement {
    private String id = UUID.randomUUID().toString();
    private String original = "";
    private String replacement = "";
    private boolean enabled = true;

    public OverlayTextReplacement() { }
    public OverlayTextReplacement(String original, String replacement, boolean enabled) {
        this.original = original;
        this.replacement = replacement;
        this.enabled = enabled;
    }
    public String getId() {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        return id;
    }
    public String getOriginal() { return original == null ? "" : original; }
    public String getReplacement() { return replacement == null ? "" : replacement; }
    public boolean isEnabled() { return enabled; }
    public boolean isComplete() { return !getOriginal().trim().isEmpty() && !getReplacement().trim().isEmpty(); }
    public boolean matches(String plainMessage) {
        return enabled && isComplete() && normalize(getOriginal()).equals(normalize(plainMessage));
    }
    private static String normalize(String text) {
        return text.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
    }
    private OverlayTextReplacement copy(String original, String replacement, boolean enabled) {
        OverlayTextReplacement result = new OverlayTextReplacement(original, replacement, enabled);
        result.id = getId();
        return result;
    }
    public OverlayTextReplacement withOriginal(String value) { return copy(value, getReplacement(), enabled); }
    public OverlayTextReplacement withReplacement(String value) { return copy(getOriginal(), value, enabled); }
    public OverlayTextReplacement withEnabled(boolean value) { return copy(getOriginal(), getReplacement(), value); }
}
