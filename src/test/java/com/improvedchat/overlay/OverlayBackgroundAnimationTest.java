package com.improvedchat.overlay;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.SwingUtilities;
import net.runelite.client.util.Filepath;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayBackgroundAnimationTest {
    private static final String NAME = "12345678-1234-1234-1234-123456789abc.gif";
    private static final String SECOND = "22345678-1234-1234-1234-123456789abc.gif";
    private static final long MS = 1_000_000L;

    @Test public void animationHonorsDelaysAndLoopBoundaries() throws Exception {
        OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(4, 2, 0,
                frame(4, 2, 1).delay(10), frame(4, 2, 2).delay(30)));
        assertEquals(Color.RED.getRGB(), image.frame(0).getRGB(0, 0));
        assertSame(image.frame(0), image.frame(99 * MS));
        assertEquals(Color.GREEN.getRGB(), image.frame(100 * MS).getRGB(0, 0));
        assertSame(image.frame(100 * MS), image.frame(399 * MS));
        assertSame(image.frame(0), image.frame(400 * MS));
        assertSame(image.frame(0), image.frame(4_000_000_000L * MS));
        assertSame(image.frame(0), image.frame(-1));
    }

    @Test public void finiteAndMissingLoopExtensionsStopOnLastFrame() throws Exception {
        for (int repeat : new int[]{-1, 1, 2}) {
            OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(4, 2, repeat,
                    frame(4, 2, 1).delay(10), frame(4, 2, 2).delay(10)));
            long end = 200L * (repeat < 0 ? 1 : repeat + 1) * MS;
            assertEquals(Color.GREEN.getRGB(), image.frame(end).getRGB(0, 0));
            assertSame(image.frame(end), image.frame(Long.MAX_VALUE));
            if (repeat > 0) assertEquals(Color.RED.getRGB(), image.frame(200 * MS).getRGB(0, 0));
        }
    }

    @Test public void zeroAndOneHundredthDelaysHaveSafePlaybackTiming() throws Exception {
        OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(2, 2, 0,
                frame(2, 2, 1).delay(0), frame(2, 2, 2).delay(1), frame(2, 2, 3).delay(2)));
        assertEquals(Color.RED.getRGB(), image.frame(99 * MS).getRGB(0, 0));
        assertEquals(Color.GREEN.getRGB(), image.frame(100 * MS).getRGB(0, 0));
        assertEquals(Color.BLUE.getRGB(), image.frame(200 * MS).getRGB(0, 0));
        assertEquals(Color.RED.getRGB(), image.frame(220 * MS).getRGB(0, 0));
    }

    @Test public void partialFramesRespectRestorePreviousAndTransparentBackgroundDisposal() throws Exception {
        OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(4, 2, 0,
                frame(4, 2, 1), frame(1, 1, 2).at(1, 0).dispose("restoreToPrevious"),
                frame(1, 1, 3).at(2, 0).dispose("restoreToBackgroundColor").transparent(),
                frame(1, 1, 4).at(3, 0)));
        assertEquals(Color.GREEN.getRGB(), image.frame(100 * MS).getRGB(1, 0));
        assertEquals(Color.RED.getRGB(), image.frame(200 * MS).getRGB(1, 0));
        assertEquals(Color.BLUE.getRGB(), image.frame(200 * MS).getRGB(2, 0));
        assertEquals(0, image.frame(300 * MS).getRGB(2, 0));
        assertEquals(Color.WHITE.getRGB(), image.frame(300 * MS).getRGB(3, 0));
        assertEquals(Color.RED.getRGB(), image.frame(300 * MS).getRGB(0, 1));
        assertEquals(Color.RED.getRGB(), image.frame(400 * MS).getRGB(2, 0));
    }

    @Test public void opaqueBackgroundDisposalUsesGlobalPaletteAndTransparentPixelsKeepPreviousFrame() throws Exception {
        Frame transparent = frame(2, 1, 0).at(1, 0).transparent();
        transparent.image.getRaster().setSample(1, 0, 0, 3);
        OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(4, 2, 0,
                frame(4, 2, 1), frame(1, 1, 2).at(0, 0).dispose("restoreToBackgroundColor"), transparent));
        assertEquals(Color.BLACK.getRGB(), image.frame(200 * MS).getRGB(0, 0));
        assertEquals(Color.RED.getRGB(), image.frame(200 * MS).getRGB(1, 0));
        assertEquals(Color.BLUE.getRGB(), image.frame(200 * MS).getRGB(2, 0));
    }

    @Test public void transparentFirstFrameAndEveryAnimatedFrameKeepOpacityAndRoundedClip() throws Exception {
        OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(20, 20, 0,
                frame(10, 10, 1).at(5, 5).transparent(), frame(20, 20, 2).transparent()));
        assertEquals(0, image.frame(0).getRGB(0, 0));
        for (long time : new long[]{0, 100 * MS}) {
            BufferedImage output = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            graphics.setClip(new RoundRectangle2D.Double(0, 0, 20, 20, 12, 12));
            OverlayBackgroundImages.draw(graphics, image.frame(time), new Color(0, 0, 0, 128), 20, 20);
            graphics.dispose();
            assertEquals(0, output.getRGB(0, 0));
            assertEquals(128, output.getRGB(10, 10) >>> 24);
        }
    }

    @Test public void manyFramesAreScaledToAnOverallSixteenMegabyteBudget() throws Exception {
        Frame[] frames = new Frame[80];
        for (int i = 0; i < frames.length; i++) frames[i] = frame(300, 200, i % 3 + 1);
        OverlayBackgroundImage image = OverlayBackgroundImage.read(gif(300, 200, 0, frames));
        assertTrue(image.bytes <= 16L * 1024 * 1024);
        assertTrue(image.frame(0).getWidth() < 300);
        assertEquals(1.5, image.frame(0).getWidth() / (double) image.frame(0).getHeight(), .02);
    }

    @Test public void excessiveFramesCanvasWorkAndOutOfBoundsFramesAreRejected() throws Exception {
        Frame[] many = new Frame[301];
        for (int i = 0; i < many.length; i++) many[i] = frame(1, 1, 1);
        byte[] oversized = gif(4, 2, 0, frame(4, 2, 1));
        oversized[6] = 0; oversized[7] = 32; oversized[8] = 0; oversized[9] = 32;
        Frame[] expensive = new Frame[200];
        for (int i = 0; i < expensive.length; i++) expensive[i] = frame(1, 1, 1);
        for (byte[] bytes : new byte[][]{gif(1, 1, 0, many), oversized,
                gif(1000, 1000, 0, expensive), gif(4, 2, 0, frame(2, 1, 1).at(3, 0))}) {
            try { OverlayBackgroundImage.read(bytes); fail("Unsafe GIF accepted"); }
            catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }

    @Test public void animatedCacheAdvancesWithoutFileReadsTasksOrUiRefreshes() throws Exception {
        AtomicLong clock = new AtomicLong();
        OverlayBackgroundImages images = new OverlayBackgroundImages(clock::get, 1024);
        List<Runnable> pending = new ArrayList<>(); List<String> changed = new ArrayList<>();
        Filepath folder = mock(Filepath.class), file = mock(Filepath.class);
        byte[] data = gif(4, 2, 0, frame(4, 2, 1), frame(4, 2, 2));
        when(folder.joinSegment(NAME)).thenReturn(file);
        when(file.openInputStream()).thenAnswer(i -> new ByteArrayInputStream(data));
        Runnable refresh = () -> changed.add("refresh");
        assertNull(images.get(NAME, () -> folder, pending::add, refresh));
        pending.remove(0).run(); flushEdt();
        BufferedImage first = images.get(NAME, () -> folder, pending::add, refresh);
        clock.set(100 * MS);
        for (int i = 0; i < 10_000; i++) assertEquals(Color.GREEN.getRGB(), images.get(NAME, () -> folder, pending::add, refresh).getRGB(0, 0));
        clock.set(200 * MS);
        assertSame(first, images.get(NAME, () -> folder, pending::add, refresh));
        flushEdt();
        assertEquals(1, changed.size()); assertTrue(pending.isEmpty());
        verify(file, times(1)).openInputStream();
    }

    @Test public void combinedCacheBudgetIsEnforcedAndRetainReleasesIt() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages(() -> 0, 64);
        List<Runnable> pending = new ArrayList<>();
        Filepath folder = mock(Filepath.class), file = mock(Filepath.class);
        byte[] data = gif(4, 2, 0, frame(4, 2, 1), frame(4, 2, 2));
        when(folder.joinSegment(anyString())).thenReturn(file);
        when(file.openInputStream()).thenAnswer(i -> new ByteArrayInputStream(data));
        images.get(NAME, () -> folder, pending::add, () -> {});
        images.get(SECOND, () -> folder, pending::add, () -> {});
        assertEquals(1, pending.size());
        pending.remove(0).run(); flushEdt();
        assertNotNull(images.get(NAME, () -> folder, pending::add, () -> {}));
        assertNull(images.get(SECOND, () -> folder, pending::add, () -> {}));
        assertTrue(images.status(SECOND).contains("memory limit"));
        assertTrue(pending.isEmpty());
        images.retain(Collections.emptySet());
        images.get(SECOND, () -> folder, pending::add, () -> {});
        pending.remove(0).run(); flushEdt();
        assertNotNull(images.get(SECOND, () -> folder, pending::add, () -> {}));
    }

    @Test public void importKeepsAnimationAndSeedsCacheWithoutAnotherDecode() throws Exception {
        AtomicLong clock = new AtomicLong();
        OverlayBackgroundImages images = new OverlayBackgroundImages(clock::get, 1024);
        byte[] gif = gif(4, 2, 0, frame(4, 2, 1), frame(4, 2, 2));
        Filepath selected = mock(Filepath.class), folder = mock(Filepath.class), file = mock(Filepath.class);
        when(selected.openInputStream()).thenReturn(new ByteArrayInputStream(gif));
        when(folder.joinSegment(anyString())).thenReturn(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(file.openOutputStream()).thenReturn(output);
        List<Runnable> pending = new ArrayList<>();
        AtomicReference<OverlayBackgroundImages.Result> result = new AtomicReference<>();
        images.importImage(selected, () -> folder, pending::add, r -> {
            assertTrue(SwingUtilities.isEventDispatchThread()); result.set(r);
        });
        pending.remove(0).run(); flushEdt();
        assertNull(result.get().error); assertTrue(result.get().name.endsWith(".gif"));
        assertArrayEquals(gif, output.toByteArray());
        clock.set(100 * MS);
        assertEquals(Color.GREEN.getRGB(), images.get(result.get().name, () -> folder, pending::add, () -> {}).getRGB(0, 0));
        assertTrue(pending.isEmpty()); verify(file, never()).openInputStream();
    }

    @Test public void rejectedExecutorDoesNotLeaveImportOrLoadStuck() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages();
        Filepath file = mock(Filepath.class);
        AtomicReference<OverlayBackgroundImages.Result> result = new AtomicReference<>();
        images.importImage(file, () -> file, r -> { throw new RejectedExecutionException(); }, result::set);
        flushEdt(); assertFalse(images.isImporting()); assertNotNull(result.get().error);
        images.get(NAME, () -> file, r -> { throw new RejectedExecutionException(); }, () -> {});
        flushEdt(); assertTrue(images.status(NAME).contains("unavailable"));
        verifyNoInteractions(file);
    }

    @Test public void profileChangePreventsPendingImportFromWritingOrPopulatingCache() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages();
        Filepath file = mock(Filepath.class), folder = mock(Filepath.class);
        when(file.openInputStream()).thenReturn(new ByteArrayInputStream(gif(2, 2, 0, frame(2, 2, 1))));
        List<Runnable> pending = new ArrayList<>();
        AtomicReference<OverlayBackgroundImages.Result> result = new AtomicReference<>();
        images.importImage(file, () -> folder, pending::add, result::set);
        images.clear(); pending.remove(0).run(); flushEdt();
        assertNotNull(result.get().error); assertNull(result.get().name);
        assertFalse(images.isImporting()); verifyNoInteractions(folder);
    }

    @Test public void gifReferencesRoundTripWithoutAllowingPaths() {
        OverlayConfig config = new OverlayConfig();
        config.setBackgroundImage(NAME, "Animated background.gif");
        config.setBackgroundColour(new Color(0, 0, 0, 75));
        Gson gson = new Gson();
        OverlayConfig restored = OverlayConfigStore.read(gson, "[" + gson.toJson(config) + "]").get(0);
        assertEquals(NAME, restored.getBackgroundImage());
        assertEquals(75, restored.getBackgroundColour().getAlpha());
        assertFalse(OverlayBackgroundImages.validName("../" + NAME));
        assertFalse(OverlayBackgroundImages.validName(NAME + ".png"));
    }

    @Test public void removingAnImageDuringDecodeCannotRepopulateTheCache() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages();
        Filepath folder = mock(Filepath.class), file = mock(Filepath.class);
        byte[] data = gif(4, 2, 0, frame(4, 2, 1), frame(4, 2, 2));
        when(folder.joinSegment(NAME)).thenReturn(file);
        when(file.openInputStream()).thenAnswer(i -> {
            images.retain(Collections.emptySet());
            return new ByteArrayInputStream(data);
        });
        List<Runnable> pending = new ArrayList<>(); List<String> changed = new ArrayList<>();
        images.get(NAME, () -> folder, pending::add, () -> changed.add("refresh"));
        pending.remove(0).run(); flushEdt();
        assertNull(images.status(NAME)); assertTrue(changed.isEmpty());
        assertNull(images.get(NAME, () -> folder, pending::add, () -> {}));
        assertEquals(1, pending.size());
    }

    @Test public void interlacedFramesDecodeAndDamagedPixelDataIsRejected() throws Exception {
        Frame striped = frame(16, 16, 1);
        striped.interlaced = true;
        for (int x = 0; x < 16; x++) striped.image.getRaster().setSample(x, 9, 0, 2);
        byte[] bytes = gif(16, 16, 0, striped);
        OverlayBackgroundImage image = OverlayBackgroundImage.read(bytes);
        assertEquals(Color.GREEN.getRGB(), image.frame(0).getRGB(5, 9));
        assertEquals(Color.RED.getRGB(), image.frame(0).getRGB(5, 10));
        try {
            OverlayBackgroundImage.read(java.util.Arrays.copyOf(bytes, bytes.length - 12));
            fail("Truncated pixel data accepted");
        } catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

    private static Frame frame(int width, int height, int color) {
        byte[] red = {0, (byte) 255, 0, 0, (byte) 255};
        byte[] green = {0, 0, (byte) 255, 0, (byte) 255};
        byte[] blue = {0, 0, 0, (byte) 255, (byte) 255};
        IndexColorModel palette = new IndexColorModel(8, 5, red, green, blue, 0);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, palette);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.getRaster().setSample(x, y, 0, color);
        return new Frame(image);
    }

    private static byte[] gif(int width, int height, int repeats, Frame... frames) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(writer.getDefaultWriteParam());
            IIOMetadataNode root = (IIOMetadataNode) streamMetadata.getAsTree("javax_imageio_gif_stream_1.0");
            IIOMetadataNode screen = node(root, "LogicalScreenDescriptor");
            screen.setAttribute("logicalScreenWidth", "" + width); screen.setAttribute("logicalScreenHeight", "" + height);
            streamMetadata.setFromTree("javax_imageio_gif_stream_1.0", root);
            writer.prepareWriteSequence(streamMetadata);
            for (int i = 0; i < frames.length; i++) {
                Frame frame = frames[i];
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame.image), writer.getDefaultWriteParam());
                IIOMetadataNode image = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                IIOMetadataNode descriptor = node(image, "ImageDescriptor");
                descriptor.setAttribute("interlaceFlag", frame.interlaced ? "TRUE" : "FALSE");
                descriptor.setAttribute("imageLeftPosition", "" + frame.x); descriptor.setAttribute("imageTopPosition", "" + frame.y);
                IIOMetadataNode control = node(image, "GraphicControlExtension");
                control.setAttribute("disposalMethod", frame.disposal); control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", frame.transparent ? "TRUE" : "FALSE");
                control.setAttribute("transparentColorIndex", "0"); control.setAttribute("delayTime", "" + frame.delay);
                if (i == 0 && repeats >= 0) {
                    IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions");
                    IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
                    extension.setAttribute("applicationID", "NETSCAPE"); extension.setAttribute("authenticationCode", "2.0");
                    extension.setUserObject(new byte[]{1, (byte) repeats, (byte) (repeats >> 8)});
                    extensions.appendChild(extension); image.appendChild(extensions);
                }
                metadata.setFromTree("javax_imageio_gif_image_1.0", image);
                writer.writeToSequence(new IIOImage(frame.image, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        return output.toByteArray();
    }

    private static IIOMetadataNode node(IIOMetadataNode root, String name) {
        return (IIOMetadataNode) root.getElementsByTagName(name).item(0);
    }

    private static final class Frame {
        final BufferedImage image;
        int x, y, delay = 10;
        boolean transparent, interlaced;
        String disposal = "doNotDispose";
        Frame(BufferedImage image) { this.image = image; }
        Frame at(int x, int y) { this.x = x; this.y = y; return this; }
        Frame delay(int delay) { this.delay = delay; return this; }
        Frame dispose(String disposal) { this.disposal = disposal; return this; }
        Frame transparent() { transparent = true; return this; }
    }
}
