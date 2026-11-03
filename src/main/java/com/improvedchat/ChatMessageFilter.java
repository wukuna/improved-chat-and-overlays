package com.improvedchat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.runelite.client.plugins.chatfilter.ChatFilterConfig;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;

public final class ChatMessageFilter {
    private List<Pattern> patterns = new ArrayList<>();
    private boolean stripAccents;

    public void rebuild(ChatFilterConfig cfg) {
        List<Pattern> compiled = new ArrayList<>();
        stripAccents = cfg.stripAccents();

        List<String> words = Text.fromCSV(BoundedPattern.source(cfg.filteredWords()));
        if (words.size() > BoundedPattern.MAX_RULES) BoundedPattern.reportLimit();
        words.stream()
                .limit(BoundedPattern.MAX_RULES)
                .map(this::maybeStripAccents)
                .map(s -> Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
                .forEach(compiled::add);

        for (String line : BoundedPattern.lines(cfg.filteredRegex())) {
            String trimmed = maybeStripAccents(line.trim());
            if (trimmed.isEmpty()) {
                continue;
            }
            Pattern p = compilePattern(trimmed);
            if (p != null) {
                compiled.add(p);
            }
        }

        patterns = compiled;
    }

    public boolean matches(String message) {
        if (message == null || patterns.isEmpty()) {
            return false;
        }

        String normalized = maybeStripAccents(message
                .replace('\u00A0', ' ')
                .replace("<lt>", "<")
                .replace("<gt>", ">")
                .replace("<at>", "@"));

        for (Pattern pattern : patterns) {
            if (BoundedPattern.find(pattern, normalized)) {
                return true;
            }
        }
        return false;
    }

    private String maybeStripAccents(String input) {
        return stripAccents ? StringUtils.stripAccents(input) : input;
    }

    private static Pattern compilePattern(String pattern) {
        try {
            return BoundedPattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }
}
