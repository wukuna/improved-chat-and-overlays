package com.improvedchat.overlay;

import com.google.gson.Gson;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.client.util.Filepath;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayBackgroundImagesTest {
    private static final String NAME = "12345678-1234-1234-1234-123456789abc.png";

    @Test public void pngAndJpegDecodeWithinTheRenderBudget() throws Exception {
        BufferedImage png = OverlayBackgroundImages.read(new ByteArrayInputStream(encoded(2000, 1000, "png")));
        assertTrue(png.getWidth() <= 1024);
        assertTrue((long) png.getWidth() * png.getHeight() <= 512 * 512);
        assertEquals(2, png.getWidth() / (double) png.getHeight(), .02);
        BufferedImage jpeg = OverlayBackgroundImages.read(new ByteArrayInputStream(encoded(20, 10, "jpeg")));
        assertEquals(20, jpeg.getWidth()); assertEquals(10, jpeg.getHeight());
        assertTrue(new Color(jpeg.getRGB(5, 5)).getRed() > 240);
    }

    @Test public void invalidAndOversizedImagesAreRejected() throws Exception {
        for (byte[] data : new byte[][]{new byte[]{1, 2, 3}, new byte[10 * 1024 * 1024 + 1],
                encoded(8193, 1, "png"), encoded(3000, 3000, "png"), encoded(5, 5, "gif")}) {
            try {
                OverlayBackgroundImages.read(new ByteArrayInputStream(data));
                fail("Invalid image accepted");
            } catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }

    @Test public void imageUsesOpacityRoundedClipAndCenteredCoverWithoutChangingGraphicsState() {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D paint = image.createGraphics();
        paint.setColor(Color.BLUE); paint.fillRect(0, 0, 40, 20);
        paint.setColor(Color.RED); paint.fillRect(10, 0, 20, 20); paint.dispose();
        BufferedImage result = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        Shape clip = new RoundRectangle2D.Double(0, 0, 20, 20, 12, 12);
        graphics.setClip(clip); graphics.setColor(Color.GREEN);
        graphics.setComposite(AlphaComposite.SrcOver.derive(.5f));
        OverlayBackgroundImages.draw(graphics, image, new Color(0, 255, 0, 128), 20, 20);
        Color center = new Color(result.getRGB(10, 10), true);
        assertEquals(64, center.getAlpha());
        assertEquals(255, center.getRed()); assertEquals(0, center.getBlue());
        assertEquals(0, result.getRGB(0, 0));
        assertEquals(Color.GREEN, graphics.getColor());
        assertEquals(.5f, ((AlphaComposite) graphics.getComposite()).getAlpha(), 0);
        assertEquals(clip.getBounds(), graphics.getClip().getBounds());
        graphics.dispose();
    }

    @Test public void cachedLoadsAreDeferredDeduplicatedAndInvalidatedOnProfileChange() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages();
        List<Runnable> pending = new ArrayList<>(); List<String> changed = new ArrayList<>();
        Filepath folder = mock(Filepath.class), file = mock(Filepath.class);
        when(folder.joinSegment(NAME)).thenReturn(file);
        byte[] png = encoded(20, 10, "png");
        when(file.openInputStream()).thenAnswer(i -> new ByteArrayInputStream(png));
        Runnable refresh = () -> changed.add("refresh");
        assertNull(images.get(NAME, () -> folder, pending::add, refresh));
        assertNull(images.get(NAME, () -> folder, pending::add, refresh));
        verifyNoInteractions(file); assertEquals(1, pending.size());
        images.clear(); pending.remove(0).run(); SwingUtilities.invokeAndWait(() -> {});
        assertTrue(changed.isEmpty());
        assertNull(images.get(NAME, () -> folder, pending::add, refresh));
        pending.remove(0).run(); SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1, changed.size());
        assertNotNull(images.get(NAME, () -> folder, pending::add, refresh));
        assertTrue(pending.isEmpty());
        images.retain(Collections.emptySet());
        assertNull(images.get(NAME, () -> folder, pending::add, refresh));
        assertEquals(1, pending.size());
    }

    @Test public void failedCacheReadsDoNotRetryEveryFrameAndUnsafeNamesNeverRead() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages();
        Filepath folder = mock(Filepath.class);
        List<Runnable> pending = new ArrayList<>();
        assertNull(images.get("../outside.png", () -> folder, pending::add, () -> {}));
        assertTrue(pending.isEmpty()); verifyNoInteractions(folder);
        when(folder.joinSegment(NAME)).thenThrow(new IllegalArgumentException());
        images.get(NAME, () -> folder, pending::add, () -> {}); pending.remove(0).run();
        SwingUtilities.invokeAndWait(() -> {});
        assertNotNull(images.status(NAME));
        assertNull(images.get(NAME, () -> folder, pending::add, () -> {}));
        assertTrue(pending.isEmpty());
    }

    @Test public void importRunsOffTheUiPathAndReportsSuccessOrFailureOnEdt() throws Exception {
        OverlayBackgroundImages images = new OverlayBackgroundImages();
        Filepath selected = mock(Filepath.class), folder = mock(Filepath.class), output = mock(Filepath.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        when(selected.openInputStream()).thenAnswer(i -> new ByteArrayInputStream(encoded(20, 10, "png")));
        when(folder.joinSegment(anyString())).thenReturn(output);
        when(output.openOutputStream()).thenReturn(bytes);
        List<Runnable> pending = new ArrayList<>();
        AtomicReference<OverlayBackgroundImages.Result> result = new AtomicReference<>();
        images.importImage(selected, () -> folder, pending::add, r -> {
            assertTrue(SwingUtilities.isEventDispatchThread()); result.set(r);
        });
        assertTrue(images.isImporting()); verifyNoInteractions(selected); assertNull(result.get());
        pending.remove(0).run(); SwingUtilities.invokeAndWait(() -> {});
        assertFalse(images.isImporting()); assertNull(result.get().error);
        assertTrue(OverlayBackgroundImages.validName(result.get().name));
        verify(folder).createDirectories();
        assertNotNull(ImageIO.read(new ByteArrayInputStream(bytes.toByteArray())));
        when(selected.openInputStream()).thenThrow(new IOException("Cannot read image"));
        images.importImage(selected, () -> folder, pending::add, result::set);
        pending.remove(0).run(); SwingUtilities.invokeAndWait(() -> {});
        assertFalse(images.isImporting()); assertNull(result.get().name);
        assertEquals("Cannot read image", result.get().error);
    }

    @Test public void savedImageReferenceRetainsOpacityAndHasNoExternalPath() {
        OverlayConfig original = new OverlayConfig();
        original.setBackgroundColour(new Color(10, 20, 30, 71));
        original.setBackgroundImage(NAME, "My image.png");
        Gson gson = new Gson();
        OverlayConfig restored = OverlayConfigStore.read(gson, "[" + gson.toJson(original) + "]").get(0);
        assertEquals(NAME, restored.getBackgroundImage());
        assertEquals("My image.png", restored.getBackgroundImageLabel());
        assertEquals(71, restored.getBackgroundColour().getAlpha());
        restored.setBackgroundImage("../outside.png", "bad");
        assertEquals("", restored.getBackgroundImage());
        assertEquals(NAME, original.getBackgroundImage());
        original.setBackgroundImage(null, null);
        assertEquals(71, original.getBackgroundColour().getAlpha());
    }

    private static byte[] encoded(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED); g.fillRect(0, 0, width, height); g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return bytes.toByteArray();
    }
}
