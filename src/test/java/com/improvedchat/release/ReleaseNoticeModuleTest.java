package com.improvedchat.release;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ProfileChanged;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReleaseNoticeModuleTest
{
    private final ReleaseNoticeModule notice=new ReleaseNoticeModule();
    private final Client client=mock(Client.class);
    private final ClientThread thread=mock(ClientThread.class);
    private final ConfigManager config=mock(ConfigManager.class);
    private final ChatMessageManager chat=mock(ChatMessageManager.class);
    private final List<Runnable> pending=new ArrayList<>();
    private String seen;

    @Before public void setup() throws Exception
    {
        inject("eventBus",mock(net.runelite.client.eventbus.EventBus.class));
        inject("client",client); inject("clientThread",thread); inject("configManager",config); inject("chat",chat);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getConfiguration("improvedchat",ReleaseNoticeModule.SEEN_KEY)).thenAnswer(i -> seen);
        doAnswer(i -> { seen=i.getArgument(2); return null; }).when(config)
            .setConfiguration(eq("improvedchat"),eq(ReleaseNoticeModule.SEEN_KEY),any(String.class));
        doAnswer(i -> { pending.add(i.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
    }

    @Test public void printsOneCompactBulletNoticeOnceAcrossLoginAndReenable()
    {
        notice.startUp(); flush();
        ArgumentCaptor<QueuedMessage> messages=ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat,times(1)).queue(messages.capture());
        String message=messages.getValue().getRuneLiteFormattedMessage();
        assertTrue(message.contains("Updates:<br>* "));
        assertEquals(6,message.split("<br>\\* ",-1).length-1);
        assertFalse(message.contains("Recent updates"));
        assertFalse(message.contains("1. "));
        assertEquals(ReleaseNoticeModule.RELEASE,seen);
        notice.onGameStateChanged(loggedIn()); flush();
        notice.shutDown(); notice.startUp(); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
    }

    @Test public void waitsForLoginAndDoesNotMarkUnshownNotesAsRead()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        notice.startUp(); flush(); verifyNoInteractions(chat); assertNull(seen);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        notice.onGameStateChanged(loggedIn()); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
    }

    @Test public void currentReceiptSuppressesNoticeButOlderReleaseDoesNot()
    {
        seen=ReleaseNoticeModule.RELEASE; notice.startUp(); flush(); verifyNoInteractions(chat);
        seen="previous-release"; notice.onProfileChanged(new ProfileChanged()); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
    }

    @Test public void shutdownAndProfileChangeInvalidateOldCallbacks()
    {
        notice.startUp(); notice.shutDown(); flush(); verifyNoInteractions(chat); assertNull(seen);
        notice.startUp(); notice.onProfileChanged(new ProfileChanged()); flush();
        verify(chat,times(1)).queue(any(QueuedMessage.class));
        seen=null; notice.onProfileChanged(new ProfileChanged()); flush();
        verify(chat,times(2)).queue(any(QueuedMessage.class));
    }

    private GameStateChanged loggedIn() { GameStateChanged event=new GameStateChanged(); event.setGameState(GameState.LOGGED_IN); return event; }
    private void flush() { List<Runnable> work=new ArrayList<>(pending); pending.clear(); work.forEach(Runnable::run); }
    private void inject(String name,Object value) throws Exception
    { Field field=ReleaseNoticeModule.class.getDeclaredField(name); field.setAccessible(true); field.set(notice,value); }
}
