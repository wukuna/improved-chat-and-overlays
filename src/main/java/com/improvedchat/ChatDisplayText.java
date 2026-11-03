package com.improvedchat;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;

public final class ChatDisplayText {
    private static final Pattern TEMPLATE = Pattern.compile("<(str_[A-Za-z0-9_]+)(?:=([^<>]*))?>");

    private ChatDisplayText() {}

    public static String normalize(ChatMessageType type, String text) {
        if (text == null) return "";
        if (type == ChatMessageType.CLAN_GIM_CHAT && text.startsWith("|")) text = text.substring(1);
        else if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.CLAN_MESSAGE
                || type == ChatMessageType.CLAN_GIM_MESSAGE) {
            if (type == ChatMessageType.CLAN_GIM_MESSAGE && text.startsWith("|")) text = text.substring(1);
            int pipe = text.indexOf('|');
            if (pipe >= 0) text = text.substring(pipe + 1);
        } else if (type == ChatMessageType.BROADCAST) {
            int pipe = text.indexOf('|');
            if (pipe >= 0) text = text.substring(0, pipe);
        }
        return expandTemplates(text);
    }

    public static String expandTemplates(String text) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = TEMPLATE.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value != null) values.put(matcher.group(1), value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? "" : values.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
