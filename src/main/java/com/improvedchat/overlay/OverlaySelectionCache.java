package com.improvedchat.overlay;

import com.improvedchat.model.OverlayMessage;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class OverlaySelectionCache {
    private List<OverlayMessage> source;
    private Object key;
    private long createdAt;
    private long expiresAt;
    private List<OverlayMessage> selected;

    public synchronized List<OverlayMessage> get(List<OverlayMessage> source, Object key, long now) {
        return this.source == source && Objects.equals(this.key, key)
                && now >= createdAt && now < expiresAt ? selected : null;
    }

    public synchronized List<OverlayMessage> put(List<OverlayMessage> source, Object key,
            long now, long expiresAt, List<OverlayMessage> selected) {
        this.source = source;
        this.key = key;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.selected = Collections.unmodifiableList(selected);
        return this.selected;
    }
}
