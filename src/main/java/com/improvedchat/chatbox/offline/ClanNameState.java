package com.improvedchat.chatbox.offline;

import com.improvedchat.model.MessageNodeIdentity;
import java.util.Objects;
import net.runelite.api.MessageNode;

final class ClanNameState
{
    private final MessageNodeIdentity identity;
    private String originalName;
    private String lastAppliedName;

    private ClanNameState(MessageNodeIdentity identity, String originalName)
    {
        this.identity = identity;
        this.originalName = normalize(originalName);
        this.lastAppliedName = this.originalName;
    }

    static ClanNameState capture(MessageNode node)
    {
        return new ClanNameState(MessageNodeIdentity.capture(node), node == null ? "" : node.getName());
    }

    boolean matches(MessageNode node)
    {
        return identity != null && identity.matches(node);
    }

    String baseName(String currentName)
    {
        String current = normalize(currentName);

        if (!Objects.equals(current, lastAppliedName) && !Objects.equals(current, originalName))
        {
            originalName = current;
            lastAppliedName = current;
        }

        return originalName;
    }

    void recordApplied(String value)
    {
        lastAppliedName = normalize(value);
    }

    boolean shouldRestore(MessageNode node)
    {
        return matches(node)
            && Objects.equals(normalize(node.getName()), lastAppliedName)
            && !Objects.equals(originalName, lastAppliedName);
    }

    String getOriginalName()
    {
        return originalName;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value;
    }
}
