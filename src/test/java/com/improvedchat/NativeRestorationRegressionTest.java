package com.improvedchat;

import com.improvedchat.chatbox.collapse.*;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRestorationRegressionTest {
    private static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    @Test public void retainedBackgroundChildKeepsNativeOpacity() throws Exception {
        NativeUserCombinationAuditTest.F f = new NativeUserCombinationAuditTest.F();
        f.module.startUp();
        NativeUserCombinationAuditTest.W added = new NativeUserCombinationAuditTest.W(-1, 65, 3);
        f.background.p.put("DynamicChildren", new net.runelite.api.widgets.Widget[]{f.surface.w, added.w});
        f.module.reapplyAfterChatMutation();
        f.opacity[0] = -1;
        f.changed("chatboxOpacity");
        assertEquals(37, f.surface.p.get("Opacity"));
        assertEquals(65, added.p.get("Opacity"));
        f.module.shutDown();
    }

    @Test public void shutdownPreservesLaterBackgroundWriter() throws Exception {
        NativeUserCombinationAuditTest.F f = new NativeUserCombinationAuditTest.F();
        f.module.startUp();
        f.surface.p.put("Opacity", 81);
        f.module.shutDown();
        assertEquals(81, f.surface.p.get("Opacity"));
    }

    @Test public void reapplyCapturesLaterNativeBackgroundStyle() throws Exception {
        NativeUserCombinationAuditTest.F f = new NativeUserCombinationAuditTest.F();
        f.module.startUp();
        f.surface.p.put("Opacity", 81);
        f.module.reapplyAfterChatMutation();
        f.module.shutDown();
        assertEquals(81, f.surface.p.get("Opacity"));
    }

    @Test public void removedBackgroundChildrenAreReleased() throws Exception {
        NativeUserCombinationAuditTest.F f = new NativeUserCombinationAuditTest.F();
        f.module.startUp();
        f.background.p.put("DynamicChildren", new net.runelite.api.widgets.Widget[0]);
        f.module.reapplyAfterChatMutation();
        assertEquals(37, f.surface.p.get("Opacity"));
        f.module.shutDown();
    }

    @Test public void nativeHiddenTabsAndReplacementTabsKeepOwnVisibility() throws Exception {
        Map<Integer, NativeUserCombinationAuditTest.W> widgets = new HashMap<>();
        for (ChatButton b : ChatButton.values()) for (int id : new int[]{b.containerID,b.graphicID,b.textID}) {
            NativeUserCombinationAuditTest.W w = new NativeUserCombinationAuditTest.W(100, 0, 5);
            w.p.put("Text", "All"); w.p.put("Hidden", false); widgets.put(id, w);
        }
        NativeUserCombinationAuditTest.W original = widgets.get(ChatButton.PRIVATE.containerID);
        original.p.put("Hidden", true);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class}, (o,m,a) -> {
            if (m.getName().equals("getWidget")) { NativeUserCombinationAuditTest.W w = widgets.get(a[0]); return w == null ? null : w.w; }
            return UserCombinationAuditTest.def(m.getReturnType());
        });
        ChatWidgetManager manager = new ChatWidgetManager();
        set(manager, "client", client); set(manager, "config", new ImprovedChatConfig(){});
        ChatState state = new ChatState(); state.collapseState = ChatCollapseState.COLLAPSED;
        manager.updateChatWidgets(state); state.reset(); manager.updateChatWidgets(state);
        assertEquals(true, original.p.get("Hidden"));
        state.collapseState = ChatCollapseState.COLLAPSED; manager.updateChatWidgets(state);
        NativeUserCombinationAuditTest.W replacement = new NativeUserCombinationAuditTest.W(100,0,5);
        replacement.p.put("Text", "All"); replacement.p.put("Hidden", false);
        widgets.put(ChatButton.PRIVATE.containerID, replacement);
        manager.updateChatWidgets(state); state.reset(); manager.updateChatWidgets(state);
        assertEquals(true, original.p.get("Hidden"));
        assertEquals(false, replacement.p.get("Hidden"));
    }

    @Test public void staleOwnershipWorkCannotCrossEitherQueueOrLifecycleGeneration() throws Exception {
        for (boolean atTickEnd : new boolean[]{false,true}) for (boolean reenabled : new boolean[]{false,true}) {
            ImprovedChatPlugin plugin = new ImprovedChatPlugin();
            java.util.Deque<Runnable> queue = new ArrayDeque<>();
            ClientThread thread = new ClientThread() {
                @Override public void invokeLater(Runnable r) { queue.add(r); }
                @Override public void invokeAtTickEnd(Runnable r) { queue.add(r); }
            };
            set(plugin,"clientThread",thread); set(plugin,"nativeChatActive",true);
            Method schedule = ImprovedChatPlugin.class.getDeclaredMethod("scheduleNativeChatOwnershipReconcile");
            schedule.setAccessible(true); schedule.invoke(plugin);
            if (atTickEnd) queue.remove().run();
            set(plugin,"nativeChatActive",reenabled); set(plugin,"nativeChatGeneration",2L);

            while (!queue.isEmpty()) queue.remove().run();
        }
    }
}
