package com.improvedchat.chatbox;

import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/** Client-thread ownership of the regular chat area and complete button bar. */
@Singleton
public final class ChatboxVisibility {
    @Inject private Client client;
    @Inject private com.improvedchat.chatbox.resize.ChatDialogBoxes dialogs;
    private final Map<Widget, Boolean> owned = new IdentityHashMap<>();
    private boolean hidden;
    private int scriptDepth;

    public boolean isHidden() { return hidden; }

    public void toggle() { hidden = !hidden; apply(); }

    public void reset() { hidden = false; scriptDepth = 0; restore(); }

    private void restore() {
        owned.forEach((widget, previous) -> {
            if (widget.isSelfHidden()) widget.setHidden(previous);
        });
        owned.clear();
    }

    private void hide(int id) {
        Widget widget = client.getWidget(id);
        if (widget == null) return;
        owned.put(widget, widget.isSelfHidden());
        widget.setHidden(true);
    }

    private void apply() {
        restore();
        if (!hidden || client.getGameState() != GameState.LOGGED_IN || scriptDepth != 0) return;
        hide(InterfaceID.Chatbox.CONTROLS);
        // CHATAREA also contains dialogue prompts. Keep those usable while hiding regular chat.
        if (dialogs.isDialogOpen()) {
            hide(InterfaceID.Chatbox.CHATDISPLAY);
        } else {
            hide(InterfaceID.Chatbox.CHATAREA);
        }
    }

    @Subscribe(priority = -20) public void onBeforeRender(BeforeRender event) {
        scriptDepth = 0;
        apply();
    }

    @Subscribe(priority = 20) public void onScriptPreFired(ScriptPreFired event) {
        // Let native scripts see native visibility and capture their new state afterwards.
        if (scriptDepth++ == 0) restore();
    }

    @Subscribe(priority = -20) public void onScriptPostFired(ScriptPostFired event) {
        if (scriptDepth > 0 && --scriptDepth == 0) apply();
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
            reset();
    }
}
