package com.improvedchat.overlay;

import com.improvedchat.MessageColorRuleEngine;
import com.improvedchat.model.AttentionTrigger;
import com.improvedchat.model.OverlayMessage;
import java.awt.Color;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.util.Text;

public final class OverlayNotifications {
    private OverlayNotifications() { }

    public static boolean eligible(OverlayConfig config, OverlayMessage message, List<OverlayMessage> visible) {
        if (config == null || message == null || !config.isShow() || !config.getNotification().isEnabled()
                || message.isOutgoing() || message.getType() == ChatMessageType.CONSOLE) return false;

        if (visible.stream().noneMatch(item -> item.getSequence() == message.getSequence())) return false;
        return config.getNotificationTrigger() == AttentionTrigger.ALL_NEW_MESSAGES
                || MessageColorRuleEngine.flashFor(message.getMessage(), message.getType(), config.getTextSettings());
    }

    public static String message(OverlayConfig config, OverlayMessage message) {
        String sender = message.getSender() == null ? "" : Text.removeTags(message.getSender()) + ": ";
        String body = Text.removeTags(message.getMessage()).replace('\n', ' ').replace('\r', ' ');
        String result = "[" + config.getName() + "] " + sender + body;
        return result.length() > 500 ? result.substring(0, 497) + "..." : result;
    }

    public static Notification normalize(Notification value) {
        Notification n = value == null ? Notification.OFF : value;
        String name = n.getSoundName();

        if (name != null && (name.length() > 128 || name.contains("/") || name.contains("\\")
                || name.contains(":") || name.contains(".."))) name = null;
        if (name != null && name.trim().isEmpty()) name = null;
        return n.withSound(n.getSound() == null ? NotificationSound.NATIVE : n.getSound())
                .withRequestFocus(n.getRequestFocus() == null ? RequestFocusType.OFF : n.getRequestFocus())
                .withFlash(n.getFlash() == null ? FlashNotification.DISABLED : n.getFlash())
                .withFlashColor(n.getFlashColor() == null ? Color.RED : n.getFlashColor())
                .withVolume(Math.max(0, Math.min(100, n.getVolume())))
                .withTimeout(Math.max(0, n.getTimeout())).withSoundName(name);
    }
}
