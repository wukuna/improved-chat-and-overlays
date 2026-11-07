package com.improvedchat.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class OverlayLayoutCache {
    private final Map<Long, Entry> entries = new LinkedHashMap<Long, Entry>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
            return size() > 256;
        }
    };

    synchronized void clear() { entries.clear(); }

    synchronized List<RenderLine> layout(long sequence, Object key, int alpha,
            Supplier<List<RenderLine>> factory) {
        Entry entry = entries.get(sequence);
        if (entry == null || !Objects.equals(entry.key, key)) {
            List<RenderLine> lines = new ArrayList<>();
            for (RenderLine line : factory.get()) lines.add(new RenderLine(line.segments, 255));
            entry = new Entry(key, lines);
            entries.put(sequence, entry);
        }
        if (alpha == 255) return entry.lines;
        List<RenderLine> faded = new ArrayList<>(entry.lines.size());
        for (RenderLine line : entry.lines) faded.add(new RenderLine(line.segments, alpha));
        return faded;
    }

    private static final class Entry {
        private final Object key;
        private final List<RenderLine> lines;
        private Entry(Object key, List<RenderLine> lines) { this.key = key; this.lines = lines; }
    }
}
