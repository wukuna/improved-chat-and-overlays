package com.improvedchat.input;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InputDockLayout {
    public final List<ChatInputMode> modes;
    public final List<Rectangle> buttons;
    public final Dimension size;
    public final Font font;
    public final double scale;

    public InputDockLayout(Graphics2D graphics, List<ChatInputMode> modes, int percent, boolean vertical) {
        this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
        scale = Math.max(60, Math.min(200, percent)) / 100.0;
        font = new Font("Arial", Font.PLAIN, 13).deriveFont((float) (13 * scale));
        FontMetrics metrics = graphics.getFontMetrics(font);
        int inset = px(6), gap = px(4), height = px(34);
        List<Integer> widths = new ArrayList<>();
        int widest = 0;
        for (ChatInputMode mode : modes) {
            int width = metrics.stringWidth(mode.label) + px(47);
            widths.add(width);
            widest = Math.max(widest, width);
        }
        List<Rectangle> rectangles = new ArrayList<>();
        int x = inset, y = inset;
        for (int width : widths) {
            rectangles.add(new Rectangle(x, y, vertical ? widest : width, height));
            if (vertical) y += height + gap;
            else x += width + gap;
        }
        buttons = Collections.unmodifiableList(rectangles);
        size = modes.isEmpty() ? new Dimension() : vertical
                ? new Dimension(widest + inset * 2, y - gap + inset)
                : new Dimension(x - gap + inset, height + inset * 2);
    }

    public int px(double value) { return (int) Math.round(value * scale); }

    public Frame attach(InputDockPlacement placement, int width, int height, int inset) {
        if (placement == InputDockPlacement.LEFT || placement == InputDockPlacement.RIGHT) {
            int totalHeight = Math.max(height, size.height + inset * 2);
            int extra = size.width + inset;
            return new Frame(new Dimension(width + extra, totalHeight),
                    new Rectangle(placement == InputDockPlacement.LEFT ? extra : 0, 0, width, totalHeight),
                    new Rectangle(placement == InputDockPlacement.LEFT ? inset : width,
                            (totalHeight - size.height) / 2, size.width, size.height));
        }
        int totalWidth = Math.max(width, size.width + inset * 2);
        return new Frame(new Dimension(totalWidth, height + size.height + inset),
                new Rectangle(0, 0, totalWidth, height),
                new Rectangle((totalWidth - size.width) / 2, height, size.width, size.height));
    }

    public static final class Frame {
        public final Dimension size;
        public final Rectangle content;
        public final Rectangle dock;
        Frame(Dimension size, Rectangle content, Rectangle dock) {
            this.size = size; this.content = content; this.dock = dock;
        }
    }
}
