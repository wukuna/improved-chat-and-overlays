package com.improvedchat;

import com.improvedchat.chatbox.ChatboxVisibility;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChatboxVisibilityTest {
    private static final int AREA=InterfaceID.Chatbox.CHATAREA, BAR=InterfaceID.Chatbox.CONTROLS;
    private static class Fixture {
        final Client client=mock(Client.class);
        final net.runelite.client.config.ConfigManager config=mock(net.runelite.client.config.ConfigManager.class);
        final Map<String,String> saved=new HashMap<>();
        final ChatboxVisibility visibility=new ChatboxVisibility();
        final com.improvedchat.chatbox.resize.ChatDialogBoxes dialogs=mock(com.improvedchat.chatbox.resize.ChatDialogBoxes.class);
        final Map<Integer,Widget> widgets=new HashMap<>();
        Fixture() throws Exception {
            Field f=ChatboxVisibility.class.getDeclaredField("client");f.setAccessible(true);f.set(visibility,client);
            f=ChatboxVisibility.class.getDeclaredField("configManager");f.setAccessible(true);f.set(visibility,config);
            when(config.getConfiguration("improvedchat","chatboxHidden")).thenAnswer(i->saved.get("hidden"));
            doAnswer(i->{saved.put("hidden",String.valueOf(i.getArgument(2,Object.class)));return null;}).when(config).setConfiguration(eq("improvedchat"),eq("chatboxHidden"),any(Object.class));
            f=ChatboxVisibility.class.getDeclaredField("dialogs");f.setAccessible(true);f.set(visibility,dialogs);
            when(dialogs.isDialogOpen()).thenAnswer(i->!widgets.get(InterfaceID.Chatbox.MES_LAYER).isHidden());
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getWidget(anyInt())).thenAnswer(i -> widgets.get(i.getArgument(0)));
            widget(AREA,false); widget(BAR,false); widget(InterfaceID.Chatbox.MES_LAYER,true);
            widget(InterfaceID.Chatbox.CHATDISPLAY,false);
        }
        Widget widget(int id,boolean hidden) {
            Widget w=mock(Widget.class); AtomicBoolean state=new AtomicBoolean(hidden);
            when(w.isSelfHidden()).thenAnswer(i->state.get());when(w.isHidden()).thenAnswer(i->state.get());
            doAnswer(i->{state.set(i.getArgument(0));return null;}).when(w).setHidden(anyBoolean());
            widgets.put(id,w);return w;
        }
    }
    @Test public void toggleHidesEntireAreaAndBarAndRestoresTheirOriginalState() throws Exception {
        Fixture f=new Fixture(); f.widgets.get(BAR).setHidden(true);
        f.visibility.toggle(); assertTrue(f.widgets.get(AREA).isSelfHidden());assertTrue(f.widgets.get(BAR).isSelfHidden());
        f.visibility.onBeforeRender(new BeforeRender());f.visibility.toggle();
        assertFalse(f.widgets.get(AREA).isSelfHidden());assertTrue(f.widgets.get(BAR).isSelfHidden());
    }
    @Test public void scriptsRunWithNativeStateAndTheirVisibilityWinsOnRestore() throws Exception {
        Fixture f=new Fixture(); f.visibility.toggle();
        f.visibility.onScriptPreFired(new ScriptPreFired(113)); assertFalse(f.widgets.get(AREA).isSelfHidden());
        f.widgets.get(AREA).setHidden(true);
        f.visibility.onScriptPostFired(new ScriptPostFired(113)); f.visibility.reset();
        assertTrue(f.widgets.get(AREA).isSelfHidden());assertFalse(f.widgets.get(BAR).isSelfHidden());
    }
    @Test public void dialogueRemainsVisibleAndReplacementWidgetsAreRestored() throws Exception {
        Fixture f=new Fixture(); Widget old=f.widgets.get(AREA);f.visibility.toggle();
        Widget next=f.widget(AREA,false);f.visibility.onBeforeRender(new BeforeRender());
        assertFalse(old.isSelfHidden());assertTrue(next.isSelfHidden());
        f.widgets.get(InterfaceID.Chatbox.MES_LAYER).setHidden(false);f.visibility.onBeforeRender(new BeforeRender());
        assertFalse(next.isSelfHidden());assertTrue(f.widgets.get(InterfaceID.Chatbox.CHATDISPLAY).isSelfHidden());
        f.visibility.reset();assertFalse(f.widgets.get(InterfaceID.Chatbox.CHATDISPLAY).isSelfHidden());
    }
    @Test public void mountedNpcDialogueAlsoKeepsItsParentVisible() throws Exception {
        Fixture f=new Fixture();when(f.dialogs.isDialogOpen()).thenReturn(true);
        f.visibility.toggle();assertFalse(f.widgets.get(AREA).isSelfHidden());
        assertTrue(f.widgets.get(BAR).isSelfHidden());assertTrue(f.widgets.get(InterfaceID.Chatbox.CHATDISPLAY).isSelfHidden());
    }
    @Test public void hideChoiceSurvivesLogoutHopAndFreshClientInstance() throws Exception {
        Fixture f=new Fixture();f.visibility.toggle();assertEquals("true",f.saved.get("hidden"));
        for(GameState state:new GameState[]{GameState.HOPPING,GameState.LOGIN_SCREEN,GameState.LOGGED_IN}) {
            when(f.client.getGameState()).thenReturn(state);
            GameStateChanged event=new GameStateChanged();event.setGameState(state);f.visibility.onGameStateChanged(event);
            assertEquals(state==GameState.LOGGED_IN,f.widgets.get(AREA).isSelfHidden());
        }
        f.visibility.reset();assertFalse(f.widgets.get(AREA).isSelfHidden());assertEquals("true",f.saved.get("hidden"));
        Fixture restarted=new Fixture();restarted.saved.putAll(f.saved);restarted.visibility.setAvailable(true);
        assertTrue(restarted.visibility.isHidden());assertTrue(restarted.widgets.get(AREA).isSelfHidden());
        restarted.visibility.toggle();assertEquals("false",restarted.saved.get("hidden"));
    }
    @Test public void profilesAndCompatibilityRestoreWithoutOverwritingTheSavedChoice() throws Exception {
        Fixture f=new Fixture();f.visibility.toggle();
        f.visibility.setAvailable(false);assertFalse(f.visibility.isHidden());assertFalse(f.widgets.get(AREA).isSelfHidden());
        assertEquals("true",f.saved.get("hidden"));
        f.visibility.setAvailable(true);assertTrue(f.visibility.isHidden());
        f.saved.clear();f.visibility.setAvailable(true);assertFalse(f.visibility.isHidden());
        f.saved.put("hidden","true");f.visibility.setAvailable(true);assertTrue(f.visibility.isHidden());
    }
}
