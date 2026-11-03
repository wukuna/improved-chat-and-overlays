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

package com.improvedchat.chatbox.resize.internal;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import java.awt.Rectangle;

public final class Widgets {
    private Widgets() {}

    public static Widget chatSlot(Client client) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        return universe == null ? null : universe.getParent();
    }

    public static Widget mountedRoot(Client client, int group) {
        Widget root = client.getWidget(group, 0);
        if (root == null) return null;
        for (Widget up = root.getParent(); up != null && WidgetUtil.componentToInterface(up.getId()) == group; up = up.getParent()) root = up;
        return root;
    }

    public static Rectangle liveBounds(Widget widget) {
        int x = widget.getRelativeX();
        int y = widget.getRelativeY();

        for (Widget up = widget.getParent(); up != null; up = up.getParent()) {
            x += up.getRelativeX();
            y += up.getRelativeY();
        }
        return new Rectangle(x, y, widget.getWidth(), widget.getHeight());
    }

    public static void revalidateChildren(Widget widget) {
        revalidateAll(widget.getStaticChildren());
        revalidateAll(widget.getDynamicChildren());
    }

    static void revalidateAll(Widget[] children) {
        if (children == null) return;
        for (Widget child : children) {
            if (child == null) continue;
            child.revalidate();
            if (child.getId() == InterfaceID.Chatbox.SCROLLAREA) continue;
            revalidateChildren(child);
        }
    }

    @SuppressWarnings("deprecation") public static void setWidth(Widget widget, int width) { widget.setWidth(width); }
    @SuppressWarnings("deprecation") public static void setHeight(Widget widget, int height) { widget.setHeight(height); }
}