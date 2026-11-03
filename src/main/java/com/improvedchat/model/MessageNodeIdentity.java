package com.improvedchat.model;

import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;

public final class MessageNodeIdentity
{
    private final int id;
    private final int timestamp;
    private final ChatMessageType type;

    private MessageNodeIdentity(int id, int timestamp, ChatMessageType type)
    {
        this.id = id;
        this.timestamp = timestamp;
        this.type = type;
    }

    public static MessageNodeIdentity capture(MessageNode node)
    {
        if (node == null)
        {
            return null;
        }
        return new MessageNodeIdentity(node.getId(), node.getTimestamp(), node.getType());
    }

    public boolean matches(MessageNode node)
    {
        return node != null
            && node.getId() == id
            && node.getTimestamp() == timestamp
            && node.getType() == type;
    }
}
