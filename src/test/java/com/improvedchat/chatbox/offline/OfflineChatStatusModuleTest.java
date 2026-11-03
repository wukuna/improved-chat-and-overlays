package com.improvedchat.chatbox.offline;

import com.improvedchat.ImprovedChatConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfflineChatStatusModuleTest
{
    @Test public void statusGreysNamesWhetherOrNotTheOptionalIconIsAvailable()
    {
        String name = "<img=5><col=ff0000>Alice</col>";
        String grey = "<col=808080><img=5>Alice</col>";
        assertEquals(grey, OfflineChatStatusModule.offlineName(name, false, 42));
        assertEquals("<img=42>" + grey, OfflineChatStatusModule.offlineName(name, true, 42));
        assertEquals(grey, OfflineChatStatusModule.offlineName(name, true, -1));
    }

    @Test public void allClanChannelsGreyOfflineNamesAndRestoreOnlineColors() throws Exception
    {
        for (ChatMessageType type : new ChatMessageType[]{ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.CLAN_GIM_CHAT})
        {
            Fixture f = new Fixture(type);
            f.format();
            assertEquals("<col=808080><img=5>Alice</col>", f.name);
            f.format();
            assertEquals("<col=808080><img=5>Alice</col>", f.name);
            f.online = true;
            f.format();
            assertEquals(f.original, f.name);
        }
    }

    @Test public void unknownClanStateAndNonClanChatRemainUnmarked() throws Exception
    {
        Fixture f = new Fixture(ChatMessageType.CLAN_CHAT);
        f.known = false; f.format(); assertEquals(f.original, f.name);
        f.known = true; f.format(); assertNotEquals(f.original, f.name);
        f.known = false; f.format(); assertEquals(f.original, f.name);
        Fixture publicChat = new Fixture(ChatMessageType.PUBLICCHAT);
        publicChat.format(); assertEquals(publicChat.original, publicChat.name);
    }

    @Test public void disablingRestoresOriginalNameButPreservesExternalChangesAndReusedNodes() throws Exception
    {
        Fixture f = new Fixture(ChatMessageType.CLAN_CHAT);
        f.format(); f.restore(); assertEquals(f.original, f.name);
        f.format(); f.name = "<img=7>Alice"; f.restore();
        assertEquals("<img=7>Alice", f.name);
        f.format(); f.id++; f.name = "Bob"; f.restore();
        assertEquals("Bob", f.name);
    }

    private static class Fixture
    {
        final String original = "<img=5><col=ff0000>Alice</col>";
        String name = original;
        boolean online, known = true;
        int id = 1;
        final OfflineChatStatusModule module = new OfflineChatStatusModule();
        final MessageNode node;
        Fixture(ChatMessageType type) throws Exception
        {
            ClanChannelMember member = proxy(ClanChannelMember.class, (p, m, a) -> fallback(m.getReturnType()));
            ClanChannel channel = proxy(ClanChannel.class, (p, m, a) ->
                "findMember".equals(m.getName()) ? (online ? member : null) : fallback(m.getReturnType()));
            Client client = proxy(Client.class, (p, m, a) -> {
                if ("getClanChannel".equals(m.getName()) || "getGuestClanChannel".equals(m.getName()))
                    return known ? channel : null;
                return fallback(m.getReturnType());
            });
            node = proxy(MessageNode.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getName": return name;
                    case "setName": name = (String) a[0]; return null;
                    case "getType": return type;
                    case "getId": return id;
                    case "getTimestamp": return 10;
                    default: return fallback(m.getReturnType());
                }
            });
            ClanChatPresentationService service = new ClanChatPresentationService();
            set(service, "client", client);
            set(module, "client", client);
            set(module, "clanPresentation", service);
            set(module, "config", new ImprovedChatConfig() {
                @Override public boolean enableOfflineChatStatus() { return true; }
                @Override public boolean enableOfflineIcon() { return false; }
            });
        }
        void format() throws Exception {
            Method m = OfflineChatStatusModule.class.getDeclaredMethod("format", MessageNode.class);
            m.setAccessible(true); m.invoke(module, node);
        }
        void restore() throws Exception {
            Method m = OfflineChatStatusModule.class.getDeclaredMethod("restoreAll");
            m.setAccessible(true); m.invoke(module);
        }
    }
    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target, value);
    }
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    private static Object fallback(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
