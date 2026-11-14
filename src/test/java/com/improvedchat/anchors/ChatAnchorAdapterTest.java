package com.improvedchat.anchors;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.input.ChatInputDock;
import com.improvedchat.input.ChatInputDockOverlay;
import com.improvedchat.input.InputDockPlacement;
import com.improvedchat.overlay.DynamicChatOverlay;
import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.model.PlacementMode;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChatAnchorAdapterTest
{
    private final Client client=mock(Client.class);
    private final ImprovedChatConfig config=mock(ImprovedChatConfig.class,CALLS_REAL_METHODS);
    private final ConfigManager configs=mock(ConfigManager.class);
    private final ImprovedChatPlugin plugin=mock(ImprovedChatPlugin.class);
    private final ChatInputDock input=mock(ChatInputDock.class);
    private final OverlayConfig settings=new OverlayConfig();
    private ChatInputDockOverlay dock;
    private DynamicChatOverlay chat;
    private ChatAnchorAdapter adapter;
    @Before public void setup() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(config.inputDockEnabled()).thenReturn(true);when(config.inputDockPlacement()).thenReturn(InputDockPlacement.FLOATING);when(input.canSwitch()).thenReturn(true);
        settings.setShow(true);settings.setAlwaysVisible(true);
        chat=new DynamicChatOverlay(plugin,config,client,mock(ChatColorConfig.class),new ClanChatPresentationService(),settings);chat.getBounds().setBounds(40,50,320,200);
        dock=new ChatInputDockOverlay(input,config,configs);dock.getBounds().setBounds(40,250,180,24);when(plugin.anchorOverlays()).thenReturn(List.of(chat));
        adapter=Guice.createInjector(new AbstractModule(){@Override protected void configure(){bind(Client.class).toInstance(client);bind(ImprovedChatConfig.class).toInstance(config);bind(ImprovedChatPlugin.class).toProvider(()->plugin);bind(ChatInputDock.class).toProvider(()->input);bind(ChatInputDockOverlay.class).toInstance(dock);bind(ClientThread.class).toInstance(mock(ClientThread.class));bind(EventBus.class).toInstance(new EventBus());bind(OverlayManager.class).toInstance(mock(OverlayManager.class));}}).getInstance(ChatAnchorAdapter.class);
    }
    private AnchorParticipant.Target target(String id){return adapter.targets().stream().filter(target->target.id().equals(id)).findFirst().get();}
    @Test public void movingTheFloatingDockNeverChangesScaleOrSendsInput() {
        var target=target("chat.input-dock");assertTrue(target.claim());for(int n=0;n<100;n++)target.move(new Point(100+n,200));
        assertEquals(new Rectangle(199,200,180,24),dock.getBounds());assertFalse(dock.isMovable());verifyNoInteractions(configs);verify(input,never()).paint(any(),any(),anyBoolean());
        target.release(false);assertTrue(dock.isMovable());
    }
    @Test public void attachedDockIsNotAnIndependentMember() {
        var target=target("chat.input-dock");InputDockPlacement attached=java.util.Arrays.stream(InputDockPlacement.values()).filter(value->value!=InputDockPlacement.FLOATING).findFirst().get();when(config.inputDockPlacement()).thenReturn(attached);
        assertFalse(target.available());assertEquals("UNAVAILABLE",target.state());
    }
    @Test public void measurementIncludesCurrentAttachedDockWithoutPaintingOrResizing() {
        when(plugin.getInputDock()).thenReturn(input);settings.setShowInputPreview(false);
        InputDockPlacement placement=java.util.Arrays.stream(InputDockPlacement.values()).filter(value->value!=InputDockPlacement.FLOATING && !value.isVertical()).findFirst().get();
        when(config.inputDockPlacement()).thenReturn(placement);
        when(input.attachedLayout(any(),eq(settings.getId()))).thenAnswer(call->new com.improvedchat.input.InputDockLayout(call.getArgument(0),List.of(com.improvedchat.input.ChatInputMode.PUBLIC),100,false));
        Rectangle measured=target("chat.history:"+settings.getId()).bounds();
        verify(input,never()).paint(any(),any(),anyBoolean());verifyNoInteractions(configs);assertNotEquals(chat.getBounds().getSize(),measured.getSize());
        java.awt.Graphics2D graphics=new java.awt.image.BufferedImage(800,600,java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {assertEquals(measured.getSize(),chat.render(graphics));}finally{graphics.dispose();}
    }
    @Test public void followPlayerAndHiddenChangesRetainIdentityButReleaseMovabilityCorrectly() {
        var target=target("chat.history:"+settings.getId());String id=target.id();target.claim();target.move(new Point(100,100));
        settings.setPlacementMode(PlacementMode.ABOVE_PLAYER);chat.setOverlayConfig(settings);assertFalse(target.available());target.release(false);assertFalse(chat.isMovable());assertNull(chat.getPreferredLocation());
        settings.setPlacementMode(PlacementMode.FREE);chat.setOverlayConfig(settings);assertEquals(id,target.id());assertTrue(target.available());settings.setShow(false);assertEquals("HIDDEN",target.state());
    }
}
