package com.improvedchat;

import com.improvedchat.model.FontSize;
import com.improvedchat.model.AttentionSpeed;
import com.improvedchat.model.AttentionStopMode;
import com.improvedchat.model.AttentionTrigger;
import com.improvedchat.model.BorderThickness;
import com.improvedchat.model.AnimatedTextStyle;
import com.improvedchat.overlay.OverlayColorSettings;
import com.improvedchat.model.MessageCategory;
import com.improvedchat.model.PlacementMode;
import com.improvedchat.model.TextAlignment;
import com.improvedchat.model.TimestampStyle;
import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.overlay.OverlayFilterMode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import com.improvedchat.overlay.OverlayMessageFilter;
import com.improvedchat.model.OverlayMessage;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.ui.PluginPanel;

public class ImprovedChatPanel extends PluginPanel {
    private static final Color BG = new Color(31, 34, 39);
    private static final Color CARD = new Color(41, 45, 52);
    private static final Color CARD_ALT = new Color(49, 54, 62);
    private static final Color CONTROL = new Color(27, 30, 35);
    private static final Color BORDER = new Color(68, 74, 84);
    private static final Color TEXT = new Color(232, 235, 240);
    private static final Color MUTED = new Color(151, 159, 171);
    private static final Color ACCENT = new Color(61, 181, 218);
    private static final Color DANGER = new Color(225, 86, 86);
    private static final String[] FONT_FAMILIES = {
            "RuneScape", "Arial", "Verdana", "Tahoma", "Segoe UI", "Georgia", "Courier New"
    };
    private static final PlacementMode[] PLACEMENT_MODES = {
            PlacementMode.FREE, PlacementMode.BELOW_PLAYER, PlacementMode.ABOVE_PLAYER
    };

    private final ImprovedChatPlugin plugin;
    private final Map<String, Boolean> openSections = new HashMap<>();
    private final Map<String, JButton> overlaySelectors = new HashMap<>();
    private final Map<String, Integer> selectedTabs = new HashMap<>();
    private String editingOverlayId;
    private String renderedOverlayId;
    private JScrollPane pageScroll;
    private JScrollPane editorScroll;
    private final JPanel editorPage = new JPanel(new BorderLayout());
    private final OverlayEditorWindow editorWindow;
    private int sidebarTab;
    private volatile boolean closed;
    private int rebuildVersion;

    public ImprovedChatPanel(ImprovedChatPlugin plugin) {
        this(plugin, new OverlayEditorWindow());
    }

    ImprovedChatPanel(ImprovedChatPlugin plugin, OverlayEditorWindow editorWindow) {
        super(false);
        this.plugin = plugin;
        this.editorWindow = editorWindow;
        setLayout(new BorderLayout());
        setBackground(BG);
        rebuild();
    }

    public final void rebuild() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::rebuild);
            return;
        }
        if (closed) return;
        int listPosition = pageScroll == null ? 0 : pageScroll.getVerticalScrollBar().getValue();
        boolean sameEditor = java.util.Objects.equals(renderedOverlayId, editingOverlayId);
        int editorPosition = sameEditor && editorScroll != null ? editorScroll.getVerticalScrollBar().getValue() : 0;
        int version = ++rebuildVersion;
        overlaySelectors.clear();
        removeAll();
        add(buildHeader(), BorderLayout.NORTH);
        JTabbedPane navigation = new JTabbedPane();
        navigation.setName("sidebar-tabs");
        navigation.addTab("Overlays", buildOverlaysPage());
        navigation.addTab("Create", buildCreatePage());
        navigation.addTab("Tools", buildToolsPage());
        navigation.setSelectedIndex(sidebarTab);
        navigation.addChangeListener(e -> sidebarTab = navigation.getSelectedIndex());
        add(navigation, BorderLayout.CENTER);
        OverlayConfig selected = findOverlay(editingOverlayId);
        if (selected == null) {
            closeEditor();
        } else {
            editorPage.removeAll();
            editorScroll = scroll(buildOverlayEditor(selected));
            editorPage.add(editorScroll, BorderLayout.CENTER);
            editorWindow.title("Edit overlay — " + selected.getName());
            editorPage.revalidate();
            editorPage.repaint();
        }
        renderedOverlayId = editingOverlayId;
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> {
            if (closed || version != rebuildVersion) return;
            if (pageScroll != null) pageScroll.getVerticalScrollBar().setValue(listPosition);
            if (editorScroll != null) editorScroll.getVerticalScrollBar().setValue(editorPosition);
        });
    }

    private void openEditor(String id) {
        if (closed || findOverlay(id) == null) return;
        editingOverlayId = id;
        rebuild();
        editorWindow.open(this, editorPage, "Edit overlay — " + findOverlay(id).getName(), () -> {
            closeEditor();
            rebuild();
        });
    }

    private void closeEditor() {
        editorWindow.close();
        editingOverlayId = null;
        renderedOverlayId = null;
        editorScroll = null;
        editorPage.removeAll();
    }

    public void close() {
        closed = true;
        if (SwingUtilities.isEventDispatchThread()) closeEditor();
        else SwingUtilities.invokeLater(this::closeEditor);
    }

    private Component buildCreatePage() {
        WidthTrackingPanel body = verticalPage();
        String[][] presets = {{"Game Alerts", "game"}, {"Private Messages", "private"},
                {"Clan Chat", "clan"}, {"All Messages", "all"}, {"Custom Overlay", "custom"}};
        for (String[] preset : presets) {
            JButton create = button(preset[0]);
            create.setAlignmentX(Component.LEFT_ALIGNMENT);
            create.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            create.setToolTipText("Create and edit a new " + preset[0] + " overlay.");
            create.addActionListener(e -> createOverlay(preset[0], preset[1]));
            body.add(create);
            body.add(Box.createVerticalStrut(6));
        }
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private Component buildToolsPage() {
        WidthTrackingPanel body = verticalPage();
        addInputDockControls(body);
        JButton settings = button("Chatbox settings…");
        settings.setToolTipText("Native chatbox, border, cleanup and dialogue settings.");
        settings.addActionListener(e -> plugin.openSettings());
        body.add(settings);
        body.add(Box.createVerticalStrut(8));
        JButton controls = button("Overlay controls…");
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        Component help = help("Click an overlay name to open its editor. Changes save automatically. "
                + "Hold Alt and drag an overlay to move it; right-click it for more actions.");
        help.setVisible(false);
        controls.addActionListener(e -> { help.setVisible(!help.isVisible()); body.revalidate(); });
        body.add(controls);
        body.add(help);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private void addInputDockControls(JPanel body) {
        ImprovedChatConfig config = plugin.getInputDockConfig();
        if (config == null) return;
        body.add(pageTitle("CHAT INPUT DOCK"));
        body.add(checkRow("Show input dock", config.inputDockEnabled(), value -> plugin.setInputDockSetting("inputDockEnabled", value)));
        if (config.inputDockEnabled()) {
            body.add(checkRow("Public", config.inputDockPublic(), value -> plugin.setInputDockSetting("inputDockPublic", value)));
            body.add(checkRow("Clan", config.inputDockClan(), value -> plugin.setInputDockSetting("inputDockClan", value)));
            body.add(checkRow("Channel", config.inputDockChannel(), value -> plugin.setInputDockSetting("inputDockChannel", value)));
            body.add(checkRow("GIM (when available)", config.inputDockGim(), value -> plugin.setInputDockSetting("inputDockGim", value)));
            body.add(spinnerRow("Button size (%)", Math.max(60, Math.min(200, config.inputDockScale())), 60, 200, 5,
                    value -> plugin.setInputDockSetting("inputDockScale", value)));
            body.add(comboRow("Placement", com.improvedchat.input.InputDockPlacement.values(), config.inputDockPlacement(),
                    value -> plugin.setInputDockSetting("inputDockPlacement", value)));
            if (config.inputDockPlacement() != com.improvedchat.input.InputDockPlacement.FLOATING) {
                java.util.List<DockTarget> targets = new java.util.ArrayList<>();
                DockTarget selected = new DockTarget("", "Choose overlay…");
                targets.add(selected);
                for (OverlayConfig overlay : plugin.getOverlayConfigs()) {
                    DockTarget target = new DockTarget(overlay.getId(), overlay.getName());
                    targets.add(target);
                    if (overlay.getId().equals(config.inputDockTarget())) selected = target;
                }
                body.add(comboRow("Attach to", targets.toArray(new DockTarget[0]), selected,
                        value -> plugin.setInputDockSetting("inputDockTarget", value.id)));
            }
            body.add(help("Switches typing destination only. Channel means Friends Chat. Unavailable chats are dimmed. "
                    + "Hold Alt and drag a floating dock's edge to resize. An attached dock follows its overlay's visibility and frame."));
        }
        body.add(Box.createVerticalStrut(12));
    }

    private static final class DockTarget {
        final String id;
        final String name;
        DockTarget(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    private Component buildHeader() {
        JPanel root = panel(BG);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(9, 7, 7, 7));

        JPanel heading = panel(BG);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setLayout(new BorderLayout(4, 0));
        heading.add(label("Chat & Overlays", TEXT, 14, Font.BOLD), BorderLayout.CENTER);
        root.add(heading);
        String warning = plugin.getConfigRecoveryMessage();
        if (warning != null) {
            JButton notice = button("Settings notice…");
            notice.setAlignmentX(Component.LEFT_ALIGNMENT);
            notice.setForeground(new Color(245, 190, 90));
            notice.setToolTipText(warning);
            notice.addActionListener(e -> {
                JTextArea text = new JTextArea(warning, 0, 40);
                text.setLineWrap(true);text.setWrapStyleWord(true);text.setEditable(false);text.setOpaque(false);
                JOptionPane.showMessageDialog(this, text, "Settings notice", JOptionPane.WARNING_MESSAGE);
            });
            root.add(Box.createVerticalStrut(5));
            root.add(notice);
        }
        return root;
    }

    private Component buildOverlaysPage() {
        WidthTrackingPanel body = verticalPage();
        JPanel toolbar = panel(BG);
        toolbar.setLayout(new BorderLayout(4, 0));
        toolbar.add(pageTitle("OVERLAYS"), BorderLayout.CENTER);
        JButton add = button("+ New");
        add.setToolTipText("Create an overlay from a preset or start empty.");
        add.addActionListener(e -> showCreateOverlayMenu(add));
        toolbar.add(add, BorderLayout.EAST);
        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        body.add(toolbar);
        body.add(Box.createVerticalStrut(5));

        if (plugin.getOverlayConfigs().isEmpty()) {
            body.add(help("No overlays yet. Create one from a preset, then choose where and when it appears."));
        }
        for (OverlayConfig oc : plugin.getOverlayConfigs()) {
            body.add(overlayCard(oc));
            body.add(Box.createVerticalStrut(6));
        }

        body.add(Box.createVerticalGlue());
        pageScroll = scroll(body);
        return pageScroll;
    }

    private Component overlayCard(OverlayConfig oc) {
        JPanel card = panel(CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(oc.getId().equals(editingOverlayId) ? ACCENT : BORDER),
                BorderFactory.createEmptyBorder(2, 3, 2, 3)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel top = panel(CARD);
        top.setLayout(new BorderLayout(4, 0));
        JButton edit = button("");
        PlainText.set(edit, oc.getName() + "  ›");
        edit.setName("overlay-selector");
        edit.setHorizontalAlignment(JButton.LEFT);
        edit.setToolTipText("Edit " + oc.getName());
        edit.addActionListener(e -> openEditor(oc.getId()));
        overlaySelectors.put(oc.getId(), edit);
        top.add(edit, BorderLayout.CENTER);
        JCheckBox enabled = new JCheckBox("", oc.isShow());
        enabled.setToolTipText("Enable " + oc.getName());
        enabled.getAccessibleContext().setAccessibleName("Enable " + oc.getName());
        enabled.setOpaque(false);
        enabled.addActionListener(e -> { oc.setShow(enabled.isSelected()); saveOverlay(); rebuild(); });
        top.add(enabled, BorderLayout.EAST);
        card.add(top);
        edit.setToolTipText(messageSummary(oc) + " · " + fadeLabel(oc) + ". Changes save automatically.");
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private Component buildOverlayEditor(OverlayConfig oc) {
        WidthTrackingPanel body = verticalPage();

        JPanel messages = panel(CARD);
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        JPanel looks = panel(CARD);
        looks.setLayout(new BoxLayout(looks, BoxLayout.Y_AXIS));
        JPanel colors = panel(CARD);
        colors.setLayout(new BoxLayout(colors, BoxLayout.Y_AXIS));
        JPanel alerts = panel(CARD);
        alerts.setLayout(new BoxLayout(alerts, BoxLayout.Y_AXIS));
        JButton preview = button("Preview");
        preview.setToolTipText("Show sample messages for 10 seconds while logged in.");
        preview.addActionListener(e -> plugin.previewOverlay(oc));
        JPanel editorHeader = panel(BG);
        editorHeader.setLayout(new BorderLayout(4, 0));
        editorHeader.add(pageTitle(oc.getName()), BorderLayout.CENTER);
        editorHeader.add(preview, BorderLayout.EAST);
        editorHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        body.add(editorHeader);

        Component fadeControl = spinnerRow("Fade (sec)", oc.getFadeOutDuration(), 0, 300, 1, v -> { oc.setFadeOutDuration(v); saveOverlay(); });
        enableTree(fadeControl, !oc.isScrollHistory());
        messages.add(accordion(oc, "general", "MESSAGES & VISIBILITY", true,
                textFieldRow("Name", oc.getName(), v -> { oc.setName(v); saveOverlay(); }),
                checkRow("Enabled", oc.isShow(), v -> { oc.setShow(v); saveOverlay(); rebuild(); }),
                overlayHotkeyRow(oc),
                comboRow("Show overlay", new String[] {"With chatbox hidden", "With chatbox open or hidden"},
                        oc.isAlwaysVisible() ? "With chatbox open or hidden" : "With chatbox hidden",
                        v -> { oc.setAlwaysVisible("With chatbox open or hidden".equals(v)); saveOverlay(); }),
                messageTypesRow(oc),
                messageSelectionHelp(oc),
                checkRow("Show channel names", plugin.showChannelNames(oc), v -> { oc.setShowChannelNames(v); saveOverlay(); }),
                spinnerRow(oc.isScrollHistory() ? "Visible lines" : "Max Messages", oc.getMaxMessages(), 1, 20, 1, v -> { oc.setMaxMessages(v); saveOverlay(); }),
                fadeControl
        ));

        JPanel history = panel(CARD);
        history.setLayout(new BoxLayout(history, BoxLayout.Y_AXIS));
        history.add(checkRow("Scroll history", oc.isScrollHistory(), v -> { oc.setScrollHistory(v); saveOverlay(); rebuild(); }));
        if (oc.isScrollHistory()) {
            history.add(spinnerRow("History messages", oc.getHistoryLimit(), 200, 5000, 100, v -> { oc.setHistoryLimit(v); saveOverlay(); }));
            history.add(help("Mouse wheel over the overlay reads older messages. Right-click > Latest returns to new messages. History stays visible without fading and ends when the plugin stops."));
        }
        messages.add(accordionPanel(oc, "history", "SESSION HISTORY", true, history));

        JPanel appearance = panel(CARD);
        appearance.setLayout(new BoxLayout(appearance, BoxLayout.Y_AXIS));
        appearance.add(comboRow("Text Size", FontSize.values(), oc.getFontSize(), v -> { oc.setFontSize(v); saveOverlay(); }));
        appearance.add(comboRow("Text Font", FONT_FAMILIES, oc.getFontFamily(), v -> { oc.setFontFamily(v); saveOverlay(); }));
        appearance.add(comboRow("Text Alignment", TextAlignment.values(), oc.getTextAlignment(), v -> { oc.setTextAlignment(v); saveOverlay(); }));
        appearance.add(checkRow("Bold", oc.isBoldText(), v -> { oc.setBoldText(v); saveOverlay(); }));
        appearance.add(checkRow("Timestamps", oc.isShowTimestamp(), v -> { oc.setShowTimestamp(v); saveOverlay(); }));
        appearance.add(comboRow("Timestamp style", TimestampStyle.values(), oc.getTimestampStyle(), v -> { oc.setTimestampStyle(v); saveOverlay(); }));
        looks.add(accordionPanel(oc, "appearance", "TEXT", true, appearance));
        JPanel frame = panel(CARD);
        frame.setLayout(new BoxLayout(frame, BoxLayout.Y_AXIS));
        frame.add(spinnerRow("Width (px)", plugin.getOverlayWidth(oc), 150, Math.max(1024, plugin.getOverlayWidth(oc)), 8, v -> plugin.setOverlayWidth(oc, v)));
        frame.add(spinnerRow("Padding X", oc.getPaddingHorizontal(), 0, 50, 1, v -> { oc.setPaddingHorizontal(v); saveOverlay(); }));
        frame.add(spinnerRow("Padding Y", oc.getPaddingVertical(), 0, 50, 1, v -> { oc.setPaddingVertical(v); saveOverlay(); }));
        frame.add(checkRow("Rounded corners", oc.isRoundedCorners(), v -> { oc.setRoundedCorners(v); saveOverlay(); }));
        looks.add(accordionPanel(oc, "frame", "FRAME", false, frame));
        JPanel border = panel(CARD);
        border.setLayout(new BoxLayout(border, BoxLayout.Y_AXIS));
        border.add(checkRow("Border", oc.isBorderEnabled(), v -> { oc.setBorderEnabled(v); saveOverlay(); rebuild(); }));
        if (oc.isBorderEnabled()) {
            addColorControls(border, "Border", oc.getBorderColors(), () -> {}, true);
            border.add(comboRow("Border Thickness", BorderThickness.values(), oc.getBorderThickness(), v -> { oc.setBorderThickness(v); saveOverlay(); }));
        }
        colors.add(accordionPanel(oc, "borderColors", "BORDER", true, border));
        JPanel background = panel(CARD);
        background.setLayout(new BoxLayout(background, BoxLayout.Y_AXIS));
        background.add(checkRow("Background", oc.isBackgroundEnabled(), v -> { oc.setBackgroundEnabled(v); saveOverlay(); rebuild(); }));
        if (oc.isBackgroundEnabled() && oc.getBackgroundImage().isEmpty()) {
            background.add(colorRow("Background Color", oc.getBackgroundColour(), v -> {
                int a = oc.getBackgroundColour().getAlpha();
                Color c = new Color(v.getRed(), v.getGreen(), v.getBlue(), a);
                oc.setBackgroundColour(c);
                saveOverlay();
            }));
        }
        if (oc.isBackgroundEnabled()) {
            background.add(spinnerRow("Opacity (%)", Math.max(1, Math.round(oc.getBackgroundColour().getAlpha() * 100f / 255)), 1, 100, 1, v -> {
                Color c = oc.getBackgroundColour();
                oc.setBackgroundColour(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(v * 255f / 100)));
                saveOverlay();
            }));
        }
        boolean hasImage = !oc.getBackgroundImage().isEmpty();
        JButton chooseImage = button(plugin.isBackgroundImageLoading() ? "Loading image…" : hasImage ? "Replace image" : "Choose image");
        chooseImage.setEnabled(!plugin.isBackgroundImageLoading());
        chooseImage.setToolTipText("Choose a PNG, JPEG or animated GIF background. The image fills the overlay using its background opacity.");
        chooseImage.addActionListener(e -> plugin.chooseOverlayBackground(chooseImage, oc));
        background.add(chooseImage);
        if (hasImage) {
            background.add(help(oc.getBackgroundImageLabel()));
            JButton removeImage = button("Remove image");
            removeImage.addActionListener(e -> plugin.removeOverlayBackground(oc));
            background.add(removeImage);
        }
        String imageStatus = plugin.getBackgroundImageStatus(oc);
        if (imageStatus != null) background.add(help(imageStatus));
        colors.add(accordionPanel(oc, "backgroundColors", "BACKGROUND", false, background));

        ImprovedChatConfig dockConfig = plugin.getInputDockConfig();
        if (dockConfig != null) {
            JPanel buttons = panel(CARD);
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
            buttons.add(help("Shared by the floating input dock and all attached input buttons."));
            buttons.add(colorRow("Button background", dockConfig.inputDockButtonBackground(),
                    value -> plugin.setInputDockSetting("inputDockButtonBackground", value)));
            buttons.add(colorRow("Button border", dockConfig.inputDockButtonBorder(),
                    value -> plugin.setInputDockSetting("inputDockButtonBorder", value)));
            buttons.add(colorRow("Selected border", dockConfig.inputDockSelectedBorder(),
                    value -> plugin.setInputDockSetting("inputDockSelectedBorder", value)));
            buttons.add(colorRow("Text and icons", dockConfig.inputDockButtonForeground(),
                    value -> plugin.setInputDockSetting("inputDockButtonForeground", value)));
            buttons.add(colorRow("Unavailable text/icons", dockConfig.inputDockDisabledForeground(),
                    value -> plugin.setInputDockSetting("inputDockDisabledForeground", value)));
            colors.add(accordionPanel(oc, "inputButtonColors", "CHAT INPUT BUTTONS", false, buttons));
        }


        JPanel positionContent = panel(CARD);
        positionContent.setLayout(new BoxLayout(positionContent, BoxLayout.Y_AXIS));
        positionContent.add(comboRow("Mode", PLACEMENT_MODES, oc.getPlacementMode(), v -> {
            oc.setPlacementMode(v);
            saveOverlay();
            rebuild();
        }));
        if (oc.getPlacementMode() == PlacementMode.ABOVE_PLAYER || oc.getPlacementMode() == PlacementMode.BELOW_PLAYER) {
            positionContent.add(spinnerRow("X Offset", oc.getOffsetX(), -500, 500, 1, v -> { oc.setOffsetX(v); saveOverlay(); }));
            positionContent.add(spinnerRow("Y Offset", oc.getOffsetY(), -500, 500, 1, v -> { oc.setOffsetY(v); saveOverlay(); }));
        }
        if (oc.getPlacementMode() == PlacementMode.FREE) positionContent.setToolTipText(plugin.movementHint());
        looks.add(accordionPanel(oc, "position", "POSITION", false, positionContent));

        messages.add(accordionPanel(oc, "filtering", "MESSAGE FILTERING", false, buildFiltering(oc)));

        JPanel clanDisplay = panel(CARD);
        clanDisplay.setLayout(new BoxLayout(clanDisplay, BoxLayout.Y_AXIS));
        clanDisplay.add(checkRow("Show Clan Rank Icons", oc.isShowClanRankIcons(), v -> {
            oc.setShowClanRankIcons(v);
            saveOverlay();
        }));
        clanDisplay.add(checkRow("Show Offline Status", oc.isShowOfflineStatus(), v -> {
            oc.setShowOfflineStatus(v);
            saveOverlay();
            rebuild();
        }));
        if (oc.isShowOfflineStatus()) {
            clanDisplay.add(checkRow("Show Offline Icon", oc.isShowOfflineIcon(), v -> {
                oc.setShowOfflineIcon(v);
                saveOverlay();
            }));
        }
        if (hasClanMessages(oc)) messages.add(accordionPanel(oc, "clanDisplay", "CLAN DISPLAY", false, clanDisplay));

        JPanel overlayColors = panel(CARD);
        overlayColors.setLayout(new BoxLayout(overlayColors, BoxLayout.Y_AXIS));
        OverlayColorSettings textColors = oc.getTextColors();
        if (textColors.getMode() == AnimatedTextStyle.NORMAL) {
            overlayColors.add(comboRow("Text color source", new String[]{"Message colors", "Channel palette", "Custom color"},
                    oc.isOverlayColorOverrideEnabled() ? "Custom color" : oc.isRetainMessageColors() ? "Message colors" : "Channel palette",
                    v -> { oc.setOverlayColorOverrideEnabled("Custom color".equals(v)); oc.setContextualColours("Message colors".equals(v)); saveOverlay(); rebuild(); }));
        }
        addColorControls(overlayColors, "Text", textColors, () -> oc.setOverlayColorOverrideEnabled(true), oc.isOverlayColorOverrideEnabled());
        if (textColors.getMode() == AnimatedTextStyle.NORMAL && !oc.isOverlayColorOverrideEnabled()) {
            Component source = comboRow("Color palette", ChatColorSource.values(), oc.getTextSettings().chatColorSource(), v -> { oc.getTextSettings().setSource(v); saveOverlay(); });
            enableTree(source, oc.isContextualColours());
            overlayColors.add(source);
        }
        JPanel fallback = panel(CARD);
        fallback.setLayout(new BoxLayout(fallback, BoxLayout.Y_AXIS));
        String[] channelNames = {"Game", "Public", "Private", "Friends chat", "Clan", "Guest clan", "GIM", "Trade", "Challenge", "Did you know", "Broadcast", "Autochat"};
        for (int i = 0; i < channelNames.length; i++) {
            final int index = i;
            fallback.add(colorRow(channelNames[i], oc.getTextSettings().getChannelColor(i), v -> { oc.getTextSettings().setChannelColor(index, v); saveOverlay(); }));
        }
        if (textColors.getMode() == AnimatedTextStyle.NORMAL && !oc.isOverlayColorOverrideEnabled())
            overlayColors.add(accordionPanel(oc, "fallbackColors", "PALETTE COLORS", false, fallback));
        if (hasClanMessages(oc) && oc.isShowOfflineStatus()) {
            overlayColors.add(checkRow("Color Offline Names", oc.isColorOfflineNames(), v -> {
                oc.setColorOfflineNames(v); saveOverlay(); rebuild();
            }));
            if (oc.isColorOfflineNames()) overlayColors.add(colorRow("Offline Color", oc.getOfflineColor(), v -> {
                oc.setOfflineColor(v); saveOverlay();
            }));
        }
        colors.add(accordionPanel(oc, "overlayColors", "TEXT COLORS", true, overlayColors), 0);
        JPanel timestamps = panel(CARD);
        timestamps.setLayout(new BoxLayout(timestamps, BoxLayout.Y_AXIS));
        if (oc.getTimestampColors().getMode() == AnimatedTextStyle.NORMAL) {
            timestamps.add(checkRow("Custom timestamp color", oc.isTimestampColorOverride(), v -> {
                oc.setTimestampColorOverride(v); saveOverlay(); rebuild();
            }));
        }
        addColorControls(timestamps, "Timestamp", oc.getTimestampColors(), () -> oc.setTimestampColorOverride(true), oc.isTimestampColorOverride());
        colors.add(accordionPanel(oc, "timestampColors", "TIMESTAMP COLORS", true, timestamps), 1);

        alerts.add(notificationControls(oc));

        JPanel attention = panel(CARD);
        attention.setLayout(new BoxLayout(attention, BoxLayout.Y_AXIS));
        attention.add(checkRow("Flash", oc.isFlashEnabled(), v -> { oc.setFlashEnabled(v); saveOverlay(); rebuild(); }));
        if (oc.isFlashEnabled()) {
            attention.add(comboRow("Trigger", AttentionTrigger.values(), oc.getAttentionTrigger(), v -> { oc.setAttentionTrigger(v); saveOverlay(); }));
            attention.add(checkRow("Flash While Focused", oc.isFlashWhileFocused(), v -> { oc.setFlashWhileFocused(v); saveOverlay(); }));
            attention.add(comboRow("Stop", AttentionStopMode.values(), oc.getAttentionStopMode(), v -> { oc.setAttentionStopMode(v); saveOverlay(); }));
            attention.add(spinnerRow("Duration (sec)", oc.getFlashDurationSeconds(), 1, 60, 1, v -> { oc.setFlashDurationSeconds(v); saveOverlay(); }));
            attention.add(comboRow("Speed", AttentionSpeed.values(), oc.getAttentionSpeed(), v -> { oc.setAttentionSpeed(v); saveOverlay(); }));
            attention.add(checkRow("Message", oc.isFlashMessage(), v -> { oc.setFlashMessage(v); saveOverlay(); }));
            attention.add(checkRow("Border", oc.isFlashBorder(), v -> { oc.setFlashBorder(v); saveOverlay(); }));
            attention.add(checkRow("Background", oc.isFlashBackground(), v -> { oc.setFlashBackground(v); saveOverlay(); }));
        }
        alerts.add(accordionPanel(oc, "attention", "OVERLAY FLASH", false, attention));

        looks.add(accordion(oc, "advanced", "LAYOUT & INPUT", false,
                checkRow("Dynamic Height", oc.isDynamicHeight(), v -> { oc.setDynamicHeight(v); saveOverlay(); }),
                checkRow("Hide Duplicate Count", oc.isHideDuplicateCount(), v -> { oc.setHideDuplicateCount(v); saveOverlay(); }),
                checkRow("Input Preview", oc.isShowInputPreview(), v -> { oc.setShowInputPreview(v); saveOverlay(); rebuild(); }),
                inputPreviewOption(oc)
        ));
        body.add(Box.createVerticalStrut(8));

        colors.add(buildHighlights(oc));
        messages.add(buildReplacements(oc));
        JTabbedPane tabs = new JTabbedPane() {
            @Override public Dimension getPreferredSize() {
                Component active = getSelectedComponent();
                int rows = getUI() == null ? 1 : Math.max(1, getUI().getTabRunCount(this));
                int headerHeight = Math.max(35, rows * (getFontMetrics(getFont()).getHeight() + 10) + 6);
                return new Dimension(0, active == null ? headerHeight : active.getPreferredSize().height + headerHeight);
            }
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        tabs.setFont(UIManager.getFont("Label.font"));
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected Insets getTabInsets(int placement, int index) {
                return new Insets(4, 3, 4, 3);
            }
        });
        alignStackChildren(messages); alignStackChildren(looks); alignStackChildren(colors); alignStackChildren(alerts);
        tabs.addTab("Messages", messages);
        tabs.addTab("Style", looks);
        tabs.addTab("Colors", colors);
        tabs.addTab("Alerts", alerts);
        tabs.setSelectedIndex(selectedTabs.getOrDefault(oc.getId(), 0));
        tabs.addChangeListener(e -> {
            selectedTabs.put(oc.getId(), tabs.getSelectedIndex());
            body.revalidate();
            body.repaint();
        });
        body.add(tabs);
        JButton duplicate = button("Duplicate");
        duplicate.setAlignmentX(Component.LEFT_ALIGNMENT);
        duplicate.addActionListener(e -> {
            openEditor(plugin.duplicateOverlay(oc).getId());
        });
        JPanel actions = panel(BG);
        actions.setLayout(new java.awt.GridLayout(1, 2, 6, 0));
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        actions.add(duplicate);
        JButton delete = button("Delete");
        delete.setForeground(DANGER);
        delete.setAlignmentX(Component.LEFT_ALIGNMENT);
        delete.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(editorPage, help("Delete “" + oc.getName() + "”? This removes its saved settings."),
                    "Delete overlay", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            plugin.removeOverlay(oc);
            editingOverlayId = null;
            rebuild();
        });
        actions.add(delete);
        body.add(Box.createVerticalStrut(6));
        body.add(actions);
        body.add(Box.createVerticalGlue());
        return body;
    }

    private JPanel notificationControls(OverlayConfig oc) {
        JPanel controls = panel(CARD);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        Notification notification = oc.getNotification();
        controls.add(checkRow("Enable Notifications", notification.isEnabled(), value -> {
            oc.setNotification(oc.getNotification().withEnabled(value)); saveOverlay(); rebuild();
        }));
        if (!notification.isEnabled()) return controls;
        controls.add(comboRow("Notify For", AttentionTrigger.values(), oc.getNotificationTrigger(), value -> {
            oc.setNotificationTrigger(value); saveOverlay();
        }));
        controls.add(checkRow("Customize Notification", notification.isOverride(), value -> {
            oc.setNotification(value ? plugin.customizeNotification(oc.getNotification()) : oc.getNotification().withOverride(false));
            saveOverlay(); rebuild();
        }));
        if (!notification.isOverride()) {
            return controls;
        }
        controls.add(checkRow("Desktop Notification", notification.isTray(), value -> updateNotification(oc, n -> n.withTray(value))));
        controls.add(comboRow("Sound", NotificationSound.values(), notification.getSound(), value -> {
            updateNotification(oc, n -> n.withSound(value)); rebuild();
        }));
        if (notification.getSound() == NotificationSound.CUSTOM) {
            controls.add(textFieldRow("Sound Filename", notification.getSoundName() == null ? "" : notification.getSoundName(), value ->
                    updateNotification(oc, n -> n.withSoundName(value))));
        }
        controls.add(spinnerRow("Volume (%)", notification.getVolume(), 0, 100, 5, value -> updateNotification(oc, n -> n.withVolume(value))));
        controls.add(checkRow("Notify While Focused", notification.isSendWhenFocused(), value -> updateNotification(oc, n -> n.withSendWhenFocused(value))));
        controls.add(comboRow("Request Focus", RequestFocusType.values(), notification.getRequestFocus(), value -> updateNotification(oc, n -> n.withRequestFocus(value))));
        controls.add(comboRow("Window Flash", FlashNotification.values(), notification.getFlash(), value -> updateNotification(oc, n -> n.withFlash(value))));
        controls.add(colorRow("Flash Color", notification.getFlashColor(), value -> updateNotification(oc, n -> n.withFlashColor(value))));
        controls.add(spinnerRow("Timeout (ms)", notification.getTimeout(), 0, Integer.MAX_VALUE, 1000, value -> updateNotification(oc, n -> n.withTimeout(value))));
        controls.add(checkRow("Chatbox Notification", notification.isGameMessage(), value -> updateNotification(oc, n -> n.withGameMessage(value))));
        return controls;
    }

    private void addColorControls(JPanel content, String target, OverlayColorSettings settings,
            Runnable useCustomColor, boolean customStatic) {
        AnimatedTextStyle[] modes = settings.getMode() == AnimatedTextStyle.RGB_STATIC ? AnimatedTextStyle.values()
                : new AnimatedTextStyle[]{AnimatedTextStyle.NORMAL, AnimatedTextStyle.DUO_CYCLE, AnimatedTextStyle.DUO_WAVE,
                    AnimatedTextStyle.RGB_CYCLE, AnimatedTextStyle.RGB_WAVE};
        content.add(comboRow(target + " mode", modes, settings.getMode(), v -> {
            settings.setMode(v); useCustomColor.run(); saveOverlay(); rebuild();
        }));
        if (settings.getMode() == AnimatedTextStyle.NORMAL || settings.isDuo()) {
            Component primary = colorRow(target + (settings.isDuo() ? " color 1" : " color"), settings.getPrimary(), v -> {
                Color old = settings.getPrimary();
                settings.setPrimary(new Color(v.getRed(), v.getGreen(), v.getBlue(), old.getAlpha()));
                useCustomColor.run(); saveOverlay();
            });
            enableTree(primary, settings.isDuo() || customStatic);
            content.add(primary);
        }
        if (settings.isDuo()) content.add(colorRow(target + " color 2", settings.getSecondary(), v -> {
            settings.setSecondary(v); saveOverlay();
        }));
        if (settings.isAnimated()) content.add(spinnerRow(target + " cycle (sec)", settings.getCycleSeconds(), 2, 30, 1,
                v -> { settings.setCycleSeconds(v); saveOverlay(); }));
        content.add(checkRow("Pulse " + target.toLowerCase(java.util.Locale.ROOT), settings.isPulse(), v -> {
            settings.setPulse(v); saveOverlay(); rebuild();
        }));
        if (settings.isPulse()) content.add(spinnerRow(target + " pulse (sec)", settings.getPulseSeconds(), 1, 60, 1,
                v -> { settings.setPulseSeconds(v); saveOverlay(); }));
    }

    private Component buildHighlights(OverlayConfig oc) {
        com.improvedchat.overlay.OverlayTextSettings rules = oc.getTextSettings();
        JPanel content = panel(CARD);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(checkRow("Allow rule overrides", rules.isHighlightsOverrideDefaults(), v -> { rules.setHighlightsOverrideDefaults(v); saveOverlay(); }));
        content.add(checkRow("Solid color rules", rules.enableMessageColorRules(), v -> { rules.setSolidEnabled(v); saveOverlay(); rebuild(); }));
        if (rules.enableMessageColorRules()) {
            content.add(textAreaRow("Color rules", rules.messageColorRules(), v -> { rules.setSolidRules(v); saveOverlay(); }));
            content.add(checkRow("Include player chat", rules.includePlayerChatInColorRules(), v -> { rules.setIncludePlayer(v); saveOverlay(); }));
            JPanel palette = panel(CARD);
            palette.setLayout(new BoxLayout(palette, BoxLayout.Y_AXIS));
            for (int i = 0; i < 9; i++) {
                final int index = i;
                palette.add(colorRow("Rule color " + (i + 1), rules.getPalette(i), v -> { rules.setPalette(index, v); saveOverlay(); }));
            }
            content.add(accordionPanel(oc, "highlightPalette", "RULE PALETTE", false, palette));
        }
        addRgbHighlights(content, oc);
        if (rules.enableOverlayRainbowRules() || oc.getTextColors().getMode() == AnimatedTextStyle.RGB_STATIC
                || oc.getTimestampColors().getMode() == AnimatedTextStyle.RGB_STATIC) {
            content.add(comboRow("RGB spread", RainbowStyle.values(), rules.rainbowStyle(), v -> { rules.setSpread(v); saveOverlay(); }));
        }
        return accordionPanel(oc, "highlights", "Specific Message Highlighting", false, content);
    }

    private void addRgbHighlights(JPanel content, OverlayConfig oc) {
        com.improvedchat.overlay.OverlayTextSettings rules = oc.getTextSettings();
        content.add(checkRow("RGB highlight rules", rules.enableOverlayRainbowRules(), v -> { rules.setRainbowEnabled(v); saveOverlay(); rebuild(); }));
        if (rules.enableOverlayRainbowRules()) {
            content.add(textAreaRow("RGB patterns", rules.overlayRainbowRules(), v -> { rules.setRainbowRules(v); saveOverlay(); }));
            boolean legacyDuo = oc.isCustomRgbCycle() && rules.getHighlightStyle() == AnimatedTextStyle.RGB_CYCLE;
            String[] effects = legacyDuo ? new String[] {"Use overlay default", "RGB Static", "RGB cycle", "RGB wave", "Duo Cycle"}
                    : new String[] {"Use overlay default", "RGB Static", "RGB cycle", "RGB wave"};
            content.add(comboRow("Highlight effect", effects,
                    legacyDuo ? "Duo Cycle" : rules.getHighlightStyle() == AnimatedTextStyle.NORMAL ? "Use overlay default" : rules.getHighlightStyle().toString(),
                    v -> {
                        if (!"Duo Cycle".equals(v)) {
                            oc.clearLegacyHighlightCycle();
                            rules.setHighlightStyle("Use overlay default".equals(v) ? AnimatedTextStyle.NORMAL : "RGB Static".equals(v) ? AnimatedTextStyle.RGB_STATIC : "RGB cycle".equals(v) ? AnimatedTextStyle.RGB_CYCLE : AnimatedTextStyle.RGB_WAVE);
                        }
                        saveOverlay(); rebuild();
                    }));
        }
    }

    private Component buildReplacements(OverlayConfig oc) {
        JPanel content = panel(CARD);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(checkRow("Enable message replacer (Beta)", oc.isMessageReplacerEnabled(), v -> { oc.setMessageReplacerEnabled(v); saveOverlay(); rebuild(); }));
        if (oc.isMessageReplacerEnabled()) {
            for (com.improvedchat.overlay.OverlayTextReplacement rule : oc.getTextReplacements()) {
                if (rule == null) continue;
                String id = rule.getId();
                JPanel fields = panel(CARD);
                fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
                fields.add(checkRow("Use this replacement", rule.isEnabled(), v -> changeReplacement(oc, id, r -> r.withEnabled(v))));
                fields.add(textFieldRow("Original message", rule.getOriginal(), v -> changeReplacement(oc, id, r -> r.withOriginal(v))));
                fields.add(textFieldRow("Show instead", rule.getReplacement(), v -> changeReplacement(oc, id, r -> r.withReplacement(v))));
                JButton remove = button("REMOVE REPLACEMENT");
                remove.addActionListener(e -> { changeReplacement(oc, id, r -> null); rebuild(); });
                fields.add(remove);
                content.add(accordionPanel(oc, "replacement:" + id, "REPLACEMENT", true, fields));
            }
            JButton add = button("+ ADD REPLACEMENT");
            add.addActionListener(e -> {
                java.util.List<com.improvedchat.overlay.OverlayTextReplacement> rules = new java.util.ArrayList<>(oc.getTextReplacements());
                rules.add(new com.improvedchat.overlay.OverlayTextReplacement());
                oc.setTextReplacements(rules); saveOverlay(); rebuild();
            });
            content.add(add);
            JTextField sample = new JTextField(); styleField(sample);
            content.add(controlRow("Test message text", sample));
            JTextArea result = (JTextArea)help("");
            JButton test = button("TEST REPLACEMENTS");
            test.addActionListener(e -> result.setText("Overlay text: " + MessageColorRuleEngine.plainText(
                    com.improvedchat.overlay.OverlayTextReplacements.displayText(oc, sample.getText()))));
            content.add(test); content.add(result);
        }
        return accordionPanel(oc, "replacements", "MESSAGE REPLACER (BETA)", false, content);
    }

    private void changeReplacement(OverlayConfig oc, String id,
            java.util.function.UnaryOperator<com.improvedchat.overlay.OverlayTextReplacement> change) {
        java.util.List<com.improvedchat.overlay.OverlayTextReplacement> rules = new java.util.ArrayList<>(oc.getTextReplacements());
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i) == null || !id.equals(rules.get(i).getId())) continue;
            com.improvedchat.overlay.OverlayTextReplacement next = change.apply(rules.get(i));
            if (next == null) rules.remove(i); else rules.set(i, next);
            oc.setTextReplacements(rules); saveOverlay(); return;
        }
    }

    private void updateNotification(OverlayConfig oc, java.util.function.UnaryOperator<Notification> change) {
        oc.setNotification(change.apply(oc.getNotification())); saveOverlay();
    }

    private Component help(String text) {
        JTextArea area = new JTextArea(text) {
            @Override public Dimension getPreferredSize() {
                int width = getParent() != null && getParent().getWidth() > 0
                        ? getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right
                        : Math.max(140, ImprovedChatPanel.this.getWidth() - 48);
                setSize(Math.max(100, width), Short.MAX_VALUE);
                Dimension preferred = super.getPreferredSize();
                return new Dimension(width, preferred.height);
            }
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
            @Override public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
        };
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(MUTED);
        area.setFont(UIManager.getFont("Label.font"));
        area.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private Component messageSelectionHelp(OverlayConfig oc) {
        return oc.getMessageTypes().isEmpty() ? help("Choose message types to see live messages.")
                : Box.createVerticalStrut(0);
    }

    private Component inputPreviewOption(OverlayConfig oc) {
        Component row = checkRow("Preview only while typing", oc.isPreviewOnlyWhenTyping(),
                v -> { oc.setPreviewOnlyWhenTyping(v); saveOverlay(); });
        enableTree(row, oc.isShowInputPreview());
        return row;
    }

    private void enableTree(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) enableTree(child, enabled);
        }
    }

    private JPanel buildFiltering(OverlayConfig oc) {
        JPanel filtering = panel(CARD);
        filtering.setLayout(new BoxLayout(filtering, BoxLayout.Y_AXIS));
        boolean overridden = plugin.isGlobalFilterOverride();
        Component globalToggle = checkRow("Use RuneLite Chat Filter", oc.isUseGlobalChatFilter(), v -> {
            oc.setUseGlobalChatFilter(v); saveOverlay(); rebuild();
        });
        enableTree(globalToggle, !overridden);
        filtering.add(globalToggle);
        boolean usesGlobal = overridden || oc.isUseGlobalChatFilter();
        if (usesGlobal) {
            filtering.add(help(overridden
                    ? "Global override is on. Change ‘Use Chat Filter Globally’ in Improved Chat and Overlays settings to use these custom rules."
                    : "This overlay uses RuneLite's Chat Filter rules."));
            filtering.add(help(plugin.isGlobalFilterAvailable()
                    ? "RuneLite Chat Filter is enabled. Custom rules below are saved but inactive."
                    : "RuneLite Chat Filter is disabled: no content filtering is applied. Enable it in RuneLite or turn off global filtering."));
        }
        JPanel custom = panel(CARD);
        custom.setLayout(new BoxLayout(custom, BoxLayout.Y_AXIS));
        custom.add(comboRow("Custom filter mode", OverlayFilterMode.values(), oc.getFilterMode(), v -> {
            oc.setFilterMode(v); saveOverlay(); rebuild();
        }));
        JPanel feedback = panel(CARD);
        feedback.setLayout(new BoxLayout(feedback, BoxLayout.Y_AXIS));
        Runnable validate = () -> {
            feedback.removeAll();
            if (oc.getFilterMode() != OverlayFilterMode.OFF) {
                for (String error : new OverlayMessageFilter().validationMessages(oc)) feedback.add(help(error));
            }
            feedback.revalidate(); feedback.repaint();
        };
        if (oc.getFilterMode() != OverlayFilterMode.OFF) {
            custom.add(textFieldRow("Words or phrases", oc.getFilteredWords(), v -> {
                oc.setFilteredWords(v); saveOverlay(); validate.run();
            }));
            custom.add(textAreaRow("Regular expressions", oc.getFilteredRegex(), v -> {
                oc.setFilteredRegex(v); saveOverlay(); validate.run();
            }));
            custom.add(textAreaRow("Player names", oc.getFilteredNames(), v -> {
                oc.setFilteredNames(v); saveOverlay(); validate.run();
            }));
            custom.add(feedback);
            JTextField sampleName = new JTextField();
            JTextField sampleBody = new JTextField();
            styleField(sampleName); styleField(sampleBody);
            custom.add(controlRow("Test player name", sampleName));
            custom.add(controlRow("Test message", sampleBody));
            JButton test = button("TEST CUSTOM RULES");
            JTextArea result = (JTextArea) help("");
            test.addActionListener(e -> {
                boolean hidden = new OverlayMessageFilter().shouldExclude(oc,
                        OverlayMessage.senderMessage(sampleName.getText(), null, sampleBody.getText(), 0, ChatMessageType.PUBLICCHAT, false));
                result.setText(hidden ? "Hidden by these custom rules." : "Allowed by these custom rules.");
                validate.run();
            });
            custom.add(test); custom.add(result);
        }
        validate.run();
        enableTree(custom, !usesGlobal);
        filtering.add(custom);
        return filtering;
    }

    private Component accordion(OverlayConfig oc, String key, String title, boolean defaultOpen, Component... rows) {
        JPanel content = panel(CARD);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (Component row : rows) content.add(row);
        return accordionPanel(oc, key, title, defaultOpen, content);
    }

    private Component accordionPanel(OverlayConfig oc, String key, String title, boolean defaultOpen, JPanel content) {
        String stateKey = oc.getId() + ":" + key;
        boolean open = openSections.containsKey(stateKey) ? openSections.get(stateKey) : defaultOpen;

        JPanel root = panel(CARD);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createLineBorder(BORDER));
        root.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JButton header = button((open ? "▼  " : "▶  ") + title);
        header.setToolTipText(tooltipFor(title));
        header.setHorizontalAlignment(JButton.LEFT);
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        header.setForeground(ACCENT);
        header.setBackground(CARD_ALT);
        header.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        root.add(header);

        alignStackChildren(content);
        content.setBorder(BorderFactory.createEmptyBorder(3, 7, 6, 7));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.setVisible(open);
        root.add(content);

        header.addActionListener(e -> {
            boolean next = !content.isVisible();
            content.setVisible(next);
            openSections.put(stateKey, next);
            header.setText((next ? "▼  " : "▶  ") + title);

            for (java.awt.Container ancestor = root; ancestor != null; ancestor = ancestor.getParent()) {
                ancestor.invalidate();
                if (ancestor == ImprovedChatPanel.this) break;
            }
            root.revalidate();
            root.repaint();
        });
        return root;
    }

    private static void onTextChange(javax.swing.text.Document document, Runnable save) {
        document.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { save.run(); }
            public void removeUpdate(DocumentEvent event) { save.run(); }
            public void changedUpdate(DocumentEvent event) { save.run(); }
        });
    }

    private boolean hasClanMessages(OverlayConfig oc) {
        return oc.getMessageTypes().contains(ChatMessageType.CLAN_CHAT)
                || oc.getMessageTypes().contains(ChatMessageType.CLAN_GUEST_CHAT)
                || oc.getMessageTypes().contains(ChatMessageType.CLAN_GIM_CHAT);
    }

    private Component textFieldRow(String name, String value, java.util.function.Consumer<String> setter) {
        JTextField field = new JTextField(value);
        styleField(field);
        onTextChange(field.getDocument(), () -> setter.accept(field.getText()));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { setter.accept(field.getText()); }
        });
        return controlRow(name, field);
    }

    private Component textAreaRow(String name, String value, java.util.function.Consumer<String> setter) {
        JTextArea area = new JTextArea(value == null ? "" : value, 3, 12);
        area.setName(name);
        area.setToolTipText(tooltipFor(name));
        area.setLineWrap(false);
        area.setBackground(CONTROL);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        onTextChange(area.getDocument(), () -> setter.accept(area.getText()));
        area.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { setter.accept(area.getText()); }
        });
        JScrollPane scroller = new JScrollPane(area);
        scroller.setPreferredSize(new Dimension(180, 86));
        scroller.setBorder(BorderFactory.createLineBorder(BORDER));
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        JPanel row = panel(CARD);
        row.setLayout(new BorderLayout(0, 4));
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = label(name, TEXT, 12, Font.PLAIN);
        title.setLabelFor(area);
        row.add(title, BorderLayout.NORTH);
        row.add(scroller, BorderLayout.CENTER);
        return row;
    }

    private Component checkRow(String name, boolean selected, java.util.function.Consumer<Boolean> setter) {
        JCheckBox box = new JCheckBox(name, selected);
        box.setOpaque(false);
        box.setForeground(TEXT);
        box.setFont(UIManager.getFont("Label.font"));
        box.setFocusPainted(true);
        box.setName(name);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.addActionListener(e -> setter.accept(box.isSelected()));
        JPanel row = panel(CARD);
        row.setLayout(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setToolTipText(tooltipFor(name));
        row.add(box, BorderLayout.CENTER);
        return row;
    }

    private Component spinnerRow(String name, int value, int min, int max, int step, java.util.function.Consumer<Integer> setter) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setPreferredSize(new Dimension(94, 24));
        spinner.setMaximumSize(new Dimension(94, 24));
        spinner.addChangeListener(e -> setter.accept(((Number) spinner.getValue()).intValue()));
        return controlRow(name, spinner);
    }

    private <T> Component comboRow(String name, T[] values, T selected, java.util.function.Consumer<T> setter) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setSelectedItem(selected);
        combo.setPreferredSize(new Dimension(112, 24));
        combo.setMaximumSize(new Dimension(112, 24));
        combo.addActionListener(e -> setter.accept((T) combo.getSelectedItem()));
        return controlRow(name, combo);
    }

    private Component colorRow(String name, Color color, java.util.function.Consumer<Color> setter) {
        JButton swatch = colorButton(color, "");
        swatch.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(swatch, name, swatch.getBackground());
            if (chosen != null) {
                setter.accept(chosen);
                swatch.setBackground(chosen);
            }
        });
        return controlRow(name, swatch);
    }

    private Component overlayHotkeyRow(OverlayConfig oc) {
        JButton key = button(oc.getToggleKeybind().toString());
        key.setFocusTraversalKeysEnabled(false);
        key.setToolTipText("Click, then press a key combination. Escape clears it. Overlays sharing a key toggle together; Show overlay still controls chatbox visibility.");
        key.addActionListener(e -> { key.setText("Press a key"); key.requestFocusInWindow(); });
        key.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                e.consume();
                int code = e.getKeyCode();
                if (code == java.awt.event.KeyEvent.VK_SHIFT || code == java.awt.event.KeyEvent.VK_CONTROL
                    || code == java.awt.event.KeyEvent.VK_ALT || code == java.awt.event.KeyEvent.VK_META) return;
                oc.setToggleKeybind(code == java.awt.event.KeyEvent.VK_ESCAPE
                    ? net.runelite.client.config.Keybind.NOT_SET : new net.runelite.client.config.Keybind(e));
                key.setText(oc.getToggleKeybind().toString());
                saveOverlay();
            }
            @Override public void keyReleased(java.awt.event.KeyEvent e) { e.consume(); }
            @Override public void keyTyped(java.awt.event.KeyEvent e) { e.consume(); }
        });
        key.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { key.setText(oc.getToggleKeybind().toString()); }
        });
        return controlRow("Toggle hotkey", key);
    }

    private Component controlRow(String name, Component control) {
        JPanel row = panel(CARD);
        row.setLayout(new BorderLayout(0, 3));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        boolean compact = control instanceof JSpinner || control instanceof JButton;
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 32 : 51));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = label(name, TEXT, 12, Font.PLAIN);
        title.setLabelFor(control);
        control.setName(name);
        if (control instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) control).getAccessibleContext().setAccessibleName(name);
        }
        if (control instanceof javax.swing.JComponent) ((javax.swing.JComponent) control).setToolTipText(tooltipFor(name));
        if (control instanceof JSpinner && ((JSpinner) control).getEditor() instanceof JSpinner.DefaultEditor)
            ((JSpinner.DefaultEditor) ((JSpinner) control).getEditor()).getTextField().setToolTipText(tooltipFor(name));
        title.setToolTipText(tooltipFor(name));
        row.add(title, compact ? BorderLayout.CENTER : BorderLayout.NORTH);
        row.add(control, compact ? BorderLayout.EAST : BorderLayout.CENTER);
        return row;
    }

    private Component messageTypesRow(OverlayConfig oc) {
        JButton b = button(oc.getMessageTypes().size() + " selected");
        b.setPreferredSize(new Dimension(102, 24));
        b.setMaximumSize(new Dimension(102, 24));
        b.addActionListener(e -> showMessageTypeMenu(oc, b));
        return controlRow("Message Types", b);
    }

    private void showMessageTypeMenu(OverlayConfig oc, JButton anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem all = new JMenuItem("Select All");
        all.addActionListener(e -> {
            EnumSet<ChatMessageType> set = EnumSet.noneOf(ChatMessageType.class);
            for (MessageCategory cat : MessageCategory.values()) set.addAll(cat.getTypes());
            oc.setMessageTypes(set);
            saveOverlay();
            rebuild();
        });
        JMenuItem clear = new JMenuItem("Clear All");
        clear.addActionListener(e -> {
            oc.setMessageTypes(EnumSet.noneOf(ChatMessageType.class));
            saveOverlay();
            rebuild();
        });
        menu.add(all);
        menu.add(clear);
        menu.addSeparator();

        for (MessageCategory cat : MessageCategory.values()) {
            Set<ChatMessageType> selectedNow = oc.getMessageTypes();
            boolean allSelected = !cat.getTypes().isEmpty() && selectedNow.containsAll(cat.getTypes());
            boolean anySelected = false;
            for (ChatMessageType type : cat.getTypes()) {
                if (selectedNow.contains(type)) { anySelected = true; break; }
            }

            String prefix = allSelected ? "✓ " : (anySelected ? "• " : "");
            JMenu sub = new JMenu(prefix + cat.toString());
            JCheckBoxMenuItem wholeCategory = new JCheckBoxMenuItem("Entire " + cat.toString(), allSelected);
            wholeCategory.setToolTipText("Select or clear every message type in this category.");
            wholeCategory.addActionListener(e -> {
                Set<ChatMessageType> current = oc.getMessageTypes();
                EnumSet<ChatMessageType> copy = current.isEmpty()
                        ? EnumSet.noneOf(ChatMessageType.class) : EnumSet.copyOf(current);
                if (wholeCategory.isSelected()) copy.addAll(cat.getTypes());
                else copy.removeAll(cat.getTypes());
                oc.setMessageTypes(copy);
                saveOverlay();
                rebuild();
            });
            sub.add(wholeCategory);
            sub.addSeparator();

            for (ChatMessageType type : cat.getTypes()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(pretty(type.name()), oc.getMessageTypes().contains(type));
                item.setToolTipText("Include " + pretty(type.name()) + " messages in this overlay.");
                item.addActionListener(e -> {
                    Set<ChatMessageType> current = oc.getMessageTypes();
                    EnumSet<ChatMessageType> copy = current.isEmpty()
                            ? EnumSet.noneOf(ChatMessageType.class) : EnumSet.copyOf(current);
                    if (item.isSelected()) copy.add(type); else copy.remove(type);
                    oc.setMessageTypes(copy);
                    saveOverlay();
                    rebuild();
                });
                sub.add(item);
            }
            menu.add(sub);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void showCreateOverlayMenu(JButton anchor) {
        JPopupMenu menu = new JPopupMenu();
        addTemplate(menu, "Game Alerts", "game");
        addTemplate(menu, "Private Messages", "private");
        addTemplate(menu, "Clan Chat", "clan");
        addTemplate(menu, "All Messages", "all");
        menu.addSeparator();
        addTemplate(menu, "Custom Overlay", "custom");
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void addTemplate(JPopupMenu menu, String name, String template) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> createOverlay(name, template));
        menu.add(item);
    }

    private void createOverlay(String name, String template) {
        OverlayConfig oc = OverlayConfig.preset(name, template);
        oc.getTextSettings();
        plugin.addConfiguredOverlay(oc);
        sidebarTab = 0;
        openEditor(oc.getId());
    }

    private JButton colorButton(Color color, String text) {
        Color c = color == null ? Color.WHITE : color;
        JButton b = new JButton(text);
        b.setBackground(c);
        b.setForeground(contrast(c));
        b.setOpaque(true);
        b.setFocusPainted(true);
        b.setMargin(new Insets(2, 5, 2, 5));
        b.setToolTipText(tooltipFor(text));
        b.setBorder(BorderFactory.createLineBorder(BORDER));
        b.setPreferredSize(new Dimension(54, 23));
        b.setMaximumSize(new Dimension(54, 23));
        return b;
    }

    private JLabel pageTitle(String title) {
        JLabel l = label("", TEXT, 11, Font.BOLD);
        PlainText.set(l, title);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String tooltipFor(String name) {
        if (name.endsWith(" mode") && !name.equals("Filter mode") && !name.equals("Custom filter mode"))
            return "Static uses one color; Duo Cycle blends two colors together; Duo Wave moves them across the surface. RGB modes use the full spectrum.";
        if (name.startsWith("Pulse ")) return "Fade this part of the overlay out and back in repeatedly, independently of message alerts.";
        if (name.endsWith(" pulse (sec)")) return "Seconds for one complete fade-out and fade-in.";
        if (name.endsWith(" cycle (sec)")) return "Seconds for one complete color cycle or wave.";
        switch (name) {
            case "Text color source": return "Keep original message colors, use channel palette colors, or choose one custom color.";
            case "Custom timestamp color": return "Give timestamps their own static color. Animated timestamp modes always use their own settings.";
            case "TIMESTAMP COLORS": return "Timestamp color and pulse, independent of message text.";
            case "BORDER": return "Frame color, animation, pulse and thickness.";
            case "BACKGROUND": return "Background color and opacity.";
            case "Name": return "Name this overlay in the editor and overlay menu.";
            case "Enabled": return "Show this overlay when it has matching messages.";
            case "Visible lines": return "Number of message lines visible at once. Scroll to read the rest of the history.";
            case "Max Messages": return "Maximum recent messages shown when scroll history is disabled.";
            case "Scroll history": return "Read session history with the mouse wheel. Messages stay visible without fading. Right-click Latest to return to new messages.";
            case "History messages": return "Maximum matching messages available from the shared session history (up to 5,000 captured messages). Nothing is saved to disk.";
            case "Text Font": return "Font used for every message in this overlay.";
            case "Text Alignment": return "Align message lines inside the overlay width.";
            case "Bold": return "Use the bold version of the selected font.";
            case "Timestamps": return "Prefix messages with a clock time or their age. Choose the style below.";
            case "Timestamp style": return "Clock time uses the configured time format. Time ago updates as messages age: 01s, 30s, 20m, 02h, 99h; hidden at 100 hours.";
            case "Width (px)": return "Width of this overlay in game pixels; long lines wrap.";
            case "Padding X": return "Space inside the left/right border. Borders and rounded corners keep at least 3 pixels clear.";
            case "Padding Y": return "Space inside the top/bottom border. Borders and rounded corners keep at least 3 pixels clear.";
            case "Background": return "Draw the overlay background. In Alerts, flash it for matching messages.";
            case "Background Color": return "Background tint. Opacity controls its transparency.";
            case "Border": return "Draw the frame. In Alerts, flash it for matching messages.";
            case "Border Thickness": return "Frame line width in pixels.";
            case "Rounded corners": return "Round the background and frame, including RGB and flashing borders.";
            case "RGB spread": return "How static rainbow colors are distributed across the message.";
            case "RGB highlight rules": return "Apply RGB only to matching message bodies. For every message, use Text mode.";
            case "Highlight effect": return "RGB effect used by matching phrases; overlay default follows Text mode.";
            case "Solid color rules": return "Apply palette colors to matching message bodies.";
            case "Include player chat": return "Also apply solid-color rules to player messages.";
            case "Mode": return "Where this overlay is anchored. Free placement uses RuneLite overlay dragging.";
            case "X Offset": return "Horizontal distance from the player anchor.";
            case "Y Offset": return "Vertical distance from the player anchor.";
            case "Show Clan Rank Icons": return "Display known clan ranks beside message senders.";
            case "Show Offline Status": return "Mark clan members who are currently offline.";
            case "Show Offline Icon": return "Add an offline marker beside the sender.";
            case "Color Offline Names": return "Use a separate name color for offline clan members.";
            case "Offline Color": return "Name color for offline clan members.";
            case "Flash": return "Pulse selected parts of the overlay for new matching messages.";
            case "Trigger": return "Which messages start the overlay flash.";
            case "Flash While Focused": return "Allow flashing even while RuneLite has focus.";
            case "Stop": return "When an active flash should end.";
            case "Duration (sec)": return "How long flashing can continue after a trigger.";
            case "Speed": return "Speed of the flash pulse.";
            case "Message": return "Pulse the message text when a flash is triggered.";
            case "Dynamic Height": return "Shrink the overlay as messages fade instead of reserving empty rows.";
            case "Hide Duplicate Count": return "Hide the repeated-message count suffix.";
            case "Input Preview": return "Display the text you are typing. Long lines scroll behind your name to keep the newest text visible.";
            case "Use this replacement": return "Enable this display-only replacement rule.";
            case "Test message text": return "Sample text to test your replacement rules without sending chat.";
            case "Customize Notification": return "Override RuneLite notification preferences for this overlay only.";
            case "Notify While Focused": return "Send notifications even when RuneLite has focus.";
            case "Volume (%)": return "Notification sound volume; 0 is silent.";
            case "Request Focus": return "RuneLite notification focus preference.";
            case "Window Flash": return "RuneLite notification flashing preference.";
            case "Flash Color": return "Color used for the notification flash.";
            case "Timeout (ms)": return "Notification lifetime in milliseconds; 0 uses RuneLite behavior.";
            case "Chatbox Notification": return "Also add the notification to the native chatbox.";
            case "Use RuneLite Chat Filter": return "Use the enabled RuneLite Chat Filter for this overlay.";
            case "Filter mode": case "Custom filter mode": return "Show or hide messages matching the custom rules.";
            case "Settings": return "Open this plugin's RuneLite settings.";
            case "+ New": return "Create another overlay with its own messages, appearance and alerts.";
            case "Preview": return "Show sample messages in this overlay without sending chat.";
            case "Duplicate": return "Copy this overlay and its saved settings.";
            case "Delete": return "Remove this overlay and its saved settings.";
            case "Notify For": return "Choose which incoming messages trigger a notification.";
            case "Desktop Notification": return "Show the notification using RuneLite's desktop notification support.";
            case "Sound": return "Select the RuneLite notification sound for this overlay.";
            case "Test player name": return "Sample sender for testing the custom filter; nothing is sent.";
            case "Test message": return "Sample message body for testing the custom filter; nothing is sent.";
            case "TEST CUSTOM RULES": return "Check whether the sample sender and message pass this overlay's custom filter.";
            case "REMOVE REPLACEMENT": return "Delete this display-only replacement rule.";
            case "+ ADD REPLACEMENT": return "Add another original-message and replacement pair.";
            case "TEST REPLACEMENTS": return "Show how the sample message would look with these replacement rules.";
            case "RULE PALETTE": return "Expand the nine colors referenced by solid-color rules.";
            case "Specific Message Highlighting": return "Choose solid or animated colors for matching messages.";
            case "TEXT": return "Font, size, alignment and timestamps.";
            case "TEXT COLORS": return "Message colors, two-color and RGB modes, and an optional pulse.";
            case "FRAME": return "Width, spacing and rounded corners.";
            case "POSITION": return "Choose an anchor and placement offsets.";
            case "PALETTE COLORS": return "Fallback colors for each message channel.";
            case "MESSAGES & VISIBILITY": return "Select which messages appear and when the overlay is visible.";
            case "MESSAGE FILTERING": return "Filter this overlay by message content or player name.";
            case "CLAN DISPLAY": return "Clan rank and offline sender presentation.";
            case "LAYOUT & INPUT": return "Height, repeated-message counts and typing preview.";
            case "OVERLAY FLASH": return "Choose which new messages flash and which parts of the overlay pulse.";
            case "Show channel names": return "Show the [channel] prefix for clan and friends chat in this overlay.";
            case "Show overlay": return "Empty overlays stay hidden. Preview shows sample messages.";
            case "Fade (sec)": return "Seconds before messages fade; 0 keeps messages visible.";
            case "Message Types": return "Choose the channels and message types to display.";
            case "Color palette": return "Automatic follows RuneLite's current chatbox mode. Explicit palettes stay fixed. Used with the Message colors source.";
            case "Text Size": return "Scale the selected font and message icons.";
            case "Opacity (%)": return "100% is solid. Turn Background off for full transparency.";
            case "Enable Notifications": return "Use RuneLite notification preferences unless Customize Notification is enabled.";
            case "Sound Filename": return "WAV filename in RuneLite's notifications folder. Blank uses the default.";
            case "Allow rule overrides": return "Matching highlight rules take priority over text overrides and effects.";
            case "Color rules": return "One phrase::1 through phrase::9 per line; optional ::flash. Supports regex:.";
            case "RGB patterns": return "One phrase per line, or regex:pattern. Invalid expressions are ignored.";
            case "Enable message replacer (Beta)": return "Replace displayed overlay text only. Original chat, filters and alerts stay unchanged.";
            case "Original message": return "First matching enabled rule wins; ignores case and chat colors.";
            case "Show instead": return "Fill both fields to activate this replacement.";
            case "Words or phrases": return "Separate phrases with commas. Matching ignores case.";
            case "Regular expressions": return "One expression per line, for example ^You have .* coins$.";
            case "Player names": return "One name fragment per line. Use regex:^Alice$ for an exact match.";
            default:
                if (name.startsWith("Rule color ")) return "Palette color referenced by this number in solid-color rules.";
                if (java.util.Arrays.asList("Game","Public","Private","Friends chat","Clan","Guest clan","GIM","Trade","Challenge","Did you know","Broadcast","Autochat").contains(name)) return "Fallback color for this channel when not using a RuneLite palette or text effect.";
                return "Configure " + name.toLowerCase(java.util.Locale.ROOT) + " for this overlay. Changes save automatically.";
        }
    }

    private WidthTrackingPanel verticalPage() {
        WidthTrackingPanel p = new WidthTrackingPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(7, 7, 10, 7));
        return p;
    }

    private JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(null);
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    private JPanel panel(Color color) {
        JPanel p = new JPanel();
        p.setBackground(color);
        p.setOpaque(true);
        return p;
    }

    private JLabel label(String text, Color color, int size, int style) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        Font base = UIManager.getFont("Label.font");
        l.setFont(base == null ? new Font(Font.SANS_SERIF, style, Math.max(12, size)) : base.deriveFont(style, Math.max(base.getSize2D(), size)));
        return l;
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltipFor(text));
        b.setFocusPainted(true);
        b.setMargin(new Insets(3, 6, 3, 6));
        b.setBackground(CARD_ALT);
        b.setForeground(TEXT);
        b.setFont(UIManager.getFont("Label.font"));
        return b;
    }

    private void styleField(JTextField field) {
        field.setBackground(CONTROL);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        field.setPreferredSize(new Dimension(102, 24));
        field.setMaximumSize(new Dimension(102, 24));
    }

    private void alignStackChildren(JPanel p) {
        for (Component c : p.getComponents()) {
            if (c instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
    }

    private void saveOverlay() {
        if (closed) return;
        plugin.onOverlayConfigChanged();
        OverlayConfig current = findOverlay(editingOverlayId);
        if (current != null) editorWindow.title("Edit overlay — " + current.getName());
        for (OverlayConfig oc : plugin.getOverlayConfigs()) {
            JButton selector = overlaySelectors.get(oc.getId());
            if (selector != null) {
                selector.setText(oc.getName() + "  ›");
                selector.setToolTipText("Edit " + oc.getName());
            }
        }
    }

    private OverlayConfig findOverlay(String id) {
        for (OverlayConfig oc : plugin.getOverlayConfigs()) {
            if (oc.getId().equals(id)) return oc;
        }
        return null;
    }

    private String messageSummary(OverlayConfig oc) {
        int count = oc.getMessageTypes().size();
        if (count == 0) return "No types";
        if (count == 1) return "1 type";
        return count + " types";
    }

    private String fadeLabel(OverlayConfig oc) {
        return oc.getFadeOutDuration() == 0 ? "No fade" : oc.getFadeOutDuration() + "s fade";
    }

    private static Color contrast(Color c) {
        int lum = c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114;
        return lum > 150000 ? Color.BLACK : Color.WHITE;
    }

    private static String pretty(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (upper && Character.isLetter(c)) {
                b.append(Character.toUpperCase(c));
                upper = false;
            } else {
                b.append(c);
            }
            if (c == ' ') upper = true;
        }
        return b.toString();
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override public Component add(Component comp) {
            if (comp instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) comp).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            return super.add(comp);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(32, visibleRect.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
