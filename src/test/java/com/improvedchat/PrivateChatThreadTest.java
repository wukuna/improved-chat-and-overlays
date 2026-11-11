package com.improvedchat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrivateChatThreadTest
{
    static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    static final class Fixture
    {
        final ImprovedChatPlugin plugin = new ImprovedChatPlugin();
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final ImprovedChatConfig config = mock(ImprovedChatConfig.class);
        final Widget container = mock(Widget.class), row = mock(Widget.class);
        final List<Runnable> tasks = new ArrayList<>();
        Fixture() throws Exception
        {
            when(config.hidePrivateChat()).thenReturn(true);
            when(client.getWidget(InterfaceID.PM_CHAT, 0)).thenReturn(container);
            when(container.getDynamicChildren()).thenReturn(new Widget[]{row});
            doAnswer(i -> { tasks.add(i.getArgument(0)); return null; }).when(thread).invoke(any(Runnable.class));
            set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
            set(plugin, "nativeChatActive", true);
            set(plugin, "modernChatCompatibility", new com.improvedchat.compat.ModernChatCompatibility());
        }
        void change() throws Exception
        {
            ConfigChanged event = new ConfigChanged(); event.setGroup("improvedchat"); event.setKey("hidePrivateChat");
            SwingUtilities.invokeAndWait(() -> plugin.onConfigChanged(event));
        }
    }
    @Test public void settingChangeDoesNotTouchWidgetsUntilClientDispatch() throws Exception
    {
        Fixture f = new Fixture(); f.change();
        verifyNoInteractions(f.client, f.container, f.row);
        assertEquals(1, f.tasks.size()); f.tasks.remove(0).run();
        verify(f.row).setHidden(true);
        when(f.row.isSelfHidden()).thenReturn(true);
        when(f.config.hidePrivateChat()).thenReturn(false); f.change();
        verify(f.row, never()).setHidden(false);
        f.tasks.remove(0).run(); verify(f.row).setHidden(false);
    }
    @Test public void queuedChangesCannotCrossProfileOrLifecycleBoundary() throws Exception
    {
        Fixture f = new Fixture(); f.change();
        set(f.plugin, "nativeChatGeneration", 1L);
        f.tasks.remove(0).run(); verifyNoInteractions(f.client);
        f.change(); set(f.plugin, "nativeChatActive", false);
        f.tasks.remove(0).run(); verifyNoInteractions(f.client);
    }
    @Test public void queuedChangesReadLatestVisibilityChoice() throws Exception
    {
        Fixture f = new Fixture(); f.change();
        when(f.config.hidePrivateChat()).thenReturn(false);
        f.tasks.remove(0).run(); verify(f.row, never()).setHidden(true);
    }
}
