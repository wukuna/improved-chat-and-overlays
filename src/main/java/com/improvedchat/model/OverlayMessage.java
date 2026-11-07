package com.improvedchat.model;

import net.runelite.api.ChatMessageType;
import java.util.concurrent.atomic.AtomicLong;

public final class OverlayMessage {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private final long sequence;
    private long firstSequence;
    private final String message;
    private String comparisonText;
    private static final java.util.regex.Pattern COLOR_TAG = java.util.regex.Pattern.compile("</?col[^>]*>");
    private final long timestamp;
    private final ChatMessageType type;
    private final boolean bossKc;
    private final String sender;
    private final String channelName;
    private final boolean outgoing;
    private final int maxFadeSeconds;
    private int count = 1;

    public static OverlayMessage gameMessage(String message, long timestamp, ChatMessageType type, boolean bossKc) {
        return new OverlayMessage(message, timestamp, type, bossKc, null, null, false, 0);
    }

    public static OverlayMessage senderMessage(String sender, String channelName, String message,
            long timestamp, ChatMessageType type, boolean outgoing) {
        return new OverlayMessage(message, timestamp, type, false, sender, channelName, outgoing, 0);
    }

    public static OverlayMessage loginNotification(String sender, String message, long timestamp, int maxFadeSeconds) {
        return new OverlayMessage(message, timestamp, ChatMessageType.LOGINLOGOUTNOTIFICATION,
                false, sender, null, false, maxFadeSeconds);
    }

    private OverlayMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            String sender, String channelName, boolean outgoing, int maxFadeSeconds) {
        this(message, timestamp, type, bossKc, sender, channelName, outgoing, maxFadeSeconds, SEQUENCE.incrementAndGet());
    }

    private OverlayMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            String sender, String channelName, boolean outgoing, int maxFadeSeconds, long sequence) {
        this.sequence = sequence;
        this.firstSequence = sequence;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.bossKc = bossKc;
        this.sender = sender;
        this.channelName = channelName;
        this.outgoing = outgoing;
        this.maxFadeSeconds = maxFadeSeconds;
    }

    public long getSequence() { return sequence; }
    public long getFirstSequence() { return firstSequence; }
    public static long latestSequence() { return SEQUENCE.get(); }

    public OverlayMessage withBody(String body) {
        OverlayMessage copy = new OverlayMessage(body, timestamp, type, bossKc, sender, channelName, outgoing, maxFadeSeconds, sequence);
        copy.count = count;
        copy.firstSequence = firstSequence;
        return copy;
    }

    public OverlayMessage withCount(int value) {
        OverlayMessage copy = withBody(message);
        copy.count = value;
        return copy;
    }

    public OverlayMessage groupedWith(OverlayMessage previous) {
        OverlayMessage copy = withCount(previous.getCount() + count);
        copy.firstSequence = previous.firstSequence;
        return copy;
    }

    public String getMessage() { return message; }
    public String getComparisonText() {
        if (comparisonText == null) comparisonText = message == null ? "" : COLOR_TAG.matcher(message).replaceAll("");
        return comparisonText;
    }
    public long getTimestamp() { return timestamp; }
    public ChatMessageType getType() { return type; }
    public boolean isBossKc() { return bossKc; }
    public String getSender() { return sender; }
    public String getChannelName() { return channelName; }
    public boolean isOutgoing() { return outgoing; }
    public int getCount() { return count; }
    public void incrementCount() { count++; }
    public void setCount(int count) { this.count = count; }
    public int getMaxFadeSeconds() { return maxFadeSeconds; }
}
