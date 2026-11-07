package com.improvedchat.overlay;

import com.improvedchat.model.AnimatedTextStyle;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.Paint;
import java.awt.geom.Point2D;

public final class RgbAnimation {
    private static final float[] STOPS = {0f, 1f / 6, 2f / 6, 3f / 6, 4f / 6, 5f / 6, 1f};

    private static final java.util.Map<java.awt.Rectangle, BorderPath> BORDER_PATHS =
            new java.util.LinkedHashMap<java.awt.Rectangle, BorderPath>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<java.awt.Rectangle, BorderPath> eldest) {
            return size() > 32;
        }
    };

    private static synchronized BorderPath borderPath(int inset, int width, int height) {
        return BORDER_PATHS.computeIfAbsent(new java.awt.Rectangle(inset, inset, width, height), BorderPath::new);
    }

    private static final class BorderPath {
        private final java.util.List<java.awt.geom.Line2D.Double> lines = new java.util.ArrayList<>();
        private final java.util.List<Double> lengths = new java.util.ArrayList<>();
        private double length;

        private BorderPath(java.awt.Rectangle bounds) {
            java.awt.geom.PathIterator path = new java.awt.geom.RoundRectangle2D.Double(
                    bounds.x, bounds.y, bounds.width, bounds.height, 16, 16).getPathIterator(null, .3);
            double[] coords = new double[6];
            Point2D.Double first = null, previous = null;
            while (!path.isDone()) {
                int kind = path.currentSegment(coords);
                Point2D.Double point = kind == java.awt.geom.PathIterator.SEG_CLOSE ? first
                        : new Point2D.Double(coords[0], coords[1]);
                if (previous != null && kind != java.awt.geom.PathIterator.SEG_MOVETO) {
                    double distance = previous.distance(point);
                    if (distance > 0) {
                        lines.add(new java.awt.geom.Line2D.Double(previous, point));
                        lengths.add(distance);
                        length += distance;
                    }
                }
                if (kind == java.awt.geom.PathIterator.SEG_MOVETO) first = point;
                previous = point;
                path.next();
            }
        }
    }

    private RgbAnimation() { }

    static double phase(long now, int seconds) {
        long period = Math.max(2, Math.min(30, seconds)) * 1000L;
        return Math.floorMod(now, period) / (double) period;
    }

    private static Color hue(double value, int alpha) {
        Color c = Color.getHSBColor((float) (value - Math.floor(value)), 1f, 1f);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static Paint gradient(double x, double y, double dx, double dy,
            double length, double distance, double phase, int alpha, OverlayColorSettings settings) {
        boolean duo = settings != null && settings.getMode() == AnimatedTextStyle.DUO_WAVE;
        float[] stops = duo ? new float[]{0f, .5f, 1f} : STOPS;
        Color[] colors = new Color[stops.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = duo ? ChatRenderUtils.withAlpha(i == 1 ? settings.getSecondary() : settings.getPrimary(), alpha)
                    : hue(stops[i], alpha);
        }
        double offset = distance - phase * length;
        Point2D start = new Point2D.Double(x - dx * offset, y - dy * offset);
        Point2D end = new Point2D.Double(start.getX() + dx * length, start.getY() + dy * length);
        return new LinearGradientPaint(start, end, stops, colors, CycleMethod.REPEAT);
    }

    static void drawBorder(Graphics2D g, int inset, int width, int height, int alpha, long now, int seconds) {
        drawBorder(g,inset,width,height,alpha,now,seconds,false);
    }

    static void drawBorder(Graphics2D g, int inset, int width, int height, int alpha, long now, int seconds, boolean rounded) {
        drawBorder(g, inset, width, height, alpha, now, seconds, rounded, null);
    }

    static void drawBorder(Graphics2D g, int inset, int width, int height, int alpha, long now,
            int seconds, boolean rounded, OverlayColorSettings settings) {
        if (width <= 0 || height <= 0) return;
        if (rounded) {
            BorderPath path = borderPath(inset, width, height);
            double distance = 0, phase = phase(now, seconds);
            for (int i = 0; i < path.lines.size(); i++) {
                java.awt.geom.Line2D.Double line = path.lines.get(i);
                double segment = path.lengths.get(i);
                g.setPaint(gradient(line.x1, line.y1, (line.x2 - line.x1) / segment,
                        (line.y2 - line.y1) / segment, path.length, distance, phase, alpha, settings));
                g.draw(line);
                distance += segment;
            }
            return;
        }
        double perimeter = 2.0 * (width + height);
        double phase = phase(now, seconds);
        int right = inset + width, bottom = inset + height;
        g.setPaint(gradient(inset, inset, 1, 0, perimeter, 0, phase, alpha, settings));
        g.drawLine(inset, inset, right, inset);
        g.setPaint(gradient(right, inset, 0, 1, perimeter, width, phase, alpha, settings));
        g.drawLine(right, inset, right, bottom);
        g.setPaint(gradient(right, bottom, -1, 0, perimeter, width + height, phase, alpha, settings));
        g.drawLine(right, bottom, inset, bottom);
        g.setPaint(gradient(inset, bottom, 0, -1, perimeter, 2.0 * width + height, phase, alpha, settings));
        g.drawLine(inset, bottom, inset, inset);
    }

    static int drawText(Graphics2D g, String text, int alpha, int x, int y,
            boolean shadow, FontMetrics metrics, OverlayConfig cfg, long now, int originX) {
        return drawText(g, text, alpha, x, y, shadow, metrics, cfg, now, originX, cfg.getAnimatedTextStyle());
    }

    static Color cycleColor(OverlayConfig cfg, long now, int alpha) {
        double phase = phase(now, cfg.getRgbCycleSeconds());
        if (!cfg.isCustomRgbCycle()) return hue(-phase, alpha);
        double mix = (1 - Math.cos(phase * 2 * Math.PI)) / 2;
        Color a = cfg.getRgbColor1(), b = cfg.getRgbColor2();
        return new Color((int)Math.round(a.getRed() + (b.getRed()-a.getRed())*mix),
                (int)Math.round(a.getGreen() + (b.getGreen()-a.getGreen())*mix),
                (int)Math.round(a.getBlue() + (b.getBlue()-a.getBlue())*mix), Math.max(0,Math.min(255,alpha)));
    }

    static int drawText(Graphics2D g, String text, int alpha, int x, int y,
            boolean shadow, FontMetrics metrics, OverlayConfig cfg, long now, int originX, AnimatedTextStyle style) {
        OverlayColorSettings settings = cfg.getTextColors();
        if (style == AnimatedTextStyle.RGB_CYCLE && cfg.isCustomRgbCycle()) {
            Color old = g.getColor();
            Paint oldPaint = g.getPaint();
            try {
                if (shadow) {
                    g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, alpha))));
                    g.drawString(text, x + 2, y + 1);
                }
                g.setColor(cycleColor(cfg, now, alpha));
                g.drawString(text, x + 1, y);
                return metrics.stringWidth(text);
            } finally { g.setColor(old); g.setPaint(oldPaint); }
        }
        return drawText(g, text, alpha, x, y, shadow, metrics, settings, now, originX, style);
    }

    static Color cycleColor(OverlayColorSettings settings, AnimatedTextStyle style, long now, int alpha) {
        double phase = phase(now, settings.getCycleSeconds());
        if (style == AnimatedTextStyle.RGB_CYCLE) return hue(-phase, alpha);
        if (style == AnimatedTextStyle.NORMAL || style == AnimatedTextStyle.RGB_STATIC)
            return ChatRenderUtils.withAlpha(settings.getPrimary(), alpha);
        double mix = (1 - Math.cos(phase * 2 * Math.PI)) / 2;
        Color a = settings.getPrimary(), b = settings.getSecondary();
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * mix),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * mix),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * mix), Math.max(0, Math.min(255, alpha)));
    }

    static int drawText(Graphics2D g, String text, int alpha, int x, int y,
            boolean shadow, FontMetrics metrics, OverlayColorSettings settings, long now, int originX, AnimatedTextStyle style) {
        Paint old = g.getPaint();
        try {
            if (shadow) {
                g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, alpha))));
                g.drawString(text, x + 2, y + 1);
            }
            double phase = phase(now, settings.getCycleSeconds());
            g.setPaint(style == AnimatedTextStyle.RGB_CYCLE || style == AnimatedTextStyle.DUO_CYCLE
                    ? cycleColor(settings, style, now, alpha)
                    : gradient(originX, y, 1, 0, 240, 0, phase, alpha,
                            style == AnimatedTextStyle.DUO_WAVE ? settings : null));
            g.drawString(text, x + 1, y);
            return metrics.stringWidth(text);
        } finally {
            g.setPaint(old);
        }
    }
}
