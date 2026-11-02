package com.improvedchat;

import com.google.gson.Gson;
import com.improvedchat.overlay.OverlayConfig;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.*;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.events.ProfileChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VisibilityIntegrationTest {
    private static void set(Object target,String name,Object value) throws Exception {
        Field f=ImprovedChatPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private static class Fixture {
        final ImprovedChatPlugin plugin=new ImprovedChatPlugin();
        final Client client=mock(Client.class);
        final ConfigManager config=mock(ConfigManager.class);
        final List<Runnable> queue=new ArrayList<>();
        final OverlayConfig first=new OverlayConfig(),second=new OverlayConfig();
        final VisibilityHotkeys keys;
        Fixture() throws Exception {
            ClientThread thread=mock(ClientThread.class);
            doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            set(plugin,"client",client);set(plugin,"clientThread",thread);set(plugin,"configManager",config);
            set(plugin,"config",new ImprovedChatConfig(){});set(plugin,"gson",new Gson());set(plugin,"nativeChatActive",true);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            first.setToggleKeybind(new Keybind(KeyEvent.VK_F7,0));second.setToggleKeybind(first.getToggleKeybind());second.setShow(false);
            plugin.getOverlayConfigs().addAll(Arrays.asList(first,second));
            Field f=ImprovedChatPlugin.class.getDeclaredField("visibilityHotkeys");f.setAccessible(true);keys=(VisibilityHotkeys)f.get(plugin);
            keys.configure(plugin.getOverlayConfigs(),Keybind.NOT_SET);
        }
        void press() throws Exception {
            SwingUtilities.invokeAndWait(()->keys.keyPressed(new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_F7,KeyEvent.CHAR_UNDEFINED) {
                @Override public int getExtendedKeyCode(){return getKeyCode();}
            }));
        }
    }
    @Test public void sharedOverlayPressQueuesAllChangesWithoutReadingClientOnAwt() throws Exception {
        Fixture f=new Fixture();clearInvocations(f.client);f.press();
        verifyNoInteractions(f.client);assertTrue(f.first.isShow());assertFalse(f.second.isShow());
        assertEquals(1,f.queue.size());f.queue.remove(0).run();
        assertFalse(f.first.isShow());assertTrue(f.second.isShow());
        verify(f.config).setConfiguration(eq("improvedchat"),eq("overlayConfigs"),any(String.class));
    }
    @Test public void profileSwitchAndShutdownDiscardQueuedPresses() throws Exception {
        Fixture f=new Fixture();f.press();f.plugin.onProfileChanged(new ProfileChanged());
        f.queue.remove(0).run();assertTrue(f.first.isShow());assertFalse(f.second.isShow());
        verifyNoInteractions(f.config);
        Fixture stopped=new Fixture();stopped.press();set(stopped.plugin,"nativeChatActive",false);
        stopped.queue.remove(0).run();assertTrue(stopped.first.isShow());verifyNoInteractions(stopped.config);
    }
}
