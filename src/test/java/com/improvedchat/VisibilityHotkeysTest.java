package com.improvedchat;

import com.google.gson.Gson;
import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.overlay.OverlayConfigStore;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.*;
import javax.swing.SwingUtilities;
import net.runelite.client.config.Keybind;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisibilityHotkeysTest {
    private static final Keybind KEY = new Keybind(KeyEvent.VK_F7, KeyEvent.CTRL_DOWN_MASK);
    private static KeyEvent event(int type) {
        return new KeyEvent(new Canvas(), type, 0, KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_F7, KeyEvent.CHAR_UNDEFINED) {
            @Override public int getExtendedKeyCode() { return getKeyCode(); }
        };
    }
    @Test public void sharedKeyFansOutOnAwtAndDoesNotRepeatWhenHeld() throws Exception {
        List<VisibilityHotkeys.Activation> calls = new ArrayList<>();
        VisibilityHotkeys keys = new VisibilityHotkeys(calls::add);
        OverlayConfig a = new OverlayConfig(), b = new OverlayConfig(), other = new OverlayConfig();
        a.setToggleKeybind(KEY); b.setToggleKeybind(KEY); b.setShow(false);
        keys.configure(Arrays.asList(a,b,other), KEY);
        SwingUtilities.invokeAndWait(() -> {
            KeyEvent press=event(KeyEvent.KEY_PRESSED); keys.keyPressed(press); assertTrue(press.isConsumed());
            keys.keyPressed(event(KeyEvent.KEY_PRESSED));
        });
        assertEquals(1,calls.size());
        assertEquals(Arrays.asList(a.getId(),b.getId()),calls.get(0).overlayIds);
        assertTrue(calls.get(0).hideChatbox);
        keys.keyReleased(event(KeyEvent.KEY_RELEASED)); keys.keyPressed(event(KeyEvent.KEY_PRESSED));
        assertEquals(2,calls.size());

        a.setShow(false); keys.configure(Arrays.asList(a,b,other),KEY);
        assertTrue(keys.isCurrent(calls.get(1)));
    }
    @Test public void changedBindingsProfileResetAndFocusLossInvalidateOrRelease() {
        List<VisibilityHotkeys.Activation> calls = new ArrayList<>();
        VisibilityHotkeys keys = new VisibilityHotkeys(calls::add);
        OverlayConfig overlay = new OverlayConfig(); overlay.setToggleKeybind(KEY);
        keys.configure(Collections.singletonList(overlay),Keybind.NOT_SET);
        keys.keyPressed(event(KeyEvent.KEY_PRESSED)); keys.focusLost(); keys.keyPressed(event(KeyEvent.KEY_PRESSED));
        assertEquals(2,calls.size()); keys.reset(); assertFalse(keys.isCurrent(calls.get(0)));
        KeyEvent cleared=event(KeyEvent.KEY_PRESSED); keys.keyPressed(cleared); assertFalse(cleared.isConsumed());
        keys.configure(Collections.singletonList(overlay),Keybind.NOT_SET);
        keys.keyPressed(event(KeyEvent.KEY_PRESSED));
        overlay.setToggleKeybind(new Keybind(KeyEvent.VK_F8,0)); keys.configure(Collections.singletonList(overlay),Keybind.NOT_SET);
        assertFalse(keys.isCurrent(calls.get(2)));
    }
    @Test public void bindingsRoundTripAndOldProfilesDefaultToUnbound() {
        Gson gson = new Gson(); OverlayConfig overlay = new OverlayConfig(); overlay.setToggleKeybind(KEY);
        OverlayConfig loaded=OverlayConfigStore.read(gson,gson.toJson(Collections.singletonList(overlay))).get(0);
        assertEquals(KEY,loaded.getToggleKeybind());
        assertEquals(Keybind.NOT_SET,OverlayConfigStore.read(gson,"[{}]").get(0).getToggleKeybind());
    }
}
