package com.improvedchat.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.SwingUtilities;
import net.runelite.client.util.Filepath;

public final class OverlayBackgroundImages {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PIXELS = 8_000_000;
    private static final int RENDER_PIXELS = 512 * 512;
    private final Map<String, Entry> images = new HashMap<>();
    private long generation;
    private boolean importing;

    @FunctionalInterface
    public interface Directory { Filepath get() throws IOException; }

    public static boolean validName(String name) {
        return name != null && name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png");
    }

    public synchronized void clear() { generation++; images.clear(); }
    public synchronized void retain(java.util.Set<String> names) { images.keySet().retainAll(names); }
    public synchronized boolean isImporting() { return importing; }
    public synchronized String status(String name) {
        Entry entry = images.get(name);
        return entry == null || entry.image != null ? null : entry.error == null ? "Loading image…" : entry.error;
    }

    public synchronized BufferedImage get(String name, Directory directory, Executor executor, Runnable changed) {
        if (!validName(name)) return null;
        Entry existing = images.get(name);
        if (existing != null) return existing.image;
        if (images.size() >= OverlayConfigStore.MAX_OVERLAYS) return null;
        Entry pending = new Entry(null, null);
        images.put(name, pending);
        long requestGeneration = generation;
        executor.execute(() -> {
            Entry loaded;
            try (InputStream input = directory.get().joinSegment(name).openInputStream()) {
                loaded = new Entry(read(input), null);
            } catch (IOException | RuntimeException ex) {
                loaded = new Entry(null, "Background image unavailable. Choose the image again.");
            }
            synchronized (this) {
                if (requestGeneration != generation || images.get(name) != pending) return;
                images.put(name, loaded);
            }
            SwingUtilities.invokeLater(changed);
        });
        return null;
    }

    public void importImage(Filepath selected, Directory directory, Executor executor, Consumer<Result> callback) {
        synchronized (this) {
            if (importing) return;
            importing = true;
        }
        executor.execute(() -> {
            Result result;
            try (InputStream input = selected.openInputStream()) {
                BufferedImage image = read(input);
                Filepath folder = directory.get();
                folder.createDirectories();
                String name = UUID.randomUUID() + ".png";
                Filepath file = folder.joinSegment(name);
                try (OutputStream output = file.openOutputStream();
                        MemoryCacheImageOutputStream encoded = new MemoryCacheImageOutputStream(output)) {
                    if (!ImageIO.write(image, "png", encoded)) throw new IOException("Could not save the background image.");
                }
                result = new Result(name, null);
            } catch (IOException | RuntimeException ex) {
                result = new Result(null, ex instanceof IOException ? ex.getMessage() : "Could not load this image.");
            }
            synchronized (this) { importing = false; }
            Result completed = result;
            SwingUtilities.invokeLater(() -> callback.accept(completed));
        });
    }

    static BufferedImage read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Choose a PNG or JPEG image smaller than 10 MB.");
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Choose a PNG or JPEG image.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"png".equalsIgnoreCase(format) && !"jpeg".equalsIgnoreCase(format))
                    throw new IOException("Choose a PNG or JPEG image.");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > MAX_PIXELS)
                    throw new IOException("Choose an image with at most 8 million pixels and sides no longer than 8192 pixels.");
                BufferedImage decoded = reader.read(0);
                double scale = Math.min(1, Math.min(1024.0 / Math.max(width, height), Math.sqrt(RENDER_PIXELS / ((double) width * height))));
                BufferedImage result = new BufferedImage(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = result.createGraphics();
                try {
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(decoded, 0, 0, result.getWidth(), result.getHeight(), null);
                } finally { graphics.dispose(); decoded.flush(); }
                return result;
            } finally { reader.dispose(); }
        }
    }

    public static void draw(Graphics2D graphics, BufferedImage image, Color background, int width, int height) {
        if (background.getAlpha() == 0 || width < 1 || height < 1) return;
        Graphics2D layer = (Graphics2D) graphics.create();
        try {
            layer.clipRect(0, 0, width, height);
            if (image == null) {
                layer.setColor(background);
                layer.fillRect(0, 0, width, height);
            } else {
                float parentAlpha = layer.getComposite() instanceof AlphaComposite ? ((AlphaComposite) layer.getComposite()).getAlpha() : 1;
                layer.setComposite(AlphaComposite.SrcOver.derive(parentAlpha * background.getAlpha() / 255f));
                layer.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
                int w = (int) Math.ceil(image.getWidth() * scale), h = (int) Math.ceil(image.getHeight() * scale);
                layer.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
            }
        } finally { layer.dispose(); }
    }

    public static final class Result {
        public final String name, error;
        Result(String name, String error) { this.name = name; this.error = error; }
    }

    private static final class Entry {
        final BufferedImage image;
        final String error;
        Entry(BufferedImage image, String error) { this.image = image; this.error = error; }
    }
}
