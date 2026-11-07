package com.improvedchat.overlay;

import com.improvedchat.model.OverlayMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.runelite.api.ChatMessageType;

public final class OverlayHistory {
    private OverlayHistory() { }

    public static List<OverlayMessage> select(List<OverlayMessage> history, long clearedThrough,
            int limit, boolean collapse) {
        return select(history, clearedThrough, limit, collapse, message -> true);
    }

    public static List<OverlayMessage> select(List<OverlayMessage> history, long clearedThrough,
            int limit, boolean collapse, java.util.function.Predicate<OverlayMessage> include) {
        List<OverlayMessage> result = new ArrayList<>();
        if (limit <= 0) return result;
        for (int i = history.size() - 1; i >= 0; i--) {
            OverlayMessage message = history.get(i);
            if (message.getSequence() <= clearedThrough || !include.test(message)) continue;
            if (collapse && !result.isEmpty()) {
                OverlayMessage newer = result.get(result.size() - 1);
                if (sameMessage(newer, message)) {
                    result.set(result.size() - 1, newer.groupedWith(message));
                    continue;
                }
            }
            if (result.size() == limit) break;
            result.add(message);
            if (!collapse && result.size() == limit) break;
        }
        java.util.Collections.reverse(result);
        return result;
    }

    private static boolean sameMessage(OverlayMessage a, OverlayMessage b) {
        return a.getType() != ChatMessageType.LOGINLOGOUTNOTIFICATION
                && a.getType() == b.getType()
                && a.isOutgoing() == b.isOutgoing()
                && Objects.equals(a.getSender(), b.getSender())
                && Objects.equals(a.getChannelName(), b.getChannelName())
                && a.getComparisonText().equals(b.getComparisonText());
    }

}
