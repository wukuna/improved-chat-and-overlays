package com.improvedchat.overlay;

import com.improvedchat.model.OverlayMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.runelite.api.ChatMessageType;

public final class OverlayHistory {
    private static final java.util.regex.Pattern COLOR_TAG = java.util.regex.Pattern.compile("</?col[^>]*>");
    private OverlayHistory() { }

    public static List<OverlayMessage> select(List<OverlayMessage> history, long clearedThrough,
            int limit, boolean collapse) {
        List<OverlayMessage> result = new ArrayList<>();
        for (OverlayMessage message : history) {
            if (message.getSequence() <= clearedThrough) continue;
            if (collapse && !result.isEmpty()) {
                OverlayMessage previous = result.get(result.size() - 1);
                if (sameMessage(previous, message)) {
                    result.set(result.size() - 1, message.groupedWith(previous));
                    continue;
                }
            }
            result.add(message);
        }
        return new ArrayList<>(result.subList(Math.max(0, result.size() - limit), result.size()));
    }

    private static boolean sameMessage(OverlayMessage a, OverlayMessage b) {
        return a.getType() != ChatMessageType.LOGINLOGOUTNOTIFICATION
                && a.getType() == b.getType()
                && a.isOutgoing() == b.isOutgoing()
                && Objects.equals(a.getSender(), b.getSender())
                && Objects.equals(a.getChannelName(), b.getChannelName())
                && stripColors(a.getMessage()).equals(stripColors(b.getMessage()));
    }

    private static String stripColors(String text) {
        return text == null ? "" : COLOR_TAG.matcher(text).replaceAll("");
    }
}
