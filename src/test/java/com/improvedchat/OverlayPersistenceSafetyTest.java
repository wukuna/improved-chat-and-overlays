package com.improvedchat;

import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.overlay.OverlayConfigStore;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayPersistenceSafetyTest
{
    @Test public void blockedRecoveryDoesNotAccessOrOverwriteConfiguration() throws Exception
    {
        ImprovedChatPlugin plugin = new ImprovedChatPlugin();
        Field blocked = ImprovedChatPlugin.class.getDeclaredField("overlayPersistenceBlocked");
        blocked.setAccessible(true);
        blocked.setBoolean(plugin, true);

        plugin.saveOverlayConfigs();
    }

    @Test public void excessOverlayIsRejectedBeforeWidgetOrPersistenceChanges()
    {
        ImprovedChatPlugin plugin = new ImprovedChatPlugin();
        for (int i=0; i<OverlayConfigStore.MAX_OVERLAYS; i++) plugin.getOverlayConfigs().add(new OverlayConfig());
        plugin.addConfiguredOverlay(new OverlayConfig());
        assertEquals(64, plugin.getOverlayConfigs().size());
        assertTrue(plugin.getConfigRecoveryMessage().contains("64 overlays"));
    }
}
