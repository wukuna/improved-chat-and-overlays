package com.improvedchat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.runelite.api.ChatMessageType;

public final class MessageColorRuleEngine {
    private static volatile ImprovedChatConfig config;
    private static final Map<String, List<SolidRule>> RULE_CACHE = new LinkedHashMap<>();

    private MessageColorRuleEngine() {}

    public static void configure(ImprovedChatConfig newConfig) {
        config = newConfig;
        synchronized (RULE_CACHE) { RULE_CACHE.clear(); }
    }

    public static Color colorFor(String rawMessage, ChatMessageType type) {
        return colorFor(rawMessage, type, config);
    }

    public static Color colorFor(String rawMessage, ChatMessageType type, ImprovedChatConfig c) {
        if (c == null || rawMessage == null || rawMessage.isEmpty() || !c.enableMessageColorRules()) return null;
        if (!c.includePlayerChatInColorRules() && isPlayerChat(type)) return null;

        String plain = plainText(rawMessage);
        if (plain.isEmpty()) return null;
        for (SolidRule rule : rules(c.messageColorRules())) {
            if (rule.rule.matches(plain)) return colorForGroup(c, rule.group);
        }
        return null;
    }

    public static boolean flashFor(String rawMessage, ChatMessageType type) {
        return flashFor(rawMessage, type, config);
    }

    public static boolean flashFor(String rawMessage, ChatMessageType type, ImprovedChatConfig c) {
        if (c == null || rawMessage == null || rawMessage.isEmpty() || !c.enableMessageColorRules()) return false;
        if (!c.includePlayerChatInColorRules() && isPlayerChat(type)) return false;
        String plain = plainText(rawMessage);
        if (plain.isEmpty()) return false;
        for (SolidRule rule : rules(c.messageColorRules())) {
            if (rule.flash && rule.rule.matches(plain)) return true;
        }
        return false;
    }

    static String forceColor(String message, Color color, ChatMessageType type) {
        String prefix = "";
        String suffix = "";
        String body = message;
        if ((type == ChatMessageType.CLAN_GIM_MESSAGE || type == ChatMessageType.CLAN_GIM_CHAT)
                && body.startsWith("|")) {
            prefix = "|";
            body = body.substring(1);
        }
        int pipe = body.indexOf('|');
        if (pipe >= 0 && (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.CLAN_MESSAGE
                || type == ChatMessageType.CLAN_GIM_MESSAGE)) {
            prefix += body.substring(0, pipe + 1);
            body = body.substring(pipe + 1);
        } else if (pipe >= 0 && type == ChatMessageType.BROADCAST) {
            suffix = body.substring(pipe);
            body = body.substring(0, pipe);
        }

        body = body.replaceAll("(?i)</?col(?:=[0-9a-f]{6}|NORMAL|HIGHLIGHT)?[^>]*>", "");
        String hex = String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return prefix + "<col=" + hex + ">" + body + "</col>" + suffix;
    }

    public static String plainText(String text) {
        if (text == null) return "";
        String out = text;
        if (out.startsWith("CA_ID:")) {
            int pipe = out.indexOf('|');
            if (pipe >= 0 && pipe + 1 < out.length()) out = out.substring(pipe + 1);
        }
        return out.replaceAll("(?i)<(?!lt>|gt>|at>)[^>]+>", "")
                .replace("<lt>", "<")
                .replace("<gt>", ">")
                .replace("<at>", "@")
                .replace('\u00a0', ' ')
                .trim();
    }

    private static boolean isPlayerChat(ChatMessageType type) {
        return type == ChatMessageType.PRIVATECHAT
            || type == ChatMessageType.PRIVATECHATOUT
            || type == ChatMessageType.MODPRIVATECHAT
            || type == ChatMessageType.PUBLICCHAT
            || type == ChatMessageType.MODCHAT
            || type == ChatMessageType.AUTOTYPER
            || type == ChatMessageType.MODAUTOTYPER
            || type == ChatMessageType.FRIENDSCHAT
            || type == ChatMessageType.CLAN_CHAT
            || type == ChatMessageType.CLAN_GUEST_CHAT
            || type == ChatMessageType.CLAN_GIM_CHAT;
    }

    private static Color colorForGroup(ImprovedChatConfig c, int group) {
        switch (group) {
            case 1: return c.messageRuleColor1();
            case 2: return c.messageRuleColor2();
            case 3: return c.messageRuleColor3();
            case 4: return c.messageRuleColor4();
            case 5: return c.messageRuleColor5();
            case 6: return c.messageRuleColor6();
            case 7: return c.messageRuleColor7();
            case 8: return c.messageRuleColor8();
            case 9: return c.messageRuleColor9();
            default: return null;
        }
    }

    private static List<SolidRule> rules(String source) {
        source = BoundedPattern.source(source);
        synchronized (RULE_CACHE) {
            List<SolidRule> hit = RULE_CACHE.get(source);
            if (hit != null) return hit;
            List<SolidRule> out = new ArrayList<>();
            for (String line : BoundedPattern.lines(source)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                boolean flash = false;
                if (line.length() >= 7 && line.regionMatches(true, line.length() - 7, "::flash", 0, 7)) {
                    flash = true;
                    line = line.substring(0, line.length() - 7).trim();
                }
                int sep = line.lastIndexOf("::");
                if (sep <= 0 || sep + 2 >= line.length()) continue;
                String expression = line.substring(0, sep).trim();
                try {
                    int group = Integer.parseInt(line.substring(sep + 2).trim());
                    if (group < 1 || group > 9 || expression.isEmpty()) continue;
                    TextRule rule = TextRule.compile(expression);
                    if (rule != null) out.add(new SolidRule(rule, group, flash));
                } catch (NumberFormatException ignored) {

                }
            }
            if (RULE_CACHE.size() >= 64) RULE_CACHE.remove(RULE_CACHE.keySet().iterator().next());
            RULE_CACHE.put(source, out);
            return out;
        }
    }

    private static final class SolidRule {
        final TextRule rule;
        final int group;
        final boolean flash;
        SolidRule(TextRule rule, int group, boolean flash) { this.rule = rule; this.group = group; this.flash = flash; }
    }

    private static final class TextRule {
        final String literalLower;
        final Pattern regex;
        private TextRule(String literalLower, Pattern regex) { this.literalLower = literalLower; this.regex = regex; }

        static TextRule compile(String expression) {
            if (expression.regionMatches(true, 0, "regex:", 0, 6)) {
                String pattern = expression.substring(6).trim();
                if (pattern.isEmpty()) return null;
                try {
                    return new TextRule(null, BoundedPattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (PatternSyntaxException ignored) { return null; }
            }
            return new TextRule(expression.toLowerCase(Locale.ROOT), null);
        }

        boolean matches(String text) {
            return regex != null ? BoundedPattern.find(regex, text) : text.toLowerCase(Locale.ROOT).contains(literalLower);
        }
    }
}
