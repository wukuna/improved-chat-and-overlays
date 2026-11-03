package com.improvedchat.overlay;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OverlayConfigStore {
    public static final int MAX_JSON = 2_000_000;
    public static final int MAX_OVERLAYS = 64;
    private OverlayConfigStore() { }

    public static void validateSize(String json) {
        if (json == null) return;
        if (json.length() > MAX_JSON) throw new JsonParseException("Saved overlays exceed 2 million characters");
        boolean quoted = false, escaped = false;
        int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32) throw new JsonParseException("Saved overlays exceed nesting limit");
            } else if (c == '}' || c == ']') depth--;
        }
    }

    public static List<OverlayConfig> defaults() {
        return new ArrayList<>(Arrays.asList(OverlayConfig.defaultPrivateOverlay(), OverlayConfig.defaultAllOverlay()));
    }

    public static List<OverlayConfig> read(Gson gson, String json) {
        validateSize(json);
        if (json == null || json.trim().isEmpty()) return defaults();
        List<OverlayConfig> loaded = new ArrayList<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginArray();
            while (reader.hasNext()) {
                if (loaded.size() >= MAX_OVERLAYS) throw new JsonParseException("Use at most 64 overlays");
                OverlayConfig config = gson.fromJson(reader, OverlayConfig.class);
                if (config == null) throw new JsonParseException("Expected an overlay");
                loaded.add(config);
            }
            reader.endArray();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("Unexpected trailing content");
        } catch (IOException | IllegalStateException ex) {
            throw new JsonParseException("Expected an overlay list", ex);
        }
        Set<String> ids = new HashSet<>();
        for (OverlayConfig config : loaded) {
            if (!ids.add(config.getId())) throw new JsonParseException("Duplicate overlay ID");
            config.normalize();
        }
        return loaded;
    }
}
