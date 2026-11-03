package com.improvedchat.overlay;

import com.improvedchat.MessageColorRuleEngine;
import com.improvedchat.model.AttentionSpeed;
import com.improvedchat.model.AttentionStopMode;
import com.improvedchat.model.AttentionTrigger;
import com.improvedchat.model.OverlayMessage;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AttentionEngine {
    private static final ThreadLocal<OverlayConfig> CURRENT = new ThreadLocal<>();
    private static final Map<String, AlertState> STATES = new ConcurrentHashMap<>();
    private static final Map<String, AlertState> WHOLE_OVERLAY = new ConcurrentHashMap<>();
    private static volatile long lastCleanup;
    private static volatile boolean clientFocused = true;

    private AttentionEngine() {}

    public static void beginOverlay(OverlayConfig config) {
        CURRENT.set(config);
    }

    public static void setClientFocused(boolean focused) {
        clientFocused = focused;
    }

    public static int adjustAlpha(OverlayMessage msg, long now, int baseAlpha) {
        OverlayConfig cfg = CURRENT.get();
        if (cfg == null || msg == null || !cfg.isFlashEnabled() || baseAlpha <= 0) return baseAlpha;

        AlertState currentState = stateFor(cfg, msg, now);
        maybeCleanup(now);
        if (!cfg.isFlashMessage() || currentState == null || !isActive(currentState, cfg, now, isClientFocused())) {
            return baseAlpha;
        }
        return pulsedAlpha(baseAlpha, now, currentState.startedAt, cfg.getAttentionSpeed());
    }

    public static Color adjustBackground(OverlayConfig cfg, Color base, long now) {
        Color c = base == null ? new Color(0, 0, 0, 0) : base;
        if (cfg == null || !cfg.isFlashEnabled() || !cfg.isFlashBackground()) return c;
        AlertState s = activeOverlayState(cfg, now);

        if (s == null) {
            return c.getAlpha() == 0 ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 1) : c;
        }

        int normalAlpha = c.getAlpha();
        int sourceAlpha = normalAlpha > 0 ? normalAlpha : 170;
        int pulse = pulsedAlpha(sourceAlpha, now, s.startedAt, cfg.getAttentionSpeed());

        if (normalAlpha == 0) pulse = Math.max(1, pulse - 28);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, pulse)));
    }

    public static void drawBorder(Graphics2D g, OverlayConfig cfg, int width, int height, long now) {
        if (g == null || cfg == null || width <= 1 || height <= 1) return;
        AlertState s = cfg.isFlashEnabled() && cfg.isFlashBorder() ? activeOverlayState(cfg, now) : null;
        if (!cfg.isBorderEnabled() && s == null) return;

        Color base = cfg.getBorderColour();
        int alpha = base.getAlpha();
        if (s != null) {
            int sourceAlpha = alpha > 0 ? alpha : 255;
            alpha = pulsedAlpha(sourceAlpha, now, s.startedAt, cfg.getAttentionSpeed());
        } else if (!cfg.isBorderEnabled()) {
            return;
        }

        int px = cfg.getBorderThickness().getPixels();
        int inset = Math.max(0, px / 2);
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        try {
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha))));
            g.setStroke(new BasicStroke(px));
            int borderWidth = Math.max(0, width - 1 - inset * 2);
            int borderHeight = Math.max(0, height - 1 - inset * 2);
            if (cfg.isRgbBorder() && borderWidth > 0 && borderHeight > 0) {
                RgbAnimation.drawBorder(g, inset, borderWidth, borderHeight, alpha, now, cfg.getRgbCycleSeconds(), cfg.isRoundedCorners());
            } else {
                if (cfg.isRoundedCorners()) g.drawRoundRect(inset, inset, borderWidth, borderHeight, 16, 16);
                else g.drawRect(inset, inset, borderWidth, borderHeight);
            }
        } finally {
            g.setStroke(oldStroke);
            g.setPaint(oldPaint);
        }
    }

    private static AlertState stateFor(OverlayConfig cfg, OverlayMessage msg, long now) {
        boolean focused = isClientFocused();
        boolean ruleFlash = MessageColorRuleEngine.flashFor(msg.getMessage(), msg.getType(), cfg.getTextSettings());
        boolean trigger = cfg.getAttentionTrigger() == AttentionTrigger.ALL_NEW_MESSAGES || ruleFlash;
        if (!trigger) return null;

        String key = key(cfg, msg);
        AlertState current = STATES.get(key);
        long eligibilityMs = Math.max(1000L, cfg.getFlashDurationSeconds() * 1000L);
        if (current == null && now - msg.getTimestamp() <= eligibilityMs) {
            AlertState created = new AlertState(key, cfg.getId(), msg.getTimestamp(), focused,
                    focused && !cfg.isFlashWhileFocused());
            AlertState prior = STATES.putIfAbsent(key, created);
            current = prior == null ? created : prior;
        }
        if (current != null && isActive(current, cfg, now, focused)) {
            WHOLE_OVERLAY.put(cfg.getId(), current);
        }
        return current;
    }

    private static AlertState activeOverlayState(OverlayConfig cfg, long now) {
        AlertState s = WHOLE_OVERLAY.get(cfg.getId());
        if (s == null) return null;
        if (!isActive(s, cfg, now, isClientFocused())) {
            WHOLE_OVERLAY.remove(cfg.getId(), s);
            return null;
        }
        return s;
    }

    private static boolean isActive(AlertState s, OverlayConfig cfg, long now, boolean focused) {
        if (s.suppressed) return false;

        if (!cfg.isFlashWhileFocused() && focused) {
            if (!s.startedFocused) s.focusStopped = true;
            return false;
        }

        if (!focused) s.seenUnfocused = true;
        boolean refocused = focused && ((!s.startedFocused) || s.seenUnfocused);
        if (refocused) s.focusStopped = true;

        AttentionStopMode stop = cfg.getAttentionStopMode();
        boolean timerExpired = now - s.startedAt >= cfg.getFlashDurationSeconds() * 1000L;
        if (stop == AttentionStopMode.TIMER_ONLY) return !timerExpired;
        if (stop == AttentionStopMode.REFOCUS_ONLY) return !s.focusStopped;
        return !timerExpired && !s.focusStopped;
    }

    private static int pulsedAlpha(int baseAlpha, long now, long startedAt, AttentionSpeed speed) {
        long period = speed == null ? AttentionSpeed.NORMAL.getPeriodMs() : speed.getPeriodMs();
        double angle = ((now - startedAt) % period) * (Math.PI * 2.0 / period);
        double wave = (Math.sin(angle - Math.PI / 2.0) + 1.0) * 0.5;
        double factor = 0.20 + (0.80 * wave);
        return Math.max(20, Math.min(baseAlpha, (int) Math.round(baseAlpha * factor)));
    }

    private static boolean isClientFocused() {
        return clientFocused;
    }

    private static String key(OverlayConfig cfg, OverlayMessage msg) {
        String body = msg.getMessage();
        return cfg.getId() + '|' + msg.getTimestamp() + '|' + msg.getType() + '|' + (body == null ? 0 : body.hashCode());
    }

    private static void maybeCleanup(long now) {
        if (now - lastCleanup < 10000L) return;
        lastCleanup = now;
        STATES.entrySet().removeIf(e -> now - e.getValue().startedAt > 180000L || e.getValue().focusStopped);
        WHOLE_OVERLAY.entrySet().removeIf(e -> now - e.getValue().startedAt > 180000L || e.getValue().focusStopped);
    }

    private static final class AlertState {
        final String key;
        final String overlayId;
        final long startedAt;
        final boolean startedFocused;
        final boolean suppressed;
        volatile boolean seenUnfocused;
        volatile boolean focusStopped;

        AlertState(String key, String overlayId, long startedAt, boolean startedFocused, boolean suppressed) {
            this.key = key;
            this.overlayId = overlayId;
            this.startedAt = startedAt;
            this.startedFocused = startedFocused;
            this.suppressed = suppressed;
        }
    }
}
