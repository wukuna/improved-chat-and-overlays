package com.improvedchat.overlay;

import com.improvedchat.model.OverlayMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class OverlayScrollback {
    private long anchor;
    private int anchorLine;
    private int pendingLines;
    private int newerMessages;

    public synchronized void scroll(int lines) {
        pendingLines = Math.max(-300, Math.min(300, pendingLines + lines));
    }

    public synchronized void latest() {
        anchor = 0;
        pendingLines = 0;
        newerMessages = 0;
    }

    public synchronized boolean isBrowsing() { return anchor != 0; }
    public synchronized int getNewerMessages() { return newerMessages; }

    public synchronized List<RenderLine> select(List<OverlayMessage> messages, int rows,
            Function<OverlayMessage, List<RenderLine>> layout) {
        if (messages.isEmpty()) {
            latest();
            return Collections.emptyList();
        }
        rows = Math.max(1, rows);
        Map<Integer, List<RenderLine>> cache = new HashMap<>();
        Function<Integer, List<RenderLine>> lines = index -> cache.computeIfAbsent(index, key -> {
            List<RenderLine> result = layout.apply(messages.get(key));
            return result.isEmpty() ? Collections.singletonList(new RenderLine(Collections.emptyList(), 255)) : result;
        });
        int last = messages.size() - 1;
        int index = last;
        if (anchor != 0) {
            index = 0;
            while (index < last && messages.get(index).getFirstSequence() < anchor) index++;
        }
        int line = anchor == 0 ? lines.apply(index).size() - 1
                : Math.min(anchorLine, lines.apply(index).size() - 1);
        int movement = pendingLines;
        pendingLines = 0;
        while (movement > 0 && (index > 0 || line > 0)) {
            if (line > 0) line--;
            else line = lines.apply(--index).size() - 1;
            movement--;
        }
        while (movement < 0 && (index < last || line < lines.apply(index).size() - 1)) {
            if (line + 1 < lines.apply(index).size()) line++;
            else { index++; line = 0; }
            movement++;
        }

        int firstIndex = index;
        int firstLine = line;
        int available = 1;
        while (available < rows && (firstIndex > 0 || firstLine > 0)) {
            if (firstLine > 0) firstLine--;
            else firstLine = lines.apply(--firstIndex).size() - 1;
            available++;
        }
        while (available < rows && (index < last || line + 1 < lines.apply(index).size())) {
            if (line + 1 < lines.apply(index).size()) line++;
            else { index++; line = 0; }
            available++;
        }
        boolean atLatest = index == last && line == lines.apply(index).size() - 1;
        anchor = atLatest ? 0 : messages.get(index).getFirstSequence();
        anchorLine = line;
        newerMessages = last - index;
        List<RenderLine> result = new ArrayList<>(rows);
        while (result.size() < rows) {
            result.add(lines.apply(index).get(line));
            if (result.size() == rows) break;
            if (line > 0) line--;
            else if (index > 0) line = lines.apply(--index).size() - 1;
            else break;
        }
        Collections.reverse(result);
        return result;
    }
}
