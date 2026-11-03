package com.improvedchat.chatbox.offline;

import java.lang.reflect.Proxy;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClanNameStateTest
{
    @Test
    public void recycledNodeNeverReusesPreviousPlayerName()
    {
        NodeState nodeState = new NodeState(200, 5000, ChatMessageType.CLAN_CHAT, "Other Player");
        MessageNode node = nodeState.proxy();
        ClanNameState oldState = ClanNameState.capture(node);

        oldState.recordApplied("<img=12><col=808080>Other Player</col>");

        nodeState.id = 201;
        nodeState.timestamp = 5001;
        nodeState.name = "Richard";

        assertFalse(oldState.matches(node));

        ClanNameState replacement = ClanNameState.capture(node);
        assertEquals("Richard", replacement.baseName(node.getName()));
        assertTrue(replacement.matches(node));
    }

    @Test
    public void restoreIsBlockedAfterNodeReuse()
    {
        NodeState nodeState = new NodeState(300, 6000, ChatMessageType.CLAN_CHAT, "Other Player");
        MessageNode node = nodeState.proxy();
        ClanNameState state = ClanNameState.capture(node);
        state.recordApplied("<col=808080>Other Player</col>");
        nodeState.name = "<col=808080>Other Player</col>";

        assertTrue(state.shouldRestore(node));

        nodeState.id = 301;
        nodeState.timestamp = 6001;
        nodeState.name = "Richard";

        assertFalse(state.shouldRestore(node));
        assertEquals("Richard", node.getName());
    }

    @Test
    public void externalNameChangeBecomesNewBaselineForSameMessage()
    {
        NodeState nodeState = new NodeState(400, 7000, ChatMessageType.CLAN_CHAT, "Alice");
        MessageNode node = nodeState.proxy();
        ClanNameState state = ClanNameState.capture(node);

        state.recordApplied("<col=808080>Alice</col>");
        nodeState.name = "<img=5>Alice";

        assertEquals("<img=5>Alice", state.baseName(node.getName()));
    }

    private static final class NodeState
    {
        private int id;
        private int timestamp;
        private ChatMessageType type;
        private String name;

        private NodeState(int id, int timestamp, ChatMessageType type, String name)
        {
            this.id = id;
            this.timestamp = timestamp;
            this.type = type;
            this.name = name;
        }

        private MessageNode proxy()
        {
            return (MessageNode) Proxy.newProxyInstance(
                MessageNode.class.getClassLoader(),
                new Class<?>[] {MessageNode.class},
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId": return id;
                        case "getTimestamp": return timestamp;
                        case "getType": return type;
                        case "getName": return name;
                        case "setName": name = (String) args[0]; return null;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "MessageNodeProxy";
                        default:
                            Class<?> returnType = method.getReturnType();
                            if (returnType == boolean.class) return false;
                            if (returnType == int.class) return 0;
                            if (returnType == long.class) return 0L;
                            return null;
                    }
                });
        }
    }
}
