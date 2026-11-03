package com.improvedchat.overlay;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.model.PlacementMode;
import java.awt.Dimension;
import java.awt.Point;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayInterfaceLayerTest {
    private static final int[] LAYOUTS = {InterfaceID.TOPLEVEL, InterfaceID.TOPLEVEL_PRE_EOC, InterfaceID.TOPLEVEL_OSRS_STRETCH};

    @Test public void realManagerKeepsSavedDraggedSnappedAndPlayerOverlaysBehindInterfaces() throws Exception {
        for (int layout : LAYOUTS) {
            Client client = mock(Client.class);
            when(client.isResized()).thenReturn(layout != InterfaceID.TOPLEVEL);
            when(client.getTopLevelInterfaceId()).thenReturn(layout);
            ConfigManager settings = mock(ConfigManager.class);
            Point saved = new Point(120, 150);
            Dimension size = new Dimension(300, 120);
            when(settings.getConfiguration(eq(RuneLiteConfig.GROUP_NAME), anyString(), eq(Point.class))).thenReturn(saved);
            when(settings.getConfiguration(eq(RuneLiteConfig.GROUP_NAME), anyString(), eq(Dimension.class))).thenReturn(size);
            OverlayManager manager = manager(client, settings);
            OverlayConfig config = new OverlayConfig();
            DynamicChatOverlay overlay = new DynamicChatOverlay(mock(ImprovedChatPlugin.class), new ImprovedChatConfig(){},
                    client, null, new ClanChatPresentationService(), config);
            manager.add(overlay);
            assertEquals(saved, overlay.getPreferredLocation());
            assertEquals(size, overlay.getPreferredSize());
            assertBehind(manager, overlay);

            overlay.setPreferredLocation(new Point(300, 200));
            manager.saveOverlay(overlay);
            assertBehind(manager, overlay);
            overlay.setPreferredPosition(OverlayPosition.TOP_RIGHT);
            manager.saveOverlay(overlay);
            assertBehind(manager, overlay);
            manager.onProfileChanged(new ProfileChanged());
            assertEquals(saved, overlay.getPreferredLocation());
            assertBehind(manager, overlay);

            for (PlacementMode mode : PlacementMode.values()) {
                config.setPlacementMode(mode);
                overlay.setOverlayConfig(config);
                manager.remove(overlay);
                manager.add(overlay);
                overlay.preview();
                assertBehind(manager, overlay);
            }
        }
    }

    private static OverlayManager manager(Client client, ConfigManager settings) throws Exception {
        Constructor<?> constructor = OverlayManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] types = constructor.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = types[i] == Client.class ? client : types[i] == ConfigManager.class ? settings : mock(types[i]);
        }
        return (OverlayManager) constructor.newInstance(args);
    }

    private static void assertBehind(OverlayManager manager, Overlay overlay) throws Exception {
        Method layer = OverlayManager.class.getDeclaredMethod("getLayer", OverlayLayer.class);
        layer.setAccessible(true);
        assertTrue(((Collection<?>) layer.invoke(manager, OverlayLayer.ABOVE_SCENE)).contains(overlay));
        assertFalse(((Collection<?>) layer.invoke(manager, OverlayLayer.ALWAYS_ON_TOP)).contains(overlay));
        Method after = OverlayManager.class.getDeclaredMethod("getForInterface", int.class);
        after.setAccessible(true);
        for (int layout : LAYOUTS) {
            assertFalse("Must never be promoted above a game interface", ((Collection<?>) after.invoke(manager, layout)).contains(overlay));
        }
    }
}
