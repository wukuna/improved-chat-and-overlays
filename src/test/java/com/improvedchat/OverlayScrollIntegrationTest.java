package com.improvedchat;

import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.overlay.*;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayScrollIntegrationTest {
    private static class TestPlugin extends ImprovedChatPlugin {
        @Override public boolean isGameFilterEnabled() { return false; }
        @Override public boolean isBossKcFilterEnabled() { return false; }
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Field f=ImprovedChatPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    @SuppressWarnings("unchecked") private static <T> List<T> list(ImprovedChatPlugin p,String name) throws Exception {
        Field f=ImprovedChatPlugin.class.getDeclaredField(name);f.setAccessible(true);return (List<T>)f.get(p);
    }
    private static class Fixture {
        final TestPlugin plugin=new TestPlugin();
        final Client client=mock(Client.class);
        final OverlayConfig config=OverlayConfig.preset("History","game");
        final DynamicChatOverlay overlay;
        Fixture() throws Exception {
            set(plugin,"client",client);set(plugin,"config",new ImprovedChatConfig(){});
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            config.setAlwaysVisible(true);config.setScrollHistory(true);config.setWidgetWidth(300);config.setMaxMessages(4);
            config.setShowInputPreview(true);config.setFadeOutDuration(1);
            config.getTextSettings().setChannelColor(1,java.awt.Color.CYAN);
            Player player=mock(Player.class);when(player.getName()).thenReturn("Alice");when(client.getLocalPlayer()).thenReturn(player);
            when(client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn("Typing stays here");
            overlay=new DynamicChatOverlay(plugin,new ImprovedChatConfig(){},client,null,new ClanChatPresentationService(),config);
            list(plugin,"overlays").add(overlay);
            for(int i=0;i<20;i++) list(plugin,"messages").add(OverlayMessage.gameMessage("Message "+i,1,ChatMessageType.GAMEMESSAGE,false));
        }
        BufferedImage render(DynamicChatOverlay target) {
            BufferedImage image=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.setClip(new Rectangle(0,0,400,200));g.translate(30,20);
            target.render(g);g.dispose();return image;
        }
        MouseWheelEvent wheel(int x,int y,int rotation,int modifiers) {
            MouseWheelEvent e=new MouseWheelEvent(new Canvas(),MouseWheelEvent.MOUSE_WHEEL,0,modifiers,x,y,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,rotation);
            plugin.onBeforeRender(new net.runelite.api.events.BeforeRender());
            plugin.mouseWheelMoved(e);return e;
        }
    }
    private static void sameRows(BufferedImage a,BufferedImage b,int startY,int endY) {
        for(int y=startY;y<endY;y++)for(int x=30;x<330;x++)assertEquals(x+","+y,a.getRGB(x,y),b.getRGB(x,y));
    }
    private static Rectangle inputRows(BufferedImage image) {
        Rectangle rows=null;
        for(int y=0;y<image.getHeight();y++)for(int x=30;x<330;x++)if(image.getRGB(x,y)==java.awt.Color.CYAN.getRGB()) {
            Rectangle pixel=new Rectangle(x,y,1,1);rows=rows==null?pixel:rows.union(pixel);
        }
        assertNotNull("Input must be visible",rows);return rows;
    }
    @Test public void renderingWheelAndLatestKeepInputAndHistoryIndependent() throws Exception {
        Fixture f=new Fixture();BufferedImage latest=f.render(f.overlay);
        assertTrue(f.wheel(60,40,-1,0).isConsumed());
        BufferedImage history=f.render(f.overlay);
        assertFalse(java.util.Arrays.equals(latest.getRGB(30,20,300,50,null,0,300),history.getRGB(30,20,300,50,null,0,300)));
        Rectangle input=inputRows(latest);
        sameRows(latest,history,input.y,input.y+input.height); // input row remains in place
        list(f.plugin,"messages").add(OverlayMessage.gameMessage("New arrival",1,ChatMessageType.GAMEMESSAGE,false));
        sameRows(history,f.render(f.overlay),20,input.y+input.height); // arrival does not move the history page
        f.plugin.onOverlayMenuClicked(new OverlayMenuClicked(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,"Latest","History"),f.overlay));
        BufferedImage resumed=f.render(f.overlay);
        assertFalse(java.util.Arrays.equals(history.getRGB(30,20,300,50,null,0,300),resumed.getRGB(30,20,300,50,null,0,300)));
        BufferedImage preview=new BufferedImage(760,240,BufferedImage.TYPE_INT_RGB);Graphics2D g=preview.createGraphics();
        g.setColor(new java.awt.Color(35,35,35));g.fillRect(0,0,760,240);g.setColor(java.awt.Color.WHITE);
        g.drawString("Following latest messages",20,20);g.drawImage(latest,0,30,null);
        g.setColor(java.awt.Color.WHITE);g.drawString("Scrolled history; typing stays fixed",400,20);g.drawImage(history,380,30,null);g.dispose();
        javax.imageio.ImageIO.write(preview,"png",new java.io.File("build/overlay-scrollback-preview.png"));
    }
    @Test public void wheelPassesThroughOutsideHiddenDisabledPreviewAndMenuStates() throws Exception {
        Fixture f=new Fixture(); f.render(f.overlay);
        assertFalse(f.wheel(5,5,-1,0).isConsumed());
        for(int modifier:new int[]{InputEvent.ALT_DOWN_MASK,InputEvent.CTRL_DOWN_MASK,InputEvent.SHIFT_DOWN_MASK})assertFalse(f.wheel(60,40,-1,modifier).isConsumed());
        when(f.client.isMenuOpen()).thenReturn(true);assertFalse(f.wheel(60,40,-1,0).isConsumed());when(f.client.isMenuOpen()).thenReturn(false);
        f.config.setShow(false);assertFalse(f.wheel(60,40,-1,0).isConsumed());f.config.setShow(true);
        f.config.setScrollHistory(false);assertFalse(f.wheel(60,40,-1,0).isConsumed());f.config.setScrollHistory(true);
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);assertFalse(f.wheel(60,40,-1,0).isConsumed());when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        f.overlay.preview();f.render(f.overlay);assertFalse(f.wheel(60,40,-1,0).isConsumed());
    }
    @Test public void lastPaintedOverlappingOverlayGetsTheWheel() throws Exception {
        Fixture f=new Fixture();
        OverlayConfig other=OverlayConfig.preset("Other","game");other.setAlwaysVisible(true);other.setScrollHistory(true);other.setMaxMessages(4);other.setWidgetWidth(300);
        DynamicChatOverlay second=new DynamicChatOverlay(f.plugin,new ImprovedChatConfig(){},f.client,null,new ClanChatPresentationService(),other);
        list(f.plugin,"overlays").add(second);
        BufferedImage baseline=f.render(f.overlay);f.render(second);
        assertTrue(f.wheel(60,40,-1,0).isConsumed());sameRows(baseline,f.render(f.overlay),20,100);
    }
    @Test public void limitsFiltersAndBothClearActionsApplyToArchivedMessages() throws Exception {
        Fixture f=new Fixture();f.plugin.clearAllMessages();
        for(int i=0;i<5100;i++)f.plugin.onChatMessage(new ChatMessage(null,ChatMessageType.GAMEMESSAGE,"","captured "+i,"",0));
        assertEquals(5000,list(f.plugin,"messages").size());
        assertEquals(1000,f.plugin.getHistoryForOverlay(f.config).size());f.config.setHistoryLimit(5000);
        assertEquals("captured 100",f.plugin.getHistoryForOverlay(f.config).get(0).getMessage());
        f.config.setFilterMode(OverlayFilterMode.SHOW_ONLY_MATCHES);f.config.setFilteredWords("captured 5099");
        assertEquals(1,f.plugin.getHistoryForOverlay(f.config).size());
        f.plugin.onOverlayMenuClicked(new OverlayMenuClicked(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,"Clear","History"),f.overlay));
        assertTrue(f.plugin.getHistoryForOverlay(f.config).isEmpty());assertEquals(5000,list(f.plugin,"messages").size());
        f.plugin.clearMessagesForTypes(java.util.EnumSet.of(ChatMessageType.GAMEMESSAGE));assertTrue(list(f.plugin,"messages").isEmpty());
        f.config.setHistoryLimit(-100);assertEquals(200,f.config.getHistoryLimit());f.config.setHistoryLimit(Integer.MAX_VALUE);assertEquals(5000,f.config.getHistoryLimit());
    }
    @Test public void wheelNeverReadsClientOrWidgetsEvenWhenNativeChatIsOpen() throws Exception {
        Fixture f=new Fixture(); f.config.setAlwaysVisible(false);
        net.runelite.api.widgets.Widget area=mock(net.runelite.api.widgets.Widget.class);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Chatbox.CHATAREA)).thenReturn(area);
        when(area.isHidden()).thenReturn(true); f.render(f.overlay);
        f.plugin.onBeforeRender(new net.runelite.api.events.BeforeRender());
        clearInvocations(f.client,area);
        MouseWheelEvent outside=new MouseWheelEvent(new Canvas(),MouseWheelEvent.MOUSE_WHEEL,0,0,390,190,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,-1);
        javax.swing.SwingUtilities.invokeAndWait(() -> f.plugin.mouseWheelMoved(outside));
        assertFalse(outside.isConsumed()); verifyNoInteractions(f.client,area);
        when(area.isHidden()).thenReturn(false); f.plugin.onBeforeRender(new net.runelite.api.events.BeforeRender());
        clearInvocations(f.client,area);
        MouseWheelEvent inside=new MouseWheelEvent(new Canvas(),MouseWheelEvent.MOUSE_WHEEL,0,0,60,40,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,-1);
        javax.swing.SwingUtilities.invokeAndWait(() -> f.plugin.mouseWheelMoved(inside));
        assertFalse(inside.isConsumed()); verifyNoInteractions(f.client,area);
    }

    @Test public void historyDoesNotAddAnInstructionRowOrReserveFooterSpace() throws Exception {
        Fixture f=new Fixture();f.config.setFadeOutDuration(0);f.config.setShowInputPreview(false);f.config.setDynamicHeight(false);
        BufferedImage history=f.render(f.overlay);f.config.setScrollHistory(false);BufferedImage normal=f.render(f.overlay);
        assertArrayEquals(normal.getRGB(0,0,400,200,null,0,400),history.getRGB(0,0,400,200,null,0,400));
    }

}
