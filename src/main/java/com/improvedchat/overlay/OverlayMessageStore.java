package com.improvedchat.overlay;

import com.improvedchat.model.OverlayMessage;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public final class OverlayMessageStore extends AbstractList<OverlayMessage> {
    private final List<OverlayMessage> messages = new ArrayList<>();
    private List<OverlayMessage> snapshot;

    public synchronized List<OverlayMessage> snapshot() {
        if (snapshot == null) snapshot = Collections.unmodifiableList(new ArrayList<>(messages));
        return snapshot;
    }

    @Override public synchronized int size() { return messages.size(); }
    @Override public synchronized OverlayMessage get(int index) { return messages.get(index); }
    @Override public synchronized int indexOf(Object value) { return messages.indexOf(value); }
    @Override public Iterator<OverlayMessage> iterator() { return snapshot().iterator(); }

    @Override public synchronized void add(int index, OverlayMessage message) {
        messages.add(index, message);
        changed();
    }

    @Override public synchronized boolean add(OverlayMessage message) {
        messages.add(message);
        changed();
        return true;
    }

    @Override public synchronized OverlayMessage set(int index, OverlayMessage message) {
        OverlayMessage previous = messages.set(index, message);
        changed();
        return previous;
    }

    @Override public synchronized OverlayMessage remove(int index) {
        OverlayMessage previous = messages.remove(index);
        changed();
        return previous;
    }

    @Override public synchronized boolean removeIf(Predicate<? super OverlayMessage> filter) {
        boolean removed = messages.removeIf(filter);
        if (removed) changed();
        return removed;
    }

    @Override public synchronized void clear() {
        messages.clear();
        changed();
    }

    private void changed() { snapshot = null; modCount++; }
}
