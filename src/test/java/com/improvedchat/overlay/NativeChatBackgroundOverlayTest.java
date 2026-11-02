package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.SpriteManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NativeChatBackgroundOverlayTest {
    @Test public void translucentBackgroundRoundsBeforeTextWithoutChangingOpacityOrInputGeometry() {
        Fixture f = new Fixture(); f.prepare(); assertTrue(f.hidden.get());
        BufferedImage result = f.render();
        assertFalse(f.hidden.get()); assertEquals(0, result.getRGB(20, 30));
        assertEquals(128, result.getRGB(60, 60) >>> 24);
        Color color = new Color(result.getRGB(60,60), true);
        // Java2D premultiplied-alpha rounding may differ by one channel value.
        assertEquals(0x33, color.getRed(), 1); assertEquals(0x66, color.getGreen(), 1); assertEquals(0x99, color.getBlue(), 1);
        verify(f.child, never()).setOpacity(anyInt()); verify(f.child, never()).setWidth(anyInt());
        verify(f.child, never()).setHeight(anyInt()); verify(f.area, never()).setHidden(anyBoolean());
        verify(f.messages, never()).setHidden(anyBoolean());
        assertTrue(f.overlay.getDrawHooks().contains(InterfaceID.Chatbox.CHAT_BACKGROUND));
    }
    @Test public void followsMovedAndResizedChatAndReflectsOpacityChanges() {
        Fixture f = new Fixture(); f.prepare(); f.render();
        when(f.area.getRelativeX()).thenReturn(40); when(f.area.getWidth()).thenReturn(120);
        when(f.child.getOpacity()).thenReturn(0); f.prepare(); BufferedImage image = f.render();
        assertEquals(0, image.getRGB(25, 50)); assertEquals(255, image.getRGB(60, 60) >>> 24);
        when(f.child.getOpacity()).thenReturn(192); f.prepare(); assertEquals(64, f.render().getRGB(60,60) >>> 24);
    }
    @Test public void missingSpritesLeaveNativeArtworkVisibleUntilReady() {
        Fixture f = new Fixture(); when(f.child.getType()).thenReturn(WidgetType.GRAPHIC);
        when(f.child.getSpriteId()).thenReturn(123); f.prepare(); assertFalse(f.hidden.get());
        BufferedImage sprite = new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sprite.createGraphics();g.setColor(Color.ORANGE);g.fillRect(0,0,4,4);g.dispose();
        when(f.sprites.getSprite(123,0)).thenReturn(sprite); when(f.child.getOpacity()).thenReturn(0);
        for (boolean tiled:new boolean[]{true,false}) {
            when(f.child.getSpriteTiling()).thenReturn(tiled);f.prepare();assertTrue(f.hidden.get());
            BufferedImage image=f.render();assertEquals(Color.ORANGE.getRGB(),image.getRGB(60,60));
            assertEquals(0,image.getRGB(20,30));assertFalse(f.hidden.get());
        }
    }
    @Test public void gameHiddenChildrenAndDialoguesRetainTheirVisibility() {
        Fixture f = new Fixture(); f.hidden.set(true); f.prepare();f.render();assertTrue(f.hidden.get());
        verify(f.child,never()).setHidden(anyBoolean());
        f.hidden.set(false);when(f.messages.isHidden()).thenReturn(true);f.prepare();assertFalse(f.hidden.get());
        when(f.messages.isHidden()).thenReturn(false);f.prepare();assertTrue(f.hidden.get());f.render();assertFalse(f.hidden.get());
    }
    @Test public void skippedLayerRestoresBeforeGameScriptsAndPreservesNewGameHiding() {
        Fixture f = new Fixture();f.prepare();assertTrue(f.hidden.get());
        f.overlay.onScriptPreFired(new ScriptPreFired(0));assertFalse(f.hidden.get());
        f.hidden.set(true);f.prepare();f.render();assertTrue(f.hidden.get());
    }
    @Test public void disableLogoutAndReenableRestoreSuppressedBackground() {
        Fixture f = new Fixture();f.prepare();when(f.config.chatboxBorderRounded()).thenReturn(false);
        f.prepare();assertFalse(f.hidden.get());
        when(f.config.chatboxBorderRounded()).thenReturn(true);f.prepare();f.overlay.shutDown();assertFalse(f.hidden.get());
        f.prepare();assertFalse(f.hidden.get());f.overlay.startUp();f.prepare();assertTrue(f.hidden.get());
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        f.overlay.onGameStateChanged(new GameStateChanged());assertFalse(f.hidden.get());
        f.prepare();assertFalse(f.hidden.get());
    }
    @Test public void unsupportedChildPreventsPartialReplacement() {
        Fixture f = new Fixture();Widget other=mock(Widget.class);
        when(other.getType()).thenReturn(WidgetType.TEXT);when(other.getParent()).thenReturn(f.background);
        when(other.getWidth()).thenReturn(40);when(other.getHeight()).thenReturn(20);
        when(f.background.getDynamicChildren()).thenReturn(new Widget[]{f.child,other});
        f.prepare();assertFalse(f.hidden.get());verify(f.child,never()).setHidden(anyBoolean());
    }
    static final class Fixture {
        final Client client=mock(Client.class);final ImprovedChatConfig config=mock(ImprovedChatConfig.class);
        final SpriteManager sprites=mock(SpriteManager.class);final ClientThread thread=mock(ClientThread.class);
        final Widget area=mock(Widget.class),background=mock(Widget.class),messages=mock(Widget.class),child=mock(Widget.class);
        final AtomicBoolean hidden=new AtomicBoolean();
        final NativeChatBackgroundOverlay overlay=new NativeChatBackgroundOverlay(client,config,sprites,thread,new EventBus());
        Fixture() {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(area);
            when(client.getWidget(InterfaceID.Chatbox.SCROLLAREA)).thenReturn(messages);
            when(client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND)).thenReturn(background);
            when(client.getWidgetSpriteOverrides()).thenReturn(new HashMap<>());when(client.getSpriteOverrides()).thenReturn(new HashMap<>());
            when(config.chatboxBorder()).thenReturn(true);when(config.chatboxBorderRounded()).thenReturn(true);
            when(config.chatboxBorderThickness()).thenReturn(com.improvedchat.model.BorderThickness.NORMAL);
            when(area.getRelativeX()).thenReturn(20);when(area.getRelativeY()).thenReturn(30);
            when(area.getWidth()).thenReturn(100);when(area.getHeight()).thenReturn(70);
            when(background.getParent()).thenReturn(area);when(background.getWidth()).thenAnswer(i->area.getWidth());when(background.getHeight()).thenReturn(70);
            when(background.getDynamicChildren()).thenReturn(new Widget[]{child});when(child.getParent()).thenReturn(background);
            when(child.getWidth()).thenAnswer(i->area.getWidth());when(child.getHeight()).thenReturn(70);
            when(child.getType()).thenReturn(WidgetType.RECTANGLE);when(child.isFilled()).thenReturn(true);
            when(child.getTextColor()).thenReturn(0x336699);when(child.getOpacity()).thenReturn(128);
            when(child.isHidden()).thenAnswer(i->hidden.get());when(child.isSelfHidden()).thenAnswer(i->hidden.get());
            when(child.setHidden(anyBoolean())).thenAnswer(i->{hidden.set(i.getArgument(0));return child;});
            doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(thread).invoke(any(Runnable.class));
            overlay.startUp();
        }
        void prepare(){overlay.onBeforeRender(new BeforeRender());}
        BufferedImage render(){BufferedImage image=new BufferedImage(200,150,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{overlay.render(g);}finally{g.dispose();}return image;}
    }
}
