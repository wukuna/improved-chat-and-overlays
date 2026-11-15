package com.improvedchat.input;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ChatInputRoutingTest {
    static class Fixture extends ChatInputDockTest.Fixture {
        final EventBus bus = new EventBus();
        final AtomicInteger nativeMode = new AtomicInteger();
        Fixture() {
            bus.register(dock);
            when(client.getVarcIntValue(VarClientID.CHATBOX_MODE)).thenAnswer(call -> nativeMode.get());
            when(client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn("Hello everyone");
            doAnswer(call -> {
                nativeMode.set(call.getArgument(1));
                bus.post(new VarClientIntChanged(VarClientID.CHATBOX_MODE));
                return null;
            }).when(client).setVarcIntValue(eq(VarClientID.CHATBOX_MODE), anyInt());
        }
        void choose(ChatInputMode mode, boolean floating) throws Exception {
            paint(floating); assertTrue(press(mode, 0).isConsumed()); runQueued();
        }
        int[] route(int type, int prefixLength) {
            int[] stack = {777, 0, type, prefixLength, 888};
            when(client.getIntStack()).thenReturn(stack);
            when(client.getIntStackSize()).thenReturn(4);
            callback("chatDefaultReturn");
            return stack;
        }
        void callback(String name) {
            ScriptCallbackEvent event = new ScriptCallbackEvent(); event.setEventName(name); bus.post(event);
        }
        void state(GameState state) {
            when(client.getGameState()).thenReturn(state);
            GameStateChanged event = new GameStateChanged(); event.setGameState(state); bus.post(event);
        }
        void configChanged() {
            ConfigChanged event = new ConfigChanged(); event.setGroup("improvedchat"); bus.post(event);
        }
    }

    @Test public void publicSelectionOverridesClanViewForSendingWithoutChangingViewOrDraft() throws Exception {
        Fixture f = new Fixture();
        when(f.client.getVarcIntValue(VarClientID.CHAT_VIEW)).thenReturn(5);
        f.choose(ChatInputMode.PUBLIC, true); clearInvocations(f.client);
        assertArrayEquals(new int[]{777, 0, -1, 0, 888}, f.route(41, 0));
        assertEquals(0, f.nativeMode.get());
        assertEquals(5, f.client.getVarcIntValue(VarClientID.CHAT_VIEW));
        assertEquals("Hello everyone", f.client.getVarcStrValue(VarClientID.CHATINPUT));
        verify(f.client, never()).setVarcStrValue(anyInt(), anyString());
        verify(f.client, never()).setVarcIntValue(anyInt(), anyInt());
        verify(f.client, never()).runScript(anyInt(), any(Object[].class));
    }

    @Test public void everyDockModeUsesNativeModeResolutionAcrossChatViewsAndPlacements() throws Exception {
        for (ChatInputMode mode : new ChatInputMode[]{ChatInputMode.PUBLIC, ChatInputMode.CLAN, ChatInputMode.CHANNEL, ChatInputMode.GIM}) {
            for (boolean floating : new boolean[]{false, true}) {
                for (int viewType : new int[]{-1, 9, 41}) {
                    Fixture f = new Fixture(); f.choose(mode, floating);
                    assertArrayEquals(mode.toString(), new int[]{777, 0, -1, 0, 888}, f.route(viewType, 0));
                    assertEquals(mode.value, f.nativeMode.get());
                }
            }
        }
    }

    @Test public void untouchedDockLeavesNativeChatViewRoutingAlone() {
        Fixture f = new Fixture();
        assertArrayEquals(new int[]{777, 0, 41, 0, 888}, f.route(41, 0));
    }

    @Test public void explicitPrefixesAndCommandsKeepTheirDestinations() throws Exception {
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
        for (String draft : new String[]{"/ hello", "// hello", "/// hello", "//// hello", "/p hello", "/c hello", "/g hello", "/f hello", "/@c", "/t hello", "::bank"}) {
            when(f.client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn(draft);
            assertArrayEquals(draft, new int[]{777, 0, 41, 0, 888}, f.route(41, 0));
            assertArrayEquals(draft, new int[]{777, 0, 41, 3, 888}, f.route(41, 3));
        }
        when(f.client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn("Hello everyone");
        assertArrayEquals(new int[]{777, 0, 41, 3, 888}, f.route(41, 3));
    }

    @Test public void handledMessagesOtherCallbacksAndIncompleteStacksAreUntouched() throws Exception {
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
        int[] stack = {1, 41, 0};
        when(f.client.getIntStack()).thenReturn(stack); when(f.client.getIntStackSize()).thenReturn(3);
        f.callback("chatDefaultReturn"); assertArrayEquals(new int[]{1, 41, 0}, stack);
        stack[0] = 0; f.callback("chatboxInput"); assertEquals(41, stack[1]);
        for (int size : new int[]{-1, 0, 2, 4}) {
            when(f.client.getIntStackSize()).thenReturn(size);
            f.callback("chatDefaultReturn"); assertArrayEquals(new int[]{0, 41, 0}, stack);
        }
        when(f.client.getIntStack()).thenReturn(null); f.callback("chatDefaultReturn");
    }

    @Test public void nativeInputModeChangesReleaseDockRoutingUntilAnotherClick() throws Exception {
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
        f.client.setVarcIntValue(VarClientID.CHATBOX_MODE, 2);
        assertEquals(41, f.route(41, 0)[2]);
        f.choose(ChatInputMode.PUBLIC, true);
        assertEquals(-1, f.route(41, 0)[2]);
        f.bus.post(new VarClientIntChanged(VarClientID.CHAT_VIEW));
        assertEquals(-1, f.route(41, 0)[2]);
    }

    @Test public void unexpectedNativeModeChangeDoesNotApplyAStaleChoice() throws Exception {
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true); f.nativeMode.set(2);
        assertEquals(41, f.route(41, 0)[2]);
    }

    @Test public void profileAndPluginLifecycleReleaseRouting() throws Exception {
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
        f.bus.post(new ProfileChanged()); assertEquals(41, f.route(41, 0)[2]);
        f.choose(ChatInputMode.PUBLIC, true); f.dock.shutDown(); assertEquals(41, f.route(41, 0)[2]);
        f.dock.startUp(); assertEquals(41, f.route(41, 0)[2]);
    }

    @Test public void logoutAndConnectionChangesReleaseRoutingButSceneReloadKeepsIt() throws Exception {
        for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.HOPPING, GameState.CONNECTION_LOST}) {
            Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
            f.state(state); assertEquals(41, f.route(41, 0)[2]);
            f.state(GameState.LOGGED_IN); assertEquals(41, f.route(41, 0)[2]);
        }
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
        f.state(GameState.LOADING); assertEquals(41, f.route(41, 0)[2]);
        f.state(GameState.LOGGED_IN); assertEquals(-1, f.route(41, 0)[2]);
    }

    @Test public void removingButtonOrDisablingDockReleasesRoutingWhileAppearanceChangesKeepIt() throws Exception {
        Fixture f = new Fixture(); f.choose(ChatInputMode.PUBLIC, true);
        f.settings.scale = 150; f.configChanged(); assertEquals(-1, f.route(41, 0)[2]);
        f.settings.publicButton = false; f.configChanged(); assertEquals(41, f.route(41, 0)[2]);
        f.settings.publicButton = true; f.configChanged(); assertEquals(41, f.route(41, 0)[2]);
        f.choose(ChatInputMode.PUBLIC, true); f.settings.enabled = false; f.configChanged();
        assertEquals(41, f.route(41, 0)[2]);
        f.settings.enabled = true; f.configChanged(); assertEquals(41, f.route(41, 0)[2]);
    }
}
