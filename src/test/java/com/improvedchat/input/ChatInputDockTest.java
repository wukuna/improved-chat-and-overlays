package com.improvedchat.input;

import com.improvedchat.ImprovedChatConfig;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ProfileChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ChatInputDockTest {
    static class Settings implements ImprovedChatConfig {
        boolean enabled = true;
        boolean publicButton = true, clan = true, channel = true, gim = true;
        int scale = 100;
        InputDockPlacement placement = InputDockPlacement.FLOATING;
        String target = "test";
        @Override public boolean inputDockEnabled() { return enabled; }
        @Override public boolean inputDockPublic() { return publicButton; }
        @Override public boolean inputDockClan() { return clan; }
        @Override public boolean inputDockChannel() { return channel; }
        @Override public boolean inputDockGim() { return gim; }
        @Override public int inputDockScale() { return scale; }
        @Override public InputDockPlacement inputDockPlacement() { return placement; }
        @Override public String inputDockTarget() { return target; }
    }

    static class Fixture {
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final Settings settings = new Settings();
        final List<Runnable> queued = new ArrayList<>();
        final ChatInputDock dock = new ChatInputDock(client, thread, settings);
        InputDockLayout layout;
        Fixture() {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getWidget(InterfaceID.Chatbox.INPUT)).thenReturn(mock(Widget.class));
            when(client.getFriendsChatManager()).thenReturn(mock(FriendsChatManager.class));
            when(client.getClanChannel(ClanID.CLAN)).thenReturn(mock(ClanChannel.class));
            when(client.getClanChannel(ClanID.GROUP_IRONMAN)).thenReturn(mock(ClanChannel.class));
            doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
            dock.startUp();
        }
        BufferedImage paint(boolean floating) {
            BufferedImage image = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setClip(0, 0, 1000, 500); g.translate(40, 30);
            layout = dock.layout(g, settings.placement.isVertical());
            if (layout != null) dock.paint(g, layout, floating);
            g.dispose(); return image;
        }
        MouseEvent press(ChatInputMode mode, int modifiers) throws Exception {
            Rectangle r = layout.buttons.get(layout.modes.indexOf(mode));
            MouseEvent event = mouse(MouseEvent.MOUSE_PRESSED, r.x + 41, r.y + 31, modifiers, MouseEvent.BUTTON1);
            SwingUtilities.invokeAndWait(() -> dock.mousePressed(event));
            return event;
        }
        void runQueued() { new ArrayList<>(queued).forEach(Runnable::run); queued.clear(); }
    }

    private static MouseEvent mouse(int type, int x, int y, int modifiers, int button) {
        return new MouseEvent(new Canvas(), type, 0, modifiers, x, y, 1, false, button);
    }

    @Test public void eachButtonOnlyChangesNativeInputModeAndRebuildsInput() throws Exception {
        for (ChatInputMode mode : ChatInputMode.values()) {
            Fixture f = new Fixture(); f.paint(true); clearInvocations(f.client);
            assertTrue(f.press(mode, 0).isConsumed());
            verifyNoInteractions(f.client);
            f.runQueued();
            verify(f.client).setVarcIntValue(VarClientID.CHATBOX_MODE, mode.value);
            verify(f.client).runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
            verify(f.client, never()).setVarcStrValue(anyInt(), anyString());
            verify(f.client, never()).setVarcIntValue(eq(VarClientID.CHAT_VIEW), anyInt());
            verify(f.client, never()).setVarbit(anyInt(), anyInt());
        }
    }

    @Test public void autoSizingRemovesSlotsAndHidesAnEmptyDock() {
        Fixture f = new Fixture(); f.paint(true); int allWidth = f.layout.size.width;
        f.settings.publicButton = false; f.settings.channel = false; f.settings.gim = false;
        f.paint(true); assertEquals(Collections.singletonList(ChatInputMode.CLAN), f.layout.modes);
        assertTrue(f.layout.size.width < allWidth / 2);
        f.settings.clan = false; f.paint(true); assertNull(f.layout);
    }

    @Test public void gimOnlyAppearsWithAGroupChannelAndUnavailableChatsDoNotSwitch() throws Exception {
        Fixture f = new Fixture();
        when(f.client.getClanChannel(ClanID.GROUP_IRONMAN)).thenReturn(null);
        when(f.client.getClanChannel(ClanID.CLAN)).thenReturn(null);
        f.paint(true); assertFalse(f.layout.modes.contains(ChatInputMode.GIM));
        assertTrue(f.press(ChatInputMode.CLAN, 0).isConsumed()); assertTrue(f.queued.isEmpty());
        f.runQueued(); verify(f.client, never()).setVarcIntValue(anyInt(), anyInt());
    }

    @Test public void modifierDragRightClickAndOutsideClicksPassThrough() throws Exception {
        Fixture f = new Fixture(); f.paint(true);
        for (int mask : new int[]{InputEvent.ALT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK, InputEvent.META_DOWN_MASK})
            assertFalse(f.press(ChatInputMode.PUBLIC, mask).isConsumed());
        MouseEvent outside = mouse(MouseEvent.MOUSE_PRESSED, 0, 0, 0, MouseEvent.BUTTON1);
        assertFalse(f.dock.mousePressed(outside).isConsumed());
        Rectangle r = f.layout.buttons.get(0);
        assertFalse(f.dock.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, r.x + 41, r.y + 31, 0, MouseEvent.BUTTON3)).isConsumed());
        assertTrue(f.queued.isEmpty());
    }

    @Test public void ownedPressConsumesDragReleaseAndClickWithoutReadingClientOnAwt() throws Exception {
        Fixture f = new Fixture(); f.paint(true); clearInvocations(f.client);
        assertTrue(f.press(ChatInputMode.PUBLIC, 0).isConsumed());
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(f.dock.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 900, 400, 0, MouseEvent.NOBUTTON)).isConsumed());
            assertTrue(f.dock.mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 900, 400, 0, MouseEvent.BUTTON1)).isConsumed());
            assertTrue(f.dock.mouseClicked(mouse(MouseEvent.MOUSE_CLICKED, 900, 400, 0, MouseEvent.BUTTON1)).isConsumed());
        });
        verifyNoInteractions(f.client);
        assertEquals(1, f.queued.size());
    }

    @Test public void staleClicksAreCancelledAcrossShutdownProfileLogoutDialogAndMembershipChanges() throws Exception {
        for (int scenario = 0; scenario < 7; scenario++) {
            Fixture f = new Fixture(); f.paint(true); f.press(ChatInputMode.CLAN, 0);
            switch (scenario) {
                case 0: f.dock.shutDown(); f.dock.startUp(); break;
                case 1: f.dock.onProfileChanged(new ProfileChanged()); break;
                case 2: when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); break;
                case 3: when(f.client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(3); break;
                case 4: when(f.client.getClanChannel(ClanID.CLAN)).thenReturn(null); break;
                case 5: f.settings.enabled = false; break;
                default: when(f.client.isMenuOpen()).thenReturn(true);
            }
            f.runQueued(); verify(f.client, never()).setVarcIntValue(anyInt(), anyInt());
        }
    }

    @Test public void realEventBusClearsHiddenBoundsAndInvalidatesQueuedClicks() throws Exception {
        Fixture f = new Fixture(); EventBus bus = new EventBus(); bus.register(f.dock);
        f.paint(true); f.press(ChatInputMode.PUBLIC, 0);
        bus.post(new ProfileChanged()); f.runQueued();
        verify(f.client, never()).setVarcIntValue(anyInt(), anyInt());
        f.paint(true); bus.post(new BeforeRender());
        assertFalse(f.press(ChatInputMode.PUBLIC, 0).isConsumed());
        bus.unregister(f.dock);
    }

    @Test public void clippedAndCoveredAttachedButtonsDoNotCaptureInput() throws Exception {
        Fixture f = new Fixture(); Widget chat = mock(Widget.class);
        when(chat.getBounds()).thenReturn(new Rectangle(0, 0, 1000, 500));
        when(f.client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(chat);
        f.paint(false); assertFalse(f.press(ChatInputMode.PUBLIC, 0).isConsumed());
        f.paint(true); assertTrue(f.press(ChatInputMode.PUBLIC, 0).isConsumed());
        f.dock.onBeforeRender(new BeforeRender());
        Graphics2D g = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setClip(0, 0, 2, 2); f.dock.paint(g, f.layout, true); g.dispose();
        assertFalse(f.press(ChatInputMode.PUBLIC, 0).isConsumed());
    }

    @Test public void floatingResizeScalesBothAxesAndRemainsAutomaticAfterButtonRemoval() {
        Fixture f = new Fixture(); ConfigManager manager = mock(ConfigManager.class);
        doAnswer(call -> { f.settings.scale = call.getArgument(2); return null; }).when(manager).setConfiguration(eq(ImprovedChatConfig.GROUP), eq("inputDockScale"), anyInt());
        ChatInputDockOverlay overlay = new ChatInputDockOverlay(f.dock, f.settings, manager);
        Graphics2D g = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB).createGraphics();
        Dimension original = overlay.render(g); overlay.getBounds().setSize(original);
        overlay.setPreferredSize(new Dimension(original.width * 3 / 2, original.height));
        assertEquals(150, f.settings.scale);
        Dimension scaled = overlay.render(g); assertTrue(scaled.width > original.width); assertTrue(scaled.height > original.height);
        f.settings.clan = false; f.settings.channel = false; f.settings.gim = false;
        assertTrue(overlay.render(g).width < original.width); assertEquals(150, f.settings.scale);
        overlay.setPreferredSize(new Dimension(10000, 10000)); assertEquals(200, f.settings.scale);
        g.dispose();
    }

    @Test public void attachmentsContainButtonsAndContentWithoutOverlapAtEverySize() {
        Graphics2D g = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB).createGraphics();
        for (InputDockPlacement placement : new InputDockPlacement[]{InputDockPlacement.LEFT, InputDockPlacement.RIGHT, InputDockPlacement.BOTTOM}) {
            for (int scale : new int[]{60, 100, 150, 200}) {
                InputDockLayout layout = new InputDockLayout(g, Arrays.asList(ChatInputMode.values()), scale, placement.isVertical());
                InputDockLayout.Frame frame = layout.attach(placement, 150, 30, 3);
                assertTrue(new Rectangle(frame.size).contains(frame.content));
                assertTrue(new Rectangle(frame.size).contains(frame.dock));
                assertFalse(frame.content.intersects(frame.dock));
                for (Rectangle button : layout.buttons) assertTrue(new Rectangle(layout.size).contains(button));
            }
        }
        g.dispose();
    }
}
