package com.improvedchat.chatbox.resize;

import com.improvedchat.ImprovedChatConfig;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChatHudRelayoutRegressionTest {
    private static final int STOCK_RESERVE = 165;

    @Test
    public void nativeRelayoutCannotScheduleItselfAgainEveryFrame() {
        for (boolean modern : new boolean[] {false, true}) {
            Fixture f = new Fixture(modern);
            f.anchors.sync(16);
            assertTrue(f.anchors.consumeLayoutChanged());

            for (int frame = 0; frame < 300; frame++) {
                f.hud.reserve = STOCK_RESERVE;
                f.anchors.sync(16);
                assertEquals(STOCK_RESERVE + 16, f.hud.reserve);
                assertFalse("Stable size queued another rebuild on frame " + frame,
                    f.anchors.consumeLayoutChanged());
            }
            assertEquals(301, f.hud.revalidations);
            assertEquals(301, f.modalSlot.revalidations);
        }
    }

    @Test
    public void changedSizesStillRequestOneRelayout() {
        Fixture f = new Fixture(true);
        for (int delta : new int[] {16, 32, -100, 0, 16}) {
            f.anchors.sync(delta);
            assertTrue(f.anchors.consumeLayoutChanged());
            f.anchors.sync(delta);
            assertFalse(f.anchors.consumeLayoutChanged());
            assertEquals(Math.max(0, STOCK_RESERVE + delta), f.hud.reserve);
        }
    }

    @Test
    public void recreatedHudAndLayoutSwitchStillRefit() {
        Fixture f = new Fixture(true);
        f.anchors.sync(16);
        assertTrue(f.anchors.consumeLayoutChanged());
        f.hud = new TestWidget(f.hud.id, f.root.widget, f.modalSlot.widget);
        f.anchors.sync(16);
        assertTrue(f.anchors.consumeLayoutChanged());
        f.anchors.sync(16);
        assertFalse(f.anchors.consumeLayoutChanged());
        f.topLevel = InterfaceID.TOPLEVEL_OSRS_STRETCH;
        f.hud = new TestWidget(InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT,
            f.root.widget, f.modalSlot.widget);
        f.anchors.sync(16);
        assertTrue(f.anchors.consumeLayoutChanged());
    }

    @Test
    public void disableAndReenableResetTheTarget() {
        Fixture f = new Fixture(true);
        f.anchors.sync(16);
        assertTrue(f.anchors.consumeLayoutChanged());
        f.anchors.restore();
        assertEquals(STOCK_RESERVE, f.hud.reserve);
        assertTrue(f.anchors.consumeLayoutChanged());
        f.anchors.sync(16);
        assertTrue(f.anchors.consumeLayoutChanged());
        f.anchors.sync(16);
        assertFalse(f.anchors.consumeLayoutChanged());
    }

    private static class Fixture {
        int topLevel;
        final TestWidget root = new TestWidget(-1, null);
        final TestWidget modalSlot = new TestWidget(-2, root.widget);
        TestWidget hud;
        final RuneLiteHudAnchors anchors;

        Fixture(boolean modern) {
            topLevel = modern ? InterfaceID.TOPLEVEL_PRE_EOC : InterfaceID.TOPLEVEL_OSRS_STRETCH;
            hud = new TestWidget(RuneLiteHudAnchors.frontIdForTopLevel(topLevel),
                root.widget, modalSlot.widget);
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[] {Client.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getWidget": return ((int) a[0]) == hud.id ? hud.widget : null;
                        case "getTopLevelInterfaceId": return topLevel;
                        case "isResized": return true;
                        default: return defaultValue(m.getReturnType());
                    }
                });
            anchors = new RuneLiteHudAnchors(client, new ImprovedChatConfig() {
                @Override public boolean growInterfaces() { return true; }
            }, null);
        }
    }

    private static class TestWidget {
        final int id;
        final Widget widget;
        int reserve = STOCK_RESERVE;
        int revalidations;

        TestWidget(int id, Widget parent, Widget... children) {
            this.id = id;
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
                new Class<?>[] {Widget.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getId": return id;
                        case "getParent": return parent;
                        case "getOriginalHeight": return reserve;
                        case "setOriginalHeight": reserve = (int) a[0]; return null;
                        case "revalidate": revalidations++; return null;
                        case "getStaticChildren": return children;
                        case "getDynamicChildren": return new Widget[0];
                        default: return defaultValue(m.getReturnType());
                    }
                });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
