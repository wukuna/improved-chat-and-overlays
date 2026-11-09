package com.improvedchat;

import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.overlay.OverlayFilterMode;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImprovedChatPanelTest {
    @org.junit.BeforeClass public static void useClientTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { javax.swing.UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF()); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private static final class FakePlugin extends ImprovedChatPlugin {
        final List<OverlayConfig> configs = new ArrayList<>();
        boolean globalOverride;
        int saves;
        String recoveryMessage;
        ImprovedChatConfig dockConfig;
        @Override public ImprovedChatConfig getInputDockConfig() { return dockConfig; }
        FakePlugin() { configs.add(OverlayConfig.preset("Game Alerts", "game")); }
        @Override public List<OverlayConfig> getOverlayConfigs() { return configs; }
        @Override public String getConfigRecoveryMessage() { return recoveryMessage; }
        @Override public void onOverlayConfigChanged() { saves++; }
        @Override public int getOverlayWidth(OverlayConfig config) { return config.getWidgetWidth(); }
        @Override public net.runelite.client.config.Notification customizeNotification(net.runelite.client.config.Notification original) {
            return com.improvedchat.overlay.OverlayNotifications.normalize(original.withOverride(true).withInitialized(true).withVolume(50));
        }
        @Override public boolean isGlobalFilterOverride() { return globalOverride; }
        @Override public boolean isGlobalFilterAvailable() { return false; }
        @Override public String movementHint() { return "Hold Alt and drag the overlay to move it."; }
    }

    private static final class TestWindow extends OverlayEditorWindow {
        javax.swing.JComponent contents;
        Runnable onClose;
        String title;
        boolean opened;
        @Override void open(Component owner, javax.swing.JComponent contents, String title, Runnable onClose) {
            this.contents=contents;this.title=title;this.onClose=onClose;opened=true;
        }
        @Override void title(String title) { this.title=title; }
        @Override void close() { opened=false; }
    }
    private static final class TestPanel extends ImprovedChatPanel {
        final TestWindow window;
        TestPanel(FakePlugin plugin) { this(plugin,new TestWindow()); }
        TestPanel(FakePlugin plugin,TestWindow window) { super(plugin,window);this.window=window; }
    }
    private static ImprovedChatPanel editingPanel(FakePlugin plugin) {
        TestPanel panel=new TestPanel(plugin);
        editButtons(panel).get(0).doClick();
        return panel;
    }
    private static Container editor(Container parent) {
        if(parent instanceof TestPanel && ((TestPanel)parent).window.opened)
            return ((TestPanel)parent).window.contents;
        return parent;
    }
    @Test public void sidebarStartsSimpleAndWindowClosesWithRemovedOverlayOrPlugin() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            FakePlugin plugin=new FakePlugin();TestPanel panel=new TestPanel(plugin);
            panel.setSize(225,650);layout(panel);
            assertFalse(panel.window.opened);
            JTabbedPane navigation=findTabs(panel);
            assertEquals("Overlays",navigation.getTitleAt(0));
            assertEquals("Create",navigation.getTitleAt(1));
            assertEquals("Tools",navigation.getTitleAt(2));
            assertEquals(1,navigation.getTabRunCount());
            assertNull(namedField(panel,"Name"));
            plugin.recoveryMessage="Saved overlays could not be read. The backup was kept.";panel.rebuild();
            assertEquals(plugin.recoveryMessage,button(panel,"Settings notice").getToolTipText());
            assertNull(findTextArea(panel));
            editButtons(panel).get(0).doClick();
            assertTrue(panel.window.opened);
            assertNull(panel.window.contents.getParent());
            namedField(panel,"Name").setText("My game alerts");
            assertEquals("Edit overlay — My game alerts",panel.window.title);
            panel.window.onClose.run();assertFalse(panel.window.opened);
            editButtons(panel).get(0).doClick();assertTrue(panel.window.opened);
            plugin.configs.clear();panel.rebuild();assertFalse(panel.window.opened);
            plugin.configs.add(OverlayConfig.preset("New", "game"));panel.rebuild();
            editButtons(panel).get(0).doClick();panel.close();assertFalse(panel.window.opened);
            panel.rebuild();editButtons(panel).get(0).doClick();assertFalse(panel.window.opened);
        });
    }

    private static javax.swing.JTextArea findTextArea(Container parent) {
        for(Component child:parent.getComponents()) {
            if(child instanceof javax.swing.JTextArea && child.isVisible())return (javax.swing.JTextArea)child;
            if(child instanceof Container && child.isVisible()) {javax.swing.JTextArea found=findTextArea((Container)child);if(found!=null)return found;}
        }return null;
    }

    @Test public void editorPreservesScrollAndDisablesOverriddenFilters() throws Exception {
        FakePlugin plugin = new FakePlugin();
        ImprovedChatPanel[] holder = new ImprovedChatPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = editingPanel(plugin);
            holder[0].setSize(280, 650);
            editButtons(holder[0]).get(0).doClick();
            tabs(holder[0]).setSelectedIndex(1);
            button(holder[0], "FRAME").doClick();
            layout(holder[0]);
        });
        SwingUtilities.invokeAndWait(() -> {
            layout(holder[0]);
            JScrollPane scroll = findScroll(holder[0]);
            scroll.getVerticalScrollBar().setValue(50);
            assertEquals(50, scroll.getVerticalScrollBar().getValue());
            button(holder[0], "Background").doClick();
            layout(holder[0]);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(50, findScroll(holder[0]).getVerticalScrollBar().getValue());
            tabs(holder[0]).setSelectedIndex(0);
            plugin.globalOverride = true;
            plugin.configs.get(0).setFilterMode(OverlayFilterMode.SHOW_ONLY_MATCHES);
            holder[0].rebuild();
            layout(holder[0]);
            assertFalse(button(holder[0], "Use RuneLite Chat Filter").isEnabled());
            assertFalse(button(holder[0], "TEST CUSTOM RULES").isEnabled());
        });
    }

    @Test public void compactEditorSavesChannelAndPaletteChoicesIndependently() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePlugin plugin = new FakePlugin();
            OverlayConfig first = plugin.configs.get(0);
            OverlayConfig second = OverlayConfig.preset("Clan", "clan");
            plugin.configs.add(second);
            ImprovedChatPanel panel = editingPanel(plugin);
            panel.setSize(225, 900); layout(panel);
            JTabbedPane pages = tabs(panel);
            assertEquals(1, pages.getTabRunCount());
            assertTrue(pages.getBoundsAt(2).getMaxX() <= pages.getWidth());
            assertTrue(button(panel, "Show channel names").isSelected());
            button(panel, "Show channel names").doClick();
            assertFalse(first.isShowChannelNames(true));
            assertTrue(second.isShowChannelNames(true));
            tabs(panel).setSelectedIndex(1);
            combo(panel, "Text Font").setSelectedItem("Arial");
            combo(panel, "Text Size").setSelectedItem(com.improvedchat.model.FontSize.SMALL);
            combo(panel, "Timestamp style").setSelectedItem(com.improvedchat.model.TimestampStyle.TIME_AGO);
            assertEquals(com.improvedchat.model.TimestampStyle.TIME_AGO, first.getTimestampStyle());
            assertEquals(com.improvedchat.model.TimestampStyle.CLOCK_TIME, second.getTimestampStyle());
            tabs(panel).setSelectedIndex(2);
            combo(panel, "Color palette").setSelectedItem(ChatColorSource.OPAQUE);
            combo(panel, "Text color source").setSelectedItem("Custom color");
            assertNull(combo(panel, "Color palette"));
            combo(panel, "Text color source").setSelectedItem("Message colors");
            assertTrue(combo(panel, "Color palette").isEnabled());
            assertEquals(ChatColorSource.OPAQUE, combo(panel, "Color palette").getSelectedItem());
            combo(panel, "Text color source").setSelectedItem("Channel palette");
            assertFalse(combo(panel, "Color palette").isEnabled());
            combo(panel, "Text color source").setSelectedItem("Message colors");
            tabs(panel).setSelectedIndex(1);
            editButtons(panel).get(1).doClick();
            assertTrue(button(panel, "Show channel names").isSelected());
            editButtons(panel).get(0).doClick();
            assertEquals(1, tabs(panel).getSelectedIndex());
            assertFalse(button(panel, "Show channel names").isSelected());
            assertEquals("Arial", combo(panel, "Text Font").getSelectedItem());
            assertEquals(com.improvedchat.model.FontSize.SMALL, combo(panel, "Text Size").getSelectedItem());
            assertEquals(com.improvedchat.model.TimestampStyle.TIME_AGO, combo(panel, "Timestamp style").getSelectedItem());
            assertEquals(ChatColorSource.AUTOMATIC, second.getTextSettings().chatColorSource());
        });
    }

    @Test public void colorsStayTogetherAndTargetsSaveIndependently() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePlugin plugin = new FakePlugin();
            plugin.dockConfig = new ImprovedChatConfig() {}; OverlayConfig cfg = plugin.configs.get(0);
            cfg.getTextSettings().setRainbowEnabled(true); cfg.getTextSettings().setRainbowRules("rare drop");
            ImprovedChatPanel panel = editingPanel(plugin); panel.setSize(225, 900); tabs(panel).setSelectedIndex(2);
            assertEquals("Colors", tabs(panel).getTitleAt(2));
            combo(panel, "Text mode").setSelectedItem(com.improvedchat.model.AnimatedTextStyle.DUO_CYCLE);
            assertEquals(com.improvedchat.model.AnimatedTextStyle.DUO_CYCLE, cfg.getTextColors().getMode());
            assertEquals(com.improvedchat.model.AnimatedTextStyle.NORMAL, cfg.getTimestampColors().getMode());
            button(panel, "Border").doClick();
            combo(panel, "Border mode").setSelectedItem(com.improvedchat.model.AnimatedTextStyle.DUO_WAVE);
            combo(panel, "Timestamp mode").setSelectedItem(com.improvedchat.model.AnimatedTextStyle.RGB_CYCLE);
            button(panel, "Pulse timestamp").doClick();
            assertTrue(cfg.getTimestampColors().isPulse());
            assertFalse(cfg.getTextColors().isPulse()); assertFalse(cfg.getBorderColors().isPulse());
            assertEquals(5, combo(panel, "Border mode").getItemCount());
            assertEquals(5, combo(panel, "Text mode").getItemCount());
            assertEquals(5, combo(panel, "Timestamp mode").getItemCount());
            assertEquals("rare drop", cfg.getTextSettings().overlayRainbowRules());
            assertTrue(SwingUtilities.isDescendingFrom(combo(panel, "Border mode"), tabs(panel).getComponentAt(2)));
            assertTrue(SwingUtilities.isDescendingFrom(combo(panel, "Timestamp mode"), tabs(panel).getComponentAt(2)));
            assertTrue(SwingUtilities.isDescendingFrom(button(panel, "Specific Message Highlighting"), tabs(panel).getComponentAt(2)));
            assertNull(findButton(editor(panel), "RGB all messages"));
            assertNull(findButton(editor(panel), "SOLID COLOR HIGHLIGHTS"));
            assertEquals(2, tabs(panel).getSelectedIndex());
            layout(panel);
            try { capture(panel, "overlay-colors"); } catch (Exception e) { throw new AssertionError(e); }

            button(panel, "CHAT INPUT BUTTONS").doClick();
            for (String name : new String[]{"Button background", "Button border", "Selected border", "Text and icons", "Unavailable text/icons"}) {
                Component swatch = namedComponent(editor(panel), name);
                assertNotNull(name, swatch);
                assertTrue(SwingUtilities.isDescendingFrom(swatch, tabs(panel).getComponentAt(2)));
            }
            button(panel, "TEXT COLORS").doClick(); button(panel, "TIMESTAMP COLORS").doClick();
            button(panel, "BORDER").doClick();
            layout(panel);
            try { capture(panel, "chat-input-button-colors"); } catch (Exception e) { throw new AssertionError(e); }

        });
    }

    @Test public void imageControlsShareTheBackgroundSectionAndRemoveKeepsOpacity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePlugin plugin = new FakePlugin(); OverlayConfig cfg = plugin.configs.get(0);
            cfg.setBackgroundColour(new java.awt.Color(10, 20, 30, 128));
            cfg.setBackgroundImage("12345678-1234-1234-1234-123456789abc.png", "My background.png");
            ImprovedChatPanel panel = editingPanel(plugin); panel.setSize(225, 900);
            tabs(panel).setSelectedIndex(2);
            button(panel, "TEXT COLORS").doClick(); button(panel, "TIMESTAMP COLORS").doClick();
            button(panel, "BACKGROUND").doClick();
            assertTrue(SwingUtilities.isDescendingFrom(button(panel, "Replace image"), tabs(panel).getComponentAt(2)));
            assertTrue(button(panel, "Remove image").isEnabled());
            layout(panel);
            try { capture(panel, "overlay-background"); } catch (Exception e) { throw new AssertionError(e); }
            button(panel, "Remove image").doClick(); panel.rebuild();
            assertEquals("", cfg.getBackgroundImage());
            assertEquals(128, cfg.getBackgroundColour().getAlpha());
            assertTrue(button(panel, "Choose image").isEnabled());
            assertNull(findButton(editor(panel), "Remove image"));
        });
    }

    private static javax.swing.JComboBox<?> combo(Container parent, String name) {
        parent = editor(parent);
        for (Component child : parent.getComponents()) {
            if (child instanceof javax.swing.JComboBox && name.equals(child.getName())) return (javax.swing.JComboBox<?>) child;
            if (child instanceof Container) {
                javax.swing.JComboBox<?> found = combo((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void renderPanelPreviews() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                FakePlugin plugin = new FakePlugin();
                ImprovedChatPanel panel = editingPanel(plugin);
                panel.setSize(280, 650);
                layout(panel);
                capture(panel, "overlay-list");
                editButtons(panel).get(0).doClick();
                layout(panel);
                capture(panel, "overlay-editor");
                plugin.configs.get(0).setFilterMode(OverlayFilterMode.SHOW_ONLY_MATCHES);
                plugin.configs.get(0).setFilteredRegex("[invalid");
                panel.rebuild();
                button(panel, "MESSAGE FILTERING").doClick();
                layout(panel);
                Component filtering = button(panel, "MESSAGE FILTERING");
                javax.swing.JComponent section = (javax.swing.JComponent) filtering.getParent();
                section.scrollRectToVisible(new java.awt.Rectangle(0, 0, section.getWidth(), 700));
                capture(panel, "overlay-filtering");
                panel.setSize(225, 900);
                panel.rebuild();
                layout(panel);
                capture(panel, "overlay-narrow");
            } catch (Exception ex) { throw new AssertionError(ex); }
        });
    }

    @Test public void notificationsEditorKeepsEachOverlayIndependent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                FakePlugin plugin=new FakePlugin(); OverlayConfig second=new OverlayConfig(); plugin.configs.add(second);
                ImprovedChatPanel panel=editingPanel(plugin); panel.setSize(280,900);
                editButtons(panel).get(0).doClick(); tabs(panel).setSelectedIndex(3);
                button(panel,"Enable Notifications").doClick();
                assertTrue(plugin.configs.get(0).getNotification().isEnabled()); assertFalse(second.getNotification().isEnabled());
                button(panel,"Customize Notification").doClick();
                assertTrue(plugin.configs.get(0).getNotification().isOverride());
                layout(panel);
                javax.swing.JComponent section=(javax.swing.JComponent)tabs(panel).getSelectedComponent();
                section.scrollRectToVisible(new java.awt.Rectangle(0,0,section.getWidth(),650));
                capture(panel,"overlay-notifications");
                button(panel,"Enable Notifications").doClick();
                assertFalse(plugin.configs.get(0).getNotification().isEnabled());
            } catch (Exception ex) { throw new AssertionError(ex); }
        });
    }

    @Test public void tabsRetainSelectionDuringConditionalRebuildAndAutoSaveText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePlugin plugin = new FakePlugin();
            ImprovedChatPanel panel = editingPanel(plugin);
            panel.setSize(225, 900); layout(panel);
            assertEquals(4, tabs(panel).getTabCount());
            assertEquals("Messages", tabs(panel).getTitleAt(0));
            assertEquals("Style", tabs(panel).getTitleAt(1));
            assertEquals("Colors", tabs(panel).getTitleAt(2));
            assertEquals("Alerts", tabs(panel).getTitleAt(3));
            javax.swing.JTextField name = namedField(panel, "Name");
            name.setText("Renamed without pressing Enter");
            assertEquals("Renamed without pressing Enter", plugin.configs.get(0).getName());
            assertTrue(plugin.saves > 0);
            tabs(panel).setSelectedIndex(2);
            button(panel, "Background").doClick();
            assertEquals(2, tabs(panel).getSelectedIndex());
            assertTrue(plugin.configs.get(0).isBackgroundEnabled());
            assertFalse(button(panel, "Use RuneLite Chat Filter").isShowing());
            panel.rebuild();
            assertEquals(2, tabs(panel).getSelectedIndex());
        });
    }

    @Test public void expandingMessagesAdjustsActivePageHeight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePlugin plugin = new FakePlugin();
            ImprovedChatPanel panel = editingPanel(plugin);
            panel.setSize(225, 650); layout(panel);
            tabs(panel).setSelectedIndex(2);
            int before = tabs(panel).getPreferredSize().height;
            button(panel, "Specific Message Highlighting").doClick();
            layout(panel);
            assertTrue(tabs(panel).getPreferredSize().height > before);
            assertEquals(2, tabs(panel).getSelectedIndex());
        });
    }

    @Test public void threeOverlaysStayListedAndEachEditorKeepsItsOwnTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                FakePlugin plugin = new FakePlugin();
                plugin.configs.add(OverlayConfig.preset("Private Messages", "private"));
                plugin.configs.add(OverlayConfig.preset("Clan Highlights", "clan"));
                ImprovedChatPanel panel = editingPanel(plugin);
                panel.setSize(280, 900); layout(panel);
                assertEquals(3, editButtons(panel).size());
                tabs(panel).setSelectedIndex(1);
                editButtons(panel).get(1).doClick();
                assertEquals(0, tabs(panel).getSelectedIndex());
                namedField(panel,"Name").setText("Private Only");
                assertEquals("Game Alerts", plugin.configs.get(0).getName());
                assertEquals("Private Only", plugin.configs.get(1).getName());
                tabs(panel).setSelectedIndex(2);
                button(panel,"Enable Notifications").doClick();
                assertTrue(plugin.configs.get(1).getNotification().isEnabled());
                assertFalse(plugin.configs.get(0).getNotification().isEnabled());
                editButtons(panel).get(0).doClick();
                assertEquals(1, tabs(panel).getSelectedIndex());
                layout(panel); capture(panel,"three-overlays-appearance");
                panel.setSize(225,900); panel.rebuild(); layout(panel); capture(panel,"three-overlays-narrow");
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test public void betaReplacementEditorSavesMultipleRulesWithoutLosingAnotherRule() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePlugin plugin = new FakePlugin();
            ImprovedChatPanel panel = editingPanel(plugin); panel.setSize(225,900);
            button(panel,"MESSAGE REPLACER (BETA)").doClick();
            button(panel,"Enable message replacer (Beta)").doClick();
            button(panel,"ADD REPLACEMENT").doClick();
            namedField(panel,"Original message").setText("Your thrall returns to the grave.");
            namedField(panel,"Show instead").setText("You need to summon a thrall!");
            javax.swing.JTextField staleField = namedField(panel,"Original message");
            button(panel,"ADD REPLACEMENT").doClick();
            List<javax.swing.JTextField> originals = namedFields(panel,"Original message");
            List<javax.swing.JTextField> replacements = namedFields(panel,"Show instead");
            assertEquals(2, originals.size());
            originals.get(1).setText("Your boost expired."); replacements.get(1).setText("Drink another potion!");
            button(panel,"REMOVE REPLACEMENT").doClick();

            staleField.setText("detached editor callback");
            OverlayConfig config = plugin.configs.get(0);
            assertEquals(1,config.getTextReplacements().size());
            assertEquals("Your boost expired.",config.getTextReplacements().get(0).getOriginal());
            assertEquals("Drink another potion!",com.improvedchat.overlay.OverlayTextReplacements.displayText(config,"Your boost expired."));
            namedField(panel,"Test message text").setText("Your boost expired.");
            button(panel,"TEST REPLACEMENTS").doClick();
            layout(panel);
            assertTrue(plugin.saves > 0);
            try { capture(panel,"message-replacer-beta"); } catch(Exception e) {throw new AssertionError(e);}
        });
    }

    private static List<javax.swing.JTextField> namedFields(Container parent, String name) {
        parent = editor(parent);
        List<javax.swing.JTextField> result = new ArrayList<>();
        for(Component child:parent.getComponents()) {
            if(child instanceof javax.swing.JTextField && name.equals(child.getName())) result.add((javax.swing.JTextField)child);
            if(child instanceof Container) result.addAll(namedFields((Container)child,name));
        }
        return result;
    }

    private static javax.swing.JTextField namedField(Container parent, String name) {
        parent = editor(parent);
        for (Component child : parent.getComponents()) {
            if (child instanceof javax.swing.JTextField && name.equals(child.getName())) return (javax.swing.JTextField)child;
            if (child instanceof Container) {
                javax.swing.JTextField found = namedField((Container)child,name);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static List<AbstractButton> editButtons(Container parent) {
        List<AbstractButton> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton && "overlay-selector".equals(child.getName())) result.add((AbstractButton)child);
            if (child instanceof Container) result.addAll(editButtons((Container)child));
        }
        return result;
    }

    private static void capture(Component panel, String name) throws Exception {
        if(panel instanceof TestPanel && !name.contains("list") && !name.contains("narrow")) panel=editor((Container)panel);
        File directory = new File("build/reports/ui");
        directory.mkdirs();
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File(directory, name + ".png"));
    }

    private static void layout(Container container) {
        if (container instanceof TestPanel && ((TestPanel)container).window.opened) {
            Container editor = editor(container);editor.setSize(540,650);layout(editor);
        }
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static JTabbedPane tabs(Container parent) {
        parent = editor(parent);
        for (Component child : parent.getComponents()) {
            if (child instanceof JTabbedPane) return (JTabbedPane) child;
            if (child instanceof Container) {
                JTabbedPane found = findTabs((Container) child);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing overlay tabs");
    }
    private static JTabbedPane findTabs(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTabbedPane) return (JTabbedPane) child;
            if (child instanceof Container) {
                JTabbedPane found = findTabs((Container)child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JScrollPane findScroll(Container parent) {
        parent = editor(parent);
        for (Component child : parent.getComponents()) {
            if (child instanceof JScrollPane) return (JScrollPane) child;
        }
        throw new AssertionError("Missing page scroll pane");
    }

    private static Component namedComponent(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container) {
                Component found = namedComponent((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static AbstractButton button(Container parent, String text) {
        parent = editor(parent);
        AbstractButton result = findButton(parent, text);
        if (result == null) throw new AssertionError("Missing button " + text);
        return result;
    }

    private static AbstractButton findButton(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton && ((AbstractButton) child).getText().contains(text)) return (AbstractButton) child;
            if (child instanceof Container) {
                AbstractButton found = findButton((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
