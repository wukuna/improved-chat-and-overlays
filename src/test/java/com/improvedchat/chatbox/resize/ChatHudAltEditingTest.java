package com.improvedchat.chatbox.resize;

import com.improvedchat.ImprovedChatConfig;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChatHudAltEditingTest
{
    @Test public void altAnchorHeightCannotLeakIntoTicksScriptsOrNextFrameLayout()
    {
        for (int top : new int[]{InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.TOPLEVEL_PRE_EOC})
        {
            Fixture f=new Fixture(top);
            f.anchors.sync(-165);
            f.anchors.dragHotkeyListener.hotkeyPressed();
            AtomicInteger tickHeight=new AtomicInteger(),scriptHeight=new AtomicInteger(),frameHeight=new AtomicInteger();
            f.bus.register(ClientTick.class,e->tickHeight.set(f.height.get()),0);
            f.bus.register(ScriptPreFired.class,e->scriptHeight.set(f.height.get()),0);
            f.bus.register(BeforeRender.class,e->{frameHeight.set(f.height.get());f.anchors.presentAnchorHeight();},0);
            for (int frame=0;frame<60;frame++)
            {
                f.anchors.indicatorPresent.render(null);
                f.anchors.indicatorRestore.render(null);
                assertEquals(500,f.height.get());
                if (frame%3==0)
                {
                    f.bus.post(new ClientTick());
                    assertEquals("Bank tick must use layout height",665,tickHeight.get());
                    f.bus.post(new ScriptPreFired(net.runelite.api.ScriptID.BANKMAIN_SIZE_CHECK));
                    assertEquals(665,scriptHeight.get());
                }
                else if (frame%3==1)
                {
                    f.bus.post(new ScriptPreFired(net.runelite.api.ScriptID.BANKMAIN_BUILD));
                    assertEquals("Scripts may run before ClientTick",665,scriptHeight.get());
                }
                f.bus.post(new BeforeRender());
                assertEquals("Layout must be restored even without a client tick",665,frameHeight.get());
                assertEquals(648,f.bankHeight.get());
                assertEquals(0,f.reserve.get());
            }
        }
    }

    @Test public void altEditingRestoresLayoutHeightBeforeBankClampsItsWindow()
    {
        for (int top : new int[]{InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.TOPLEVEL_PRE_EOC})
        {
            Fixture f=new Fixture(top);
            f.anchors.sync(-165);
            f.anchors.dragHotkeyListener.hotkeyPressed();
            for (int frame=0;frame<30;frame++)
            {
                f.anchors.presentAnchorHeight();
                assertEquals(500,f.height.get());
                f.anchors.indicatorRestore.render(null);
                assertEquals(500,f.height.get());
                f.bus.post(new BeforeRender());
                assertEquals(665,f.height.get());
                assertEquals(648,f.bankHeight.get());
                assertEquals(0,f.reserve.get());
            }
            f.anchors.presentAnchorHeight();
            f.anchors.dragHotkeyListener.hotkeyReleased();
            f.anchors.indicatorRestore.render(null);
            assertEquals(665,f.height.get());
            verify(f.hud,never()).setHidden(anyBoolean());
        }
    }

    @Test public void largerChatStillLimitsTheBankAndDisablingRestoresNativeHeight()
    {
        Fixture f=new Fixture(InterfaceID.TOPLEVEL_PRE_EOC);
        f.anchors.sync(80);
        f.anchors.dragHotkeyListener.hotkeyPressed();
        f.anchors.presentAnchorHeight();
        f.bus.post(new BeforeRender());
        assertEquals(420,f.height.get());
        assertEquals(420,f.bankHeight.get());
        f.anchors.restore();
        assertEquals(165,f.reserve.get());
        assertEquals(500,f.height.get());
        f.bus.post(new BeforeRender());
        assertEquals(500,f.bankHeight.get());
    }

    private static final class Fixture
    {
        final AtomicInteger reserve=new AtomicInteger(165),height=new AtomicInteger(500),bankHeight=new AtomicInteger();
        final Widget hud=mock(Widget.class),root=mock(Widget.class);
        final EventBus bus=new EventBus();
        final RuneLiteHudAnchors anchors;

        Fixture(int top)
        {
            Client client=mock(Client.class);ImprovedChatConfig config=mock(ImprovedChatConfig.class);
            when(config.growInterfaces()).thenReturn(true);
            when(client.getTopLevelInterfaceId()).thenReturn(top);
            when(client.isResized()).thenReturn(true);
            when(client.getWidget(RuneLiteHudAnchors.frontIdForTopLevel(top))).thenReturn(hud);
            when(hud.getParent()).thenReturn(root);when(root.getHeight()).thenReturn(665);
            when(hud.getOriginalHeight()).thenAnswer(c->reserve.get());
            when(hud.getHeight()).thenAnswer(c->height.get());
            doAnswer(c->{reserve.set(c.getArgument(0));return null;}).when(hud).setOriginalHeight(anyInt());
            doAnswer(c->{height.set(c.getArgument(0));return null;}).when(hud).setHeight(anyInt());
            doAnswer(c->{height.set(665-reserve.get());return null;}).when(hud).revalidate();
            anchors=new RuneLiteHudAnchors(client,config,null);
            bus.register(anchors);
            bus.register(anchors.beforeDrawRestore);
            bus.register(BeforeRender.class,e->bankHeight.set(Math.min(648,hud.getHeight())),-20);
        }
    }
}
