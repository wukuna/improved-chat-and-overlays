package com.improvedchat.overlay;

import com.improvedchat.ChatDisplayText;
import com.improvedchat.MessageColorRuleEngine;

public final class OverlayTextReplacements {
    private OverlayTextReplacements() { }

    public static String displayText(OverlayConfig config, String original) {
        if (config == null || !config.isMessageReplacerEnabled() || original == null
                || config.getTextReplacements().isEmpty()) return original;
        String plain = MessageColorRuleEngine.plainText(ChatDisplayText.expandTemplates(original));
        for (OverlayTextReplacement rule : config.getTextReplacements()) {
            if (rule != null && rule.matches(plain)) return escape(rule.getReplacement());
        }
        return original;
    }

    private static String escape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') result.append("<lt>");
            else if (c == '>') result.append("<gt>");
            else if (c == '\n') result.append("<br>");
            else if (c != '\r') result.append(c);
        }
        return result.toString();
    }
}
