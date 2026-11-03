package com.improvedchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.runelite.api.ChatMessageType;

public final class OverlayColorRuleEngine {
    public enum Kind { NONE, RAINBOW }

    public static final class Decision {
        public final Kind kind;
        public final RainbowStyle rainbowStyle;

        private Decision(Kind kind, RainbowStyle rainbowStyle) {
            this.kind = kind;
            this.rainbowStyle = rainbowStyle;
        }

        public static Decision none() { return new Decision(Kind.NONE, null); }
        public static Decision rainbow(RainbowStyle style) { return new Decision(Kind.RAINBOW, style); }
    }

    private static final Decision NONE = Decision.none();
    private static volatile ImprovedChatConfig config;
    private static final Map<String, List<TextRule>> RULE_CACHE = new LinkedHashMap<>();

    private OverlayColorRuleEngine() {}

    public static void configure(ImprovedChatConfig newConfig) {
        config = newConfig;
        synchronized (RULE_CACHE) { RULE_CACHE.clear(); }
    }

    public static ChatColorSource chatColorSource() {
        ImprovedChatConfig c = config;
        return c == null ? ChatColorSource.AUTOMATIC : c.chatColorSource();
    }

    public static Decision decide(String rawMessage, ChatMessageType type) {
        return decide(rawMessage, type, config);
    }

    public static Decision decide(String rawMessage, ChatMessageType type, ImprovedChatConfig c) {
        if (c == null || rawMessage == null || rawMessage.isEmpty() || !c.enableOverlayRainbowRules()) {
            return NONE;
        }

        String plain = plainText(rawMessage);
        if (plain.isEmpty()) return NONE;
        for (TextRule rule : rainbowRules(c.overlayRainbowRules())) {
            if (rule.matches(plain)) return Decision.rainbow(c.rainbowStyle());
        }
        return NONE;
    }

    private static List<TextRule> rainbowRules(String source) {
        source = BoundedPattern.source(source);
        synchronized (RULE_CACHE) {
            List<TextRule> hit = RULE_CACHE.get(source);
            if (hit != null) return hit;
            List<TextRule> out = new ArrayList<>();
            for (String line : BoundedPattern.lines(source)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                TextRule rule = TextRule.compile(line);
                if (rule != null) out.add(rule);
            }
            if (RULE_CACHE.size() >= 64) RULE_CACHE.remove(RULE_CACHE.keySet().iterator().next());
            RULE_CACHE.put(source, out);
            return out;
        }
    }

    public static String plainText(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)<(?!lt>|gt>|at>)[^>]+>", "")
            .replace("<lt>", "<")
            .replace("<gt>", ">")
            .replace("<at>", "@")
            .trim();
    }

    private static final class TextRule {
        final String literalLower;
        final Pattern regex;

        private TextRule(String literalLower, Pattern regex) {
            this.literalLower = literalLower;
            this.regex = regex;
        }

        static TextRule compile(String expression) {
            if (expression.regionMatches(true, 0, "regex:", 0, 6)) {
                String pattern = expression.substring(6).trim();
                if (pattern.isEmpty()) return null;
                try {
                    return new TextRule(null, BoundedPattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (PatternSyntaxException ignored) {
                    return null;
                }
            }
            return new TextRule(expression.toLowerCase(Locale.ROOT), null);
        }

        boolean matches(String text) {
            if (regex != null) return BoundedPattern.find(regex, text);
            return text.toLowerCase(Locale.ROOT).contains(literalLower);
        }
    }
}
