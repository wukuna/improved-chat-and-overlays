package com.improvedchat.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.SwingUtilities;
import net.runelite.client.util.Filepath;

public final class OverlayBackgroundImages {
    private static final long MAX_CACHED_BYTES = 64L * 1024 * 1024;
    private static final Pattern NAME = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|gif)");
    private final Map<String, Entry> images = new HashMap<>();
    private final ArrayDeque<Work> work = new ArrayDeque<>();
    private final LongSupplier clock;
    private final long maxCachedBytes;
    private long cachedBytes;
    private long generation;
    private boolean importing;
    private boolean working;

    public OverlayBackgroundImages() { this(System::nanoTime, MAX_CACHED_BYTES); }

    OverlayBackgroundImages(LongSupplier clock, long maxCachedBytes) {
        this.clock = clock;
        this.maxCachedBytes = maxCachedBytes;
    }

    @FunctionalInterface
    public interface Directory { Filepath get() throws IOException; }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public synchronized void clear() { generation++; images.clear(); cachedBytes = 0; }
    public synchronized void retain(java.util.Set<String> names) {
        images.entrySet().removeIf(entry -> {
            if (names.contains(entry.getKey())) return false;
            if (entry.getValue().image != null) cachedBytes -= entry.getValue().image.bytes;
            return true;
        });
    }
    public synchronized boolean isImporting() { return importing; }
    public synchronized String status(String name) {
        Entry entry = images.get(name);
        return entry == null || entry.image != null ? null : entry.error == null ? "Loading image…" : entry.error;
    }

    public synchronized BufferedImage get(String name, Directory directory, Executor executor, Runnable changed) {
        Entry existing = images.get(name);
        if (existing != null) return existing.image == null ? null : existing.image.frame(clock.getAsLong() - existing.started);
        if (!validName(name)) return null;
        if (images.size() >= OverlayConfigStore.MAX_OVERLAYS) return null;
        Entry pending = new Entry(null, null, 0);
        images.put(name, pending);
        long requestGeneration = generation;
        enqueue(executor, () -> {
            synchronized (this) {
                if (requestGeneration != generation || images.get(name) != pending) return;
            }
            OverlayBackgroundImage image = null;
            String error = null;
            try (InputStream input = directory.get().joinSegment(name).openInputStream()) {
                image = OverlayBackgroundImage.read(OverlayBackgroundImage.readBytes(input));
            } catch (IOException | RuntimeException ex) {
                error = "Background image unavailable. Choose the image again.";
            }
            synchronized (this) {
                if (requestGeneration != generation || images.get(name) != pending) return;
                if (image != null && cachedBytes + image.bytes > maxCachedBytes) {
                    image = null;
                    error = "Background memory limit reached. Remove an unused image or choose a smaller GIF.";
                }
                if (image != null) cachedBytes += image.bytes;
                images.put(name, new Entry(image, error, clock.getAsLong()));
            }
            refresh(requestGeneration, changed);
        }, () -> {
            synchronized (this) {
                if (requestGeneration != generation || images.get(name) != pending) return;
                images.put(name, new Entry(null, "Background loading unavailable. Choose the image again.", 0));
            }
            refresh(requestGeneration, changed);
        });
        return null;
    }

    private void refresh(long requestGeneration, Runnable changed) {
        SwingUtilities.invokeLater(() -> {
            synchronized (this) { if (requestGeneration != generation) return; }
            changed.run();
        });
    }

    public void importImage(Filepath selected, Directory directory, Executor executor, Consumer<Result> callback) {
        long requestGeneration;
        synchronized (this) {
            if (importing) return;
            importing = true;
            requestGeneration = generation;
        }
        enqueue(executor, () -> {
            Result result;
            try (InputStream input = selected.openInputStream()) {
                byte[] bytes = OverlayBackgroundImage.readBytes(input);
                OverlayBackgroundImage image = OverlayBackgroundImage.read(bytes);
                synchronized (this) {
                    if (requestGeneration != generation) throw new IOException("Background selection cancelled.");
                    if (cachedBytes + image.bytes > maxCachedBytes || images.size() >= OverlayConfigStore.MAX_OVERLAYS)
                        throw new IOException("Background memory limit reached. Remove an unused image or choose a smaller GIF.");
                }
                Filepath folder = directory.get();
                folder.createDirectories();
                String name = UUID.randomUUID() + (image.gif ? ".gif" : ".png");
                Filepath file = folder.joinSegment(name);
                try (OutputStream output = file.openOutputStream()) {
                    if (image.gif) output.write(bytes);
                    else try (MemoryCacheImageOutputStream encoded = new MemoryCacheImageOutputStream(output)) {
                        if (!ImageIO.write(image.frame(0), "png", encoded)) throw new IOException("Could not save the background image.");
                    }
                }
                synchronized (this) {
                    if (requestGeneration != generation) throw new IOException("Background selection cancelled.");
                    cachedBytes += image.bytes;
                    images.put(name, new Entry(image, null, clock.getAsLong()));
                }
                result = new Result(name, null);
            } catch (IOException | RuntimeException ex) {
                result = new Result(null, ex instanceof IOException ? ex.getMessage() : "Could not load this image.");
            }
            finishImport(callback, result);
        }, () -> finishImport(callback, new Result(null, "Background loading unavailable. Choose the image again.")));
    }

    private void finishImport(Consumer<Result> callback, Result result) {
        synchronized (this) { importing = false; }
        SwingUtilities.invokeLater(() -> callback.accept(result));
    }

    private void enqueue(Executor executor, Runnable action, Runnable rejected) {
        synchronized (this) {
            work.addLast(new Work(action, rejected));
            if (working) return;
            working = true;
        }
        try { executor.execute(this::runWork); }
        catch (RejectedExecutionException ex) {
            ArrayDeque<Work> failed;
            synchronized (this) {
                failed = new ArrayDeque<>(work);
                work.clear();
                working = false;
            }
            for (Work item : failed) item.rejected.run();
        }
    }

    private void runWork() {
        while (true) {
            Work item;
            synchronized (this) {
                item = work.pollFirst();
                if (item == null) { working = false; return; }
            }
            item.action.run();
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
        final OverlayBackgroundImage image;
        final String error;
        final long started;
        Entry(OverlayBackgroundImage image, String error, long started) {
            this.image = image; this.error = error; this.started = started;
        }
    }

    private static final class Work {
        final Runnable action, rejected;
        Work(Runnable action, Runnable rejected) { this.action = action; this.rejected = rejected; }
    }
}
