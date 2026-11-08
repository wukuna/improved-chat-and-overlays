package com.improvedchat.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Node;

final class OverlayBackgroundImage {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PIXELS = 8_000_000;
    private static final int MAX_FRAMES = 300;
    private static final long MAX_GIF_WORK_PIXELS = 128_000_000;
    private static final int RENDER_PIXELS = 512 * 512;
    private static final int ANIMATION_PIXELS = 4 * 1024 * 1024;
    final boolean gif;
    final long bytes;
    private final BufferedImage[] frames;
    private final long[] ends;
    private final int plays;

    private OverlayBackgroundImage(boolean gif, BufferedImage[] frames, long[] ends, int plays) {
        this.gif = gif;
        this.frames = frames;
        this.ends = ends;
        this.plays = plays;
        long size = 0;
        for (BufferedImage frame : frames) size += 4L * frame.getWidth() * frame.getHeight();
        bytes = size;
    }

    BufferedImage frame(long elapsedNanos) {
        if (frames.length == 1) return frames[0];
        long elapsed = Math.max(0, elapsedNanos);
        long duration = ends[ends.length - 1];
        if (plays > 0 && elapsed / duration >= plays) return frames[frames.length - 1];
        long time = elapsed % duration;
        int low = 0, high = ends.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (time < ends[mid]) high = mid;
            else low = mid + 1;
        }
        return frames[low];
    }

    static byte[] readBytes(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Choose a PNG, JPEG or GIF smaller than 10 MB.");
        return bytes;
    }

    static OverlayBackgroundImage read(byte[] bytes) throws IOException {
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Choose a PNG, JPEG or GIF image.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                boolean gif = "gif".equalsIgnoreCase(format);
                if (!gif && !"png".equalsIgnoreCase(format) && !"jpeg".equalsIgnoreCase(format))
                    throw new IOException("Choose a PNG, JPEG or GIF image.");
                reader.setInput(stream, false, false);
                if (gif) return readGif(reader);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                checkSize(width, height);
                BufferedImage decoded = reader.read(0);
                try {
                    return new OverlayBackgroundImage(false, new BufferedImage[]{normalize(decoded, RENDER_PIXELS)}, new long[]{1}, 1);
                } finally { decoded.flush(); }
            } finally { reader.dispose(); }
        }
    }

    private static OverlayBackgroundImage readGif(ImageReader reader) throws IOException {
        Node stream = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
        Node screen = child(stream, "LogicalScreenDescriptor");
        int width = number(screen, "logicalScreenWidth"), height = number(screen, "logicalScreenHeight");
        checkSize(width, height);
        int count = reader.getNumImages(true);
        if (count < 1 || count > MAX_FRAMES) throw new IOException("Choose a GIF with at most 300 frames.");
        if ((long) width * height * count > MAX_GIF_WORK_PIXELS)
            throw new IOException("This GIF is too large to animate. Choose a smaller or shorter GIF.");
        FrameInfo[] info = new FrameInfo[count];
        int plays = 1;
        for (int i = 0; i < count; i++) {
            Node metadata = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
            info[i] = new FrameInfo(metadata);
            FrameInfo frame = info[i];
            if (frame.width < 1 || frame.height < 1 || frame.x < 0 || frame.y < 0
                    || (long) frame.x + frame.width > width || (long) frame.y + frame.height > height)
                throw new IOException("This GIF has invalid frame dimensions.");
            Node extensions = child(metadata, "ApplicationExtensions");
            for (Node extension = extensions == null ? null : extensions.getFirstChild(); extension != null; extension = extension.getNextSibling()) {
                String application = attribute(extension, "applicationID");
                String authentication = attribute(extension, "authenticationCode");
                if (("NETSCAPE".equals(application) && "2.0".equals(authentication))
                        || ("ANIMEXTS".equals(application) && "1.0".equals(authentication))) {
                    Object data = ((IIOMetadataNode) extension).getUserObject();
                    if (data instanceof byte[] && ((byte[]) data).length >= 3 && ((byte[]) data)[0] == 1) {
                        byte[] loop = (byte[]) data;
                        int repeats = (loop[1] & 255) | ((loop[2] & 255) << 8);
                        plays = repeats == 0 ? 0 : repeats + 1;
                    }
                }
            }
        }
        int background = background(stream);
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        BufferedImage[] frames = new BufferedImage[count];
        long[] ends = new long[count];
        long duration = 0;
        try {
            if (!info[0].transparent) {
                graphics.setColor(new Color(background, true));
                graphics.fillRect(0, 0, width, height);
            }
            for (int i = 0; i < count; i++) {
                FrameInfo frame = info[i];
                int[] previous = "restoreToPrevious".equals(frame.disposal)
                        ? canvas.getRGB(frame.x, frame.y, frame.width, frame.height, null, 0, frame.width) : null;
                BufferedImage decoded = reader.read(i);
                try {
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(decoded, frame.x, frame.y, null);
                } finally { decoded.flush(); }
                frames[i] = normalize(canvas, Math.min(RENDER_PIXELS, ANIMATION_PIXELS / count));
                duration += (frame.delay < 2 ? 100L : frame.delay * 10L) * 1_000_000L;
                ends[i] = duration;
                if (previous != null) canvas.setRGB(frame.x, frame.y, frame.width, frame.height, previous, 0, frame.width);
                else if ("restoreToBackgroundColor".equals(frame.disposal)) {
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.setColor(new Color(frame.transparent ? 0 : background, true));
                    graphics.fillRect(frame.x, frame.y, frame.width, frame.height);
                }
            }
        } finally { graphics.dispose(); canvas.flush(); }
        return new OverlayBackgroundImage(true, frames, ends, plays);
    }

    private static int background(Node stream) throws IOException {
        Node table = child(stream, "GlobalColorTable");
        if (table == null) return 0;
        int index = number(table, "backgroundColorIndex");
        for (Node color = table.getFirstChild(); color != null; color = color.getNextSibling()) {
            if (number(color, "index") == index)
                return new Color(number(color, "red"), number(color, "green"), number(color, "blue")).getRGB();
        }
        return 0;
    }

    private static void checkSize(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > MAX_PIXELS)
            throw new IOException("Choose an image with at most 8 million pixels and sides no longer than 8192 pixels.");
    }

    private static BufferedImage normalize(BufferedImage decoded, int pixels) {
        int width = decoded.getWidth(), height = decoded.getHeight();
        double scale = Math.min(1, Math.min(1024.0 / Math.max(width, height), Math.sqrt(pixels / ((double) width * height))));
        BufferedImage result = new BufferedImage(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(decoded, 0, 0, result.getWidth(), result.getHeight(), null);
        } finally { graphics.dispose(); }
        return result;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (name.equals(node.getNodeName())) return node;
        return null;
    }

    private static String attribute(Node node, String name) {
        Node value = node == null ? null : node.getAttributes().getNamedItem(name);
        return value == null ? "" : value.getNodeValue();
    }

    private static int number(Node node, String name) throws IOException {
        try { return Integer.parseInt(attribute(node, name)); }
        catch (NumberFormatException ex) { throw new IOException("This GIF has invalid metadata.", ex); }
    }

    private static final class FrameInfo {
        final int x, y, width, height, delay;
        final String disposal;
        final boolean transparent;
        FrameInfo(Node metadata) throws IOException {
            Node descriptor = child(metadata, "ImageDescriptor");
            x = number(descriptor, "imageLeftPosition"); y = number(descriptor, "imageTopPosition");
            width = number(descriptor, "imageWidth"); height = number(descriptor, "imageHeight");
            Node control = child(metadata, "GraphicControlExtension");
            delay = control == null ? 0 : number(control, "delayTime");
            disposal = attribute(control, "disposalMethod");
            transparent = "TRUE".equals(attribute(control, "transparentColorFlag"));
        }
    }
}
