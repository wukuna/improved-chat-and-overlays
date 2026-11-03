/*
 * Adapted portions from Chat Resizer by shanktank.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2026, shanktank
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.improvedchat.chatbox.resize;

import com.improvedchat.chatbox.resize.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.annotations.Component;
import net.runelite.api.annotations.Interface;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetUtil;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatDialogBoxes {
    @Interface private static final int CHATBOX_GROUP = WidgetUtil.componentToInterface(InterfaceID.Chatbox.UNIVERSE);
    @Component private static final int[] DIALOGS_TO_CENTER = {
        InterfaceID.MembershipBenefitsPrompt.UNIVERSE,
        InterfaceID.Chatmenu.OPTIONS,
    };

    private final Client client;

    private boolean dialogOpen;

    @Inject
    ChatDialogBoxes(Client client) {
        this.client = client;
    }

    void reset() {
        dialogOpen = isDialogOpen();
    }

    public boolean isDialogOpen() {
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return false;

        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != InputType.NONE.getType()) return true;

        Widget mesLayer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
        if (mesLayer != null && !mesLayer.isHidden()) return true;

        for (WidgetNode node : client.getComponentTable()) {
            if (WidgetUtil.componentToInterface((int) node.getHash()) == CHATBOX_GROUP) return true;
        }

        return false;
    }

    void centerDialogs() {
        alignMounted();
        for (int id : DIALOGS_TO_CENTER) {
            Widget dialog = client.getWidget(id);
            Widget parent = dialog == null ? null : dialog.getParent();
            if (parent == null) continue;
            place(dialog, Math.max(0, (parent.getWidth() - dialog.getWidth()) / 2),
                          Math.max(0, (parent.getHeight() - dialog.getHeight()) / 2));
        }
    }

    void resetDialogPositions() {
        alignMounted();
        for (int id : DIALOGS_TO_CENTER) {
            Widget dialog = client.getWidget(id);
            Widget parent = dialog == null ? null : dialog.getParent();
            if (parent != null) align(dialog, parent);
        }
    }

    private void alignMounted() {
        for (WidgetNode node : client.getComponentTable()) {
            if (WidgetUtil.componentToInterface((int) node.getHash()) != CHATBOX_GROUP) continue;
            Widget slot = client.getWidget((int) node.getHash());
            Widget root = Widgets.mountedRoot(client, node.getId());
            if (slot != null && root != null) align(root, slot);
        }
    }

    private static void align(Widget widget, Widget slot) {
        int x = aligned(widget.getXPositionMode(), slot.getWidth(), widget.getWidth(), widget.getOriginalX());
        int y = aligned(widget.getYPositionMode(), slot.getHeight(), widget.getHeight(), widget.getOriginalY());
        if (x >= 0 && y >= 0) place(widget, x, y);
    }

    private static int aligned(int mode, int slotSize, int size, int original) {
        if (mode == WidgetPositionMode.ABSOLUTE_LEFT) return original;
        if (mode == WidgetPositionMode.ABSOLUTE_CENTER) return Math.max(0, (slotSize - size) / 2 + original);
        if (mode == WidgetPositionMode.ABSOLUTE_RIGHT) return Math.max(0, slotSize - size - original);
        return -1;
    }

    private static void place(Widget widget, int x, int y) {
        if (x != widget.getRelativeX() || y != widget.getRelativeY()) widget.setForcedPosition(x, y);
    }

    boolean dialogOpenStateChanged() {
        boolean open = isDialogOpen();
        boolean changed = open != dialogOpen;
        dialogOpen = open;
        return changed;
    }
}
