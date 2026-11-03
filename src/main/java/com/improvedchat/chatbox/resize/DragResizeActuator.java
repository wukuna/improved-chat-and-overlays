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

import com.improvedchat.ImprovedChatConfig;

import com.improvedchat.chatbox.resize.internal.SizeClamps;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.HotkeyListener;
import lombok.Getter;
import lombok.Setter;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class DragResizeActuator extends MouseAdapter {
    static final int BORDER_GRAB = 6;
    static final int RIGHT_BAND_SHIFT = 1;

    private final ImprovedChatConfig config;
    private final ConfigManager configManager;
    private final SecondarySize swapSize;

    @Getter private final KeyListener keyListener;

    @Getter private volatile Point pointer;
    private volatile Rectangle bounds;
    private volatile int maxWidthChange, maxHeightChange;
    private volatile int canvasH;
    private volatile boolean modifierHeld;

    @Getter @Setter private Dimension lastDragSize;
    @Getter private volatile boolean dragging;
    @Getter private volatile boolean fixedMode;

    private volatile boolean dragTop, dragRight;
    private boolean dragFixed;
    private boolean dragSwap;
    private int startX, startY, startExtraW, startExtraH;

    @Inject
    public DragResizeActuator(ImprovedChatConfig config, ConfigManager configManager, SecondarySize swapSize) {
        this.config = config;
        this.configManager = configManager;
        this.swapSize = swapSize;
        this.keyListener = new HotkeyListener(config::dragModifier) {
            @Override public void hotkeyPressed() { modifierHeld = true; }
            @Override public void hotkeyReleased() { modifierHeld = false; }
            @Override public void focusLost() { super.focusLost(); dragging = false; }
        };
    }

    public void update(Rectangle bounds, boolean fixedMode, int canvasW, int canvasH) {
        this.bounds = bounds;
        this.fixedMode = fixedMode;
        this.maxWidthChange = SizeClamps.maxWidthChange(canvasW);
        this.maxHeightChange = SizeClamps.maxHeightChange(fixedMode, canvasH);
        this.canvasH = canvasH;
    }

    public void reset() {
        bounds = null;
        pointer = null;
        modifierHeld = false;
        dragging = false;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent e) {
        if (dragging || !modifierActive()) return e;

        Rectangle b = bounds;
        if (b == null) return e;

        boolean top = nearTop(b, e);
        boolean right = !fixedMode && nearRight(b, e);
        if (!top && !right) return e;

        dragging = true;
        dragTop = top;
        dragRight = right;
        dragFixed = fixedMode;
        dragSwap = swapSize.isActive();
        startX = e.getX();
        startY = e.getY();

        startExtraW = swapSize.effectiveWidthChange();
        startExtraH = dragFixed ? swapSize.effectiveFixedHeightChange() : swapSize.effectiveHeightChange();

        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e) {
        pointer = e.getPoint();
        if (!dragging) return e;
        applyDrag(e);
        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e) {
        if (!dragging) return e;
        applyDrag(e);
        dragging = false;
        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e) {
        pointer = e.getPoint();
        return e;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent e) {
        pointer = null;
        return e;
    }

    private void applyDrag(MouseEvent e) {
        if (dragRight) updateConfig(draggedWidthKey(), clamp(startExtraW + (e.getX() - startX), SizeClamps.MIN_WIDTH_CHANGE, maxWidthChange));
        if (dragTop) updateConfig(draggedHeightKey(), clamp(startExtraH + (startY - e.getY()), SizeClamps.MIN_HEIGHT_CHANGE, maxHeightChange));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String draggedWidthKey() {
        return dragSwap ? ImprovedChatConfig.SWAP_WIDTH_CHANGE : ImprovedChatConfig.WIDTH_CHANGE;
    }

    private String draggedHeightKey() {
        if (dragSwap) return ImprovedChatConfig.SWAP_HEIGHT_CHANGE;
        return dragFixed ? ImprovedChatConfig.FIXED_HEIGHT_CHANGE : ImprovedChatConfig.HEIGHT_CHANGE;
    }

    private void updateConfig(String key, int value) {
        Integer current = configManager.getConfiguration(ImprovedChatConfig.GROUP, key, Integer.class);
        if (current == null || current != value) configManager.setConfiguration(ImprovedChatConfig.GROUP, key, value);
    }

    private boolean nearTop(Rectangle b, MouseEvent e) {
        int y = bandY(b, BORDER_GRAB, canvasH);
        return e.getY() >= y && e.getY() <= y + BORDER_GRAB && e.getX() >= b.x - BORDER_GRAB && e.getX() <= b.x + b.width - RIGHT_BAND_SHIFT;
    }

    private static boolean nearRight(Rectangle b, MouseEvent e) {
        int outerX = b.x + b.width - RIGHT_BAND_SHIFT;
        return e.getX() >= outerX - BORDER_GRAB && e.getX() <= outerX && e.getY() >= b.y && e.getY() <= b.y + b.height + BORDER_GRAB;
    }

    private boolean modifierActive() {
        return modifierHeld && !Keybind.NOT_SET.equals(config.dragModifier());
    }

    boolean isHighlightActive() {
        return dragging || modifierActive();
    }

    boolean isDraggingTop() {
        return dragging && dragTop;
    }

    boolean isDraggingRight() {
        return dragging && dragRight;
    }

    static Rectangle rightBand(Rectangle b, int grab) {
        return new Rectangle(b.x + b.width - grab - RIGHT_BAND_SHIFT, b.y, grab + 1, b.height + grab);
    }

    static Rectangle topBand(Rectangle b, int grab, int canvasH) {
        return new Rectangle(b.x - grab, bandY(b, grab, canvasH), b.width + grab + 1 - RIGHT_BAND_SHIFT, grab);
    }

    static int bandY(Rectangle b, int grab, int canvasH) {
        return Math.min(b.y, canvasH - grab);
    }

    boolean isSizeReadoutActive() {
        if (dragging) return true;
        if (!modifierActive()) return false;
        Rectangle b = bounds;
        Point p = pointer;
        if (b == null || p == null) return false;
        if (topBand(b, BORDER_GRAB, canvasH).contains(p)) return true;
        return !fixedMode && rightBand(b, BORDER_GRAB).contains(p);
    }
}