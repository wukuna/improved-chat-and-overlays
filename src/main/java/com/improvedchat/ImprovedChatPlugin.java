package com.improvedchat;

import com.improvedchat.chatbox.clean.CleanChatModule;
import com.improvedchat.chatbox.collapse.ChatCollapseModule;
import com.improvedchat.chatbox.menu.RemoveChatOptionsModule;
import com.improvedchat.chatbox.offline.ClanChatPresentationService;
import com.improvedchat.chatbox.offline.OfflineChatStatusModule;
import com.improvedchat.chatbox.opacity.ChatboxOpacityModule;
import com.improvedchat.chatbox.resize.ChatResizeModule;
import com.improvedchat.compat.ModernChatCompatibility;
import com.improvedchat.compat.ModernChatOwnership;
import com.improvedchat.dialogue.DialogueFontsModule;
import com.improvedchat.release.ReleaseNoticeModule;
import com.improvedchat.model.MessageCategory;
import com.improvedchat.model.MessageMergeRule;
import com.improvedchat.model.MessageNodeIdentity;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.overlay.AttentionEngine;
import com.improvedchat.overlay.DynamicChatOverlay;
import com.improvedchat.overlay.OverlayConfig;
import com.improvedchat.overlay.OverlayNotifications;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.config.RuneLiteConfig;
import com.improvedchat.overlay.OverlayMessageFilter;
import com.google.gson.Gson;
import com.improvedchat.overlay.OverlayHistory;
import com.improvedchat.overlay.OverlayConfigStore;
import javax.swing.SwingUtilities;
import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.Point;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.chatfilter.ChatFilterConfig;
import net.runelite.client.plugins.chatfilter.ChatFilterPlugin;
import net.runelite.client.plugins.emojis.EmojiPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;

@PluginDescriptor(
        name = "Improved Chat and Overlays",
        configName = "improvedchat",
        internalName = "improved-chat",
        description = "Create filtered chat overlays with custom styles, RGB effects and alerts. Resize or collapse the chatbox, tidy messages and improve dialogue readability.",
        tags = {"chat", "message", "overlay", "color", "customize", "private", "clan"},
        conflicts = {"Chat Widgets", "Force Recolor"})
public class ImprovedChatPlugin extends Plugin implements net.runelite.client.input.MouseWheelListener {

    public static final boolean DEBUG = false;

    private static final String CONFIG_GROUP = "improvedchat";
    private static final String OVERLAY_CONFIGS_KEY = "overlayConfigs";
    private static final int MAX_POOL_SIZE = 5000;

    private static final Pattern BOSS_KC_PATTERN = Pattern.compile("Your .+ count is:");
    private static final MessageMergeRule[] MESSAGE_MERGE_RULES = {
            new MessageMergeRule("You eat", "It heals some health.", true),
            new MessageMergeRule("You drink", Pattern.compile("You have [0-9].*")),
            new MessageMergeRule("You drink", "You have finished your potion.", true),
            new MessageMergeRule("You are now a guest of", "To talk, start each", false),

    };

    private static final Set<ChatMessageType> SENDER_TYPES = EnumSet.of(
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT,
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.MODCHAT,
            ChatMessageType.AUTOTYPER,
            ChatMessageType.MODAUTOTYPER,
            ChatMessageType.FRIENDSCHAT,
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT
    );

    private static final Set<ChatMessageType> EMOJI_WATCHED_TYPES = EnumSet.of(
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.MODCHAT,
            ChatMessageType.FRIENDSCHAT,
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT,
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT
    );

    private static final Set<ChatMessageType> ALL_SUPPORTED_TYPES = EnumSet.of(

            ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM, ChatMessageType.CONSOLE,
            ChatMessageType.WELCOME, ChatMessageType.BROADCAST, ChatMessageType.DIDYOUKNOW,
            ChatMessageType.ENGINE, ChatMessageType.UNKNOWN, ChatMessageType.LEVELUPMESSAGE,
            ChatMessageType.NPC_SAY, ChatMessageType.MESBOX,
            ChatMessageType.PLAYERRELATED, ChatMessageType.TENSECTIMEOUT, ChatMessageType.SNAPSHOTFEEDBACK,
            ChatMessageType.FRIENDNOTIFICATION, ChatMessageType.FRIENDSCHATNOTIFICATION,
            ChatMessageType.IGNORENOTIFICATION,
            ChatMessageType.ITEM_EXAMINE, ChatMessageType.NPC_EXAMINE, ChatMessageType.OBJECT_EXAMINE,
            ChatMessageType.CLAN_MESSAGE, ChatMessageType.CLAN_GUEST_MESSAGE,
            ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.CLAN_CREATION_INVITATION,

            ChatMessageType.TRADE, ChatMessageType.TRADE_SENT, ChatMessageType.TRADEREQ,
            ChatMessageType.CHALREQ_TRADE, ChatMessageType.CHALREQ_FRIENDSCHAT,

            ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
            ChatMessageType.AUTOTYPER, ChatMessageType.MODAUTOTYPER,

            ChatMessageType.FRIENDSCHAT,

            ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.CLAN_GIM_CHAT,
            ChatMessageType.CHALREQ_CLANCHAT,

            ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT, ChatMessageType.MODPRIVATECHAT,
            ChatMessageType.LOGINLOGOUTNOTIFICATION
    );

    @Inject
    private Client client;
    @Inject private Notifier notifier;
    @Inject private RuneLiteConfig runeLiteConfig;
    @Inject private java.util.concurrent.ScheduledExecutorService executor;
    private final com.improvedchat.overlay.OverlayBackgroundImages backgroundImages = new com.improvedchat.overlay.OverlayBackgroundImages();
    private final Map<OverlayConfig, Object> backgroundRequests = new ConcurrentHashMap<>();
    private final Map<OverlayConfig, String> backgroundErrors = new ConcurrentHashMap<>();

    @Inject
    private ImprovedChatConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChatColorConfig chatColorConfig;

    @Inject
    private Gson gson;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ModernChatCompatibility modernChatCompatibility;

    @Inject
    private CleanChatModule cleanChatModule;

    @Inject
    private ChatResizeModule chatResizeModule;

    @Inject
    private ChatCollapseModule chatCollapseModule;

    @Inject
    private ChatboxOpacityModule chatboxOpacityModule;

    @Inject
    private RemoveChatOptionsModule removeChatOptionsModule;

    @Inject
    private OfflineChatStatusModule offlineChatStatusModule;

    @Inject
    private ClanChatPresentationService clanChatPresentationService;

    @Inject
    private DialogueFontsModule dialogueFontsModule;

    @Inject
    private ReleaseNoticeModule releaseNoticeModule;

    @Inject
    private com.improvedchat.overlay.NativeChatBorderOverlay nativeChatBorderOverlay;
    @Inject
    private com.improvedchat.overlay.NativeChatBackgroundOverlay nativeChatBackgroundOverlay;

    @Inject private net.runelite.client.input.MouseManager mouseManager;

    private final com.improvedchat.overlay.OverlayMessageStore messages = new com.improvedchat.overlay.OverlayMessageStore();
    private final Map<String, com.improvedchat.overlay.OverlaySelectionCache> selectionCaches = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong renderRevision = new java.util.concurrent.atomic.AtomicLong();
    public long getRenderRevision() { return renderRevision.get(); }

    private final ChatMessageFilter chatMessageFilter = new ChatMessageFilter();
    private Plugin chatFilterPlugin;
    private Plugin emojiPlugin;

    private final List<OverlayConfig> overlayConfigs = new CopyOnWriteArrayList<>();
    private final Map<String, Long> clearedThrough = new ConcurrentHashMap<>();
    private String configRecoveryMessage;
    private boolean overlayPersistenceBlocked;
    private boolean ruleWarningShown;

    @Inject
    private net.runelite.client.eventbus.EventBus eventBus;
    private final List<DynamicChatOverlay> overlays = new CopyOnWriteArrayList<>();
    private volatile boolean overlayWheelReady;
    private final Map<String, OverlayMessageFilter> overlayMessageFilters = new ConcurrentHashMap<>();

    private NavigationButton navButton;
    private ImprovedChatPanel panel;
    private boolean pmWidgetsHidden = false;

    private final List<PendingMessageUpdate> pendingUpdates = new ArrayList<>();

    private static final int COMMAND_UPDATE_TICKS = 10;
    private static final Function<MessageNode, String> COMMAND_VALUE = MessageNode::getRuneLiteFormatMessage;

    private static final int EMOJI_UPDATE_TICKS = 1;
    private static final Function<MessageNode, String> EMOJI_VALUE = node -> {
        String value = node.getValue();
        return value == null ? null : value.trim();
    };

    static class PendingMessageUpdate {
        final OverlayMessage widgetMessage;
        final MessageNode messageNode;
        final MessageNodeIdentity messageIdentity;
        final Function<MessageNode, String> valueAccessor;
        final String originalText;
        int ticksRemaining;

        PendingMessageUpdate(OverlayMessage widgetMessage, MessageNode messageNode,
                Function<MessageNode, String> valueAccessor, String originalText, int ticksRemaining) {
            this.widgetMessage = widgetMessage;
            this.messageNode = messageNode;
            this.messageIdentity = MessageNodeIdentity.capture(messageNode);
            this.valueAccessor = valueAccessor;
            this.originalText = originalText;
            this.ticksRemaining = ticksRemaining;
        }

        boolean stillRepresentsOriginalMessage() {
            return messageIdentity != null && messageIdentity.matches(messageNode);
        }
    }

    @Inject private net.runelite.client.input.KeyManager keyManager;
    @Inject private com.improvedchat.chatbox.ChatboxVisibility chatboxVisibility;
    private final VisibilityHotkeys visibilityHotkeys = new VisibilityHotkeys(this::queueVisibilityToggle);

    private void refreshVisibilityHotkeys() {
        if (nativeChatActive) visibilityHotkeys.configure(overlayConfigs, config.hideChatboxHotkey());
    }

    private void queueVisibilityToggle(VisibilityHotkeys.Activation activation) {
        clientThread.invokeLater(() -> {
            if (!nativeChatActive || !visibilityHotkeys.isCurrent(activation)
                || client.getGameState() != GameState.LOGGED_IN) return;
            if (activation.hideChatbox && !modernChatCompatibility.snapshot().blocksVisibility())
                chatboxVisibility.toggle();
            boolean changed = false;
            for (OverlayConfig overlay : overlayConfigs) {
                if (activation.overlayIds.contains(overlay.getId())) {
                    overlay.setShow(!overlay.isShow());
                    changed = true;
                }
            }
            if (changed) {
                onOverlayConfigChanged();
                refreshPanel();
            }
        });
    }

    @Subscribe public void onProfileChanged(net.runelite.client.events.ProfileChanged event) {
        visibilityHotkeys.reset();
        backgroundImages.clear();
        backgroundRequests.clear();
        backgroundErrors.clear();
        long generation = ++nativeChatGeneration;
        clientThread.invokeLater(() -> {
            if (!nativeChatActive || generation != nativeChatGeneration) return;
            chatboxVisibility.reset();
            for (DynamicChatOverlay overlay : overlays) overlayManager.remove(overlay);
            overlays.clear();
            overlayMessageFilters.clear();
        selectionCaches.clear();
            clearedThrough.clear();
            loadOverlayConfigs();
            for (OverlayConfig overlay : overlayConfigs) addOverlay(overlay);
            refreshVisibilityHotkeys();
            refreshPanel();
        });
    }

    @Inject private com.improvedchat.input.ChatInputDock inputDock;
    @Inject private com.improvedchat.input.ChatInputDockOverlay inputDockOverlay;

    public com.improvedchat.input.ChatInputDock getInputDock() { return inputDock; }
    public ImprovedChatConfig getInputDockConfig() { return config; }
    public void setInputDockSetting(String key, Object value) {
        configManager.setConfiguration(CONFIG_GROUP, key, value);
    }

    private volatile boolean nativeChatActive;
    private volatile long nativeChatGeneration;

    @Override
    protected void startUp() {
        nativeChatGeneration++;
        nativeChatActive = true;
        loadOverlayConfigs();
        MessageColorRuleEngine.configure(config);
        OverlayColorRuleEngine.configure(config);
        rebuildChatFilter();
        clanChatPresentationService.startUp();
        overlayManager.add(nativeChatBorderOverlay);
        overlayManager.add(nativeChatBackgroundOverlay);
        nativeChatBackgroundOverlay.startUp();
        inputDock.startUp();
        eventBus.register(inputDock);
        mouseManager.registerMouseListener(inputDock);
        overlayManager.add(inputDockOverlay);

        for (OverlayConfig oc : overlayConfigs) {
            addOverlay(oc);
        }

        refreshVisibilityHotkeys();
        keyManager.registerKeyListener(visibilityHotkeys);
        eventBus.register(chatboxVisibility);
        mouseManager.registerMouseWheelListener(this);
        updatePmWidgetVisibility();

        reconcileNativeChatOwnership(modernChatCompatibility.startupSnapshot());
        removeChatOptionsModule.startUp();
        dialogueFontsModule.startUp();
        releaseNoticeModule.startUp();
        scheduleNativeChatOwnershipReconcile();

        panel = new ImprovedChatPanel(this);
        BufferedImage icon;
        try {
            icon = ImageUtil.loadImageResource(getClass(), "/panelicon.png");
        } catch (Exception e) {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
        navButton = NavigationButton.builder()
                .tooltip("Improved Chat and Overlays")
                .icon(icon)
                .priority(10)
                .panel(panel)
                .build();
        if (!config.hideSidePanel()) {
            clientToolbar.addNavigation(navButton);
        }
    }

    @Override
    protected void shutDown() {
        overlayWheelReady = false;
        visibilityHotkeys.reset();
        keyManager.unregisterKeyListener(visibilityHotkeys);
        eventBus.unregister(chatboxVisibility);
        clientThread.invokeLater(chatboxVisibility::reset);
        mouseManager.unregisterMouseWheelListener(this);
        overlayManager.remove(nativeChatBorderOverlay);
        overlayManager.remove(nativeChatBackgroundOverlay);
        nativeChatBackgroundOverlay.shutDown();
        inputDock.shutDown();
        eventBus.unregister(inputDock);
        mouseManager.unregisterMouseListener(inputDock);
        overlayManager.remove(inputDockOverlay);
        nativeChatActive = false;
        nativeChatGeneration++;
        backgroundImages.clear();
        backgroundRequests.clear();
        backgroundErrors.clear();
        releaseNoticeModule.shutDown();
        dialogueFontsModule.shutDown();
        offlineChatStatusModule.shutDown();
        removeChatOptionsModule.shutDown();
        cleanChatModule.shutDown();
        chatboxOpacityModule.shutDown();
        chatCollapseModule.shutDown();
        chatResizeModule.shutDown();

        for (DynamicChatOverlay overlay : overlays) {
            overlayManager.remove(overlay);
        }
        overlays.clear();
        overlayMessageFilters.clear();
        selectionCaches.clear();
        clearedThrough.clear();
        pendingUpdates.clear();
        messages.clear();
        clanChatPresentationService.shutDown();
        MessageColorRuleEngine.configure(null);
        OverlayColorRuleEngine.configure(null);
        AttentionEngine.setClientFocused(true);

        if (pmWidgetsHidden) {
            setPmWidgetsHidden(false);
        }

        if (panel != null) {
            panel.close();
            panel = null;
        }
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
    }

    public List<OverlayConfig> getOverlayConfigs() {
        return overlayConfigs;
    }

    private net.runelite.client.util.Filepath backgroundDirectory() throws java.io.IOException {
        return getPluginDirectory().joinSegment("backgrounds");
    }

    public BufferedImage getOverlayBackground(OverlayConfig overlay) {
        return backgroundImages.get(overlay.getBackgroundImage(), this::backgroundDirectory, executor, this::refreshPanel);
    }

    public boolean isBackgroundImageLoading() { return backgroundImages.isImporting(); }

    public String getBackgroundImageStatus(OverlayConfig overlay) {
        String error = backgroundErrors.get(overlay);
        return error != null ? error : backgroundImages.status(overlay.getBackgroundImage());
    }

    public void chooseOverlayBackground(java.awt.Component owner, OverlayConfig overlay) {
        if (!nativeChatActive || backgroundImages.isImporting() || !overlayConfigs.contains(overlay)) return;
        long generation = nativeChatGeneration;
        List<net.runelite.client.util.Filepath> files = new net.runelite.client.util.Filepath.Chooser()
                .setIsOpen().setAcceptsFiles().setDialogTitle("Choose overlay background")
                .addExtensionFilter("PNG, JPEG or GIF images", "png", "jpg", "jpeg", "gif").showDialog(owner);
        if (files == null || files.isEmpty() || !nativeChatActive || generation != nativeChatGeneration
                || !overlayConfigs.contains(overlay)) return;
        Object request = new Object();
        backgroundRequests.put(overlay, request);
        backgroundErrors.remove(overlay);
        net.runelite.client.util.Filepath selected = files.get(0);
        backgroundImages.importImage(selected, this::backgroundDirectory, executor, result -> {
            if (!backgroundRequests.remove(overlay, request) || !nativeChatActive || generation != nativeChatGeneration
                    || !overlayConfigs.contains(overlay)) {
                backgroundImages.retain(overlayConfigs.stream().map(OverlayConfig::getBackgroundImage).collect(java.util.stream.Collectors.toSet()));
                refreshPanel();
                return;
            }
            if (result.error != null) backgroundErrors.put(overlay, result.error);
            else {
                overlay.setBackgroundImage(result.name, selected.getFileName());
                overlay.setBackgroundEnabled(true);
                onOverlayConfigChanged();
            }
            refreshPanel();
        });
        refreshPanel();
    }

    public void removeOverlayBackground(OverlayConfig overlay) {
        backgroundRequests.remove(overlay);
        backgroundErrors.remove(overlay);
        overlay.setBackgroundImage(null, null);
        onOverlayConfigChanged();
        refreshPanel();
    }

    public void addNewOverlay() {
        addConfiguredOverlay(new OverlayConfig());
    }

    public void addConfiguredOverlay(OverlayConfig oc) {
        if (overlayConfigs.size() >= OverlayConfigStore.MAX_OVERLAYS) {
            configRecoveryMessage = "Use at most 64 overlays. Existing overlays were kept.";
            refreshPanel();
            return;
        }
        oc.getTextSettings();
        overlayConfigs.add(oc);
        addOverlay(oc);
        saveOverlayConfigs();
        if (panel != null) {
            panel.rebuild();
        }
    }

    public void removeOverlay(OverlayConfig oc) {
        overlayConfigs.remove(oc);
        backgroundRequests.remove(oc);
        backgroundErrors.remove(oc);
        backgroundImages.retain(overlayConfigs.stream().map(OverlayConfig::getBackgroundImage).collect(java.util.stream.Collectors.toSet()));
        overlayMessageFilters.remove(oc.getId());
        selectionCaches.remove(oc.getId() + ":history");
        selectionCaches.remove(oc.getId() + ":visible");
        clearedThrough.remove(oc.getId());
        DynamicChatOverlay toRemove = null;
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.getOverlayConfig() == oc) {
                toRemove = overlay;
                break;
            }
        }
        if (toRemove != null) {
            overlayManager.remove(toRemove);
            overlays.remove(toRemove);
        }
        saveOverlayConfigs();
        updatePmWidgetVisibility();
        if (panel != null) {
            panel.rebuild();
        }
    }

    public void resetOverlays() {
        backgroundImages.clear();
        backgroundRequests.clear();
        backgroundErrors.clear();
        overlayPersistenceBlocked = false;
        configRecoveryMessage = null;
        for (DynamicChatOverlay overlay : overlays) {
            overlayManager.remove(overlay);
        }
        overlays.clear();
        overlayConfigs.clear();
        overlayMessageFilters.clear();
        selectionCaches.clear();
        clearedThrough.clear();

        overlayConfigs.add(OverlayConfig.defaultPrivateOverlay());
        overlayConfigs.add(OverlayConfig.defaultAllOverlay());
        for (OverlayConfig oc : overlayConfigs) {
            oc.getTextSettings();
            addOverlay(oc);
        }

        saveOverlayConfigs();
        updatePmWidgetVisibility();
        if (panel != null) {
            panel.rebuild();
        }
    }

    public void onOverlayConfigChanged() {
        renderRevision.incrementAndGet();
        backgroundImages.retain(overlayConfigs.stream().map(OverlayConfig::getBackgroundImage).collect(java.util.stream.Collectors.toSet()));
        saveOverlayConfigs();
        updatePmWidgetVisibility();
        for (DynamicChatOverlay overlay : overlays) {
            overlay.setOverlayConfig(overlay.getOverlayConfig());
        }
    }

    public void moveOverlay(OverlayConfig oc, int direction) {
        int index = overlayConfigs.indexOf(oc);
        int newIndex = index + direction;
        if (newIndex < 0 || newIndex >= overlayConfigs.size()) {
            return;
        }
        Collections.swap(overlayConfigs, index, newIndex);
        Collections.swap(overlays, index, newIndex);
        refreshOverlayPriorities();
        saveOverlayConfigs();
        if (panel != null) {
            panel.rebuild();
        }
    }

    private void refreshOverlayPriorities() {
        for (int i = 0; i < overlays.size(); i++) {
            DynamicChatOverlay overlay = overlays.get(i);
            overlayManager.remove(overlay);
            overlay.setPriority(10f + i);
        }
        for (DynamicChatOverlay overlay : overlays) {
            overlayManager.add(overlay);
        }
    }

    private void addOverlay(OverlayConfig oc) {
        DynamicChatOverlay overlay = new DynamicChatOverlay(this, config, client,
                chatColorConfig, clanChatPresentationService, oc);
        overlays.add(overlay);
        overlayManager.add(overlay);
        refreshOverlayPriorities();
    }

    private void loadOverlayConfigs() {
        backgroundImages.clear();
        overlayConfigs.clear();
        configRecoveryMessage = null;
        overlayPersistenceBlocked = false;
        String json = configManager.getConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY);
        try {
            overlayConfigs.addAll(OverlayConfigStore.read(gson, json));
        } catch (RuntimeException ex) {

            overlayConfigs.addAll(OverlayConfigStore.defaults());
            if (json != null && json.length() > OverlayConfigStore.MAX_JSON) {
                overlayPersistenceBlocked = true;
                configRecoveryMessage = "Saved overlays exceed the storage safety limit. The original remains unchanged; automatic saving is paused. Export your RuneLite profile for recovery before explicitly resetting overlays.";
            } else {
                configManager.setConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY + "Backup", json);
                configRecoveryMessage = "Saved overlays could not be read. Defaults are shown; the original configuration was kept in an overlayConfigsBackup setting for recovery.";
            }
        }
        boolean migrated = false;
        for (OverlayConfig overlay : overlayConfigs) migrated |= overlay.migrateTextSettings(config);
        if (migrated && configRecoveryMessage == null && json != null && !json.trim().isEmpty()
                && configManager.getConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY + "BeforeMenuCleanup") == null) {
            configManager.setConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY + "BeforeMenuCleanup", json);
        }
        if (configRecoveryMessage == null && (migrated || json == null || json.trim().isEmpty())) saveOverlayConfigs();
    }

    public OverlayConfig duplicateOverlay(OverlayConfig original) {
        OverlayConfig copy = gson.fromJson(gson.toJson(original), OverlayConfig.class);
        copy.renewId();
        copy.setName(original.getName() + " (copy)");
        copy.setWidgetWidth(getOverlayWidth(original));
        addConfiguredOverlay(copy);
        return copy;
    }

    public boolean isGlobalFilterOverride() { return config.useChatFilter(); }

    public String getConfigRecoveryMessage() {
        String warning = BoundedPattern.warning();
        return configRecoveryMessage == null ? warning : configRecoveryMessage + (warning == null ? "" : " " + warning);
    }

    public void openSettings() {
        net.runelite.client.ui.overlay.Overlay settingsTarget = new net.runelite.client.ui.overlay.Overlay(this) {
            @Override public java.awt.Dimension render(java.awt.Graphics2D graphics) { return null; }
        };
        eventBus.post(new OverlayMenuClicked(
                new net.runelite.client.ui.overlay.OverlayMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG,
                        OverlayManager.OPTION_CONFIGURE, "Improved Chat and Overlays"), settingsTarget));
    }

    public boolean isGlobalFilterAvailable() { return isChatFilterEnabled(); }

    public void refreshPanel() {
        SwingUtilities.invokeLater(() -> { if (panel != null) panel.rebuild(); });
    }

    public int getOverlayWidth(OverlayConfig oc) {
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.getOverlayConfig() == oc) return overlay.getEffectiveWidth();
        }
        return oc.getWidgetWidth();
    }

    public void setOverlayWidth(OverlayConfig oc, int width) {
        oc.setWidgetWidth(width);
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.getOverlayConfig() == oc) {
                overlay.setWidth(width);
                overlayManager.saveOverlay(overlay);
            }
        }
        onOverlayConfigChanged();
    }

    public void previewOverlay(OverlayConfig oc) {
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.getOverlayConfig() == oc) overlay.preview();
        }
    }

    public boolean showChannelNames(OverlayConfig overlay) {
        return overlay.isShowChannelNames(config == null || config.showChannelName());
    }

    public String movementHint() {
        return "Hold " + configManager.getConfig(net.runelite.client.config.RuneLiteConfig.class).dragHotkey()
                + " and drag the overlay to move it.";
    }

    public void saveOverlayConfigs() {
        refreshVisibilityHotkeys();
        if (overlayPersistenceBlocked) return;
        String json = gson.toJson(overlayConfigs);
        try { OverlayConfigStore.validateSize(json); }
        catch (com.google.gson.JsonParseException ex) {
            configRecoveryMessage = "Overlay changes exceed the storage safety limit and were not saved. Shorten text or remove overlays. The previous saved configuration is unchanged.";
            refreshPanel();
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY, json);
    }

    private void updatePmWidgetVisibility() {
        boolean shouldHide = config.hidePrivateChat();
        setPmWidgetsHidden(shouldHide);
        pmWidgetsHidden = shouldHide;
    }

    @Subscribe
    public void onBeforeRender(net.runelite.api.events.BeforeRender event) {

        overlayWheelReady = client.getGameState() == GameState.LOGGED_IN && !client.isMenuOpen() && !client.isWidgetSelected()
                && !com.improvedchat.chatbox.resize.TopLevelModals.hasOpenModal(client);
        for (DynamicChatOverlay overlay : overlays) overlay.updateScrollInputState();
        if (!ruleWarningShown && BoundedPattern.warning() != null) {
            ruleWarningShown = true;
            refreshPanel();
        }
        if (nativeChatActive && pmWidgetsHidden && client.getGameState() == GameState.LOGGED_IN) {
            setPmWidgetsHidden(true);
        }
    }

    @Subscribe
    public void onFocusChanged(FocusChanged event) {
        AttentionEngine.setClientFocused(event.isFocused());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) overlayWheelReady = false;
        if (event.getGameState() == GameState.LOGGED_IN) {
            updatePmWidgetVisibility();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.PM_CHAT && pmWidgetsHidden) {
            setPmWidgetsHidden(true);
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() == VarClientID.CHAT_VIEW) {
            for (DynamicChatOverlay overlay : overlays) {
                if (overlay.getOverlayConfig().getPlacementMode() == com.improvedchat.model.PlacementMode.FREE) {
                    updateSmartPosition(overlay);
                }
            }
        }
    }

    @Subscribe
    public void onResizeableChanged(ResizeableChanged event) {
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.getOverlayConfig().getPlacementMode() == com.improvedchat.model.PlacementMode.FREE) {
                updateDefaultPosition(overlay);
                updateSmartPosition(overlay);
            }
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        if (option == null) {
            return;
        }

        if (option.contains("Clear")) {
            if (option.contains("Game:")) {
                clearMessagesForCategories(
                        MessageCategory.GAME,
                        MessageCategory.GAME_CLAN);
                return;
            }
            if (option.contains("Public:")) {
                clearMessagesForCategories(MessageCategory.PUBLIC_CHAT, MessageCategory.AUTO);
                return;
            }
            if (option.contains("Private:")) {
                clearMessagesForCategories(MessageCategory.PRIVATE);
                return;
            }
            if (option.contains("Channel:")) {
                clearMessagesForCategories(MessageCategory.FRIENDS_CHAT);
                return;
            }
            if (option.contains("Clan:")) {
                clearMessagesForCategories(
                        MessageCategory.GAME_CLAN,
                        MessageCategory.CLAN_CHAT,
                        MessageCategory.GUEST_CLAN_CHAT,
                        MessageCategory.GIM_CLAN_CHAT);
                return;
            }
        }
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked event) {
        if (event.getOverlay() instanceof DynamicChatOverlay) {
            DynamicChatOverlay overlay = (DynamicChatOverlay) event.getOverlay();
            if ("Clear".equals(event.getEntry().getOption())) {
                clearedThrough.put(overlay.getOverlayConfig().getId(), OverlayMessage.latestSequence());
                overlay.showLatest();
            } else if ("Latest".equals(event.getEntry().getOption())) {
                overlay.showLatest();
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();

        if (DEBUG) {
            prependDebugType(event);
        }

        if (!ALL_SUPPORTED_TYPES.contains(type)) {
            return;
        }

        if (type == ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            int permanent = client.getVarbitValue(net.runelite.api.gameval.VarbitID.LOGINLOGOUT_PERMANENT);
            int setting = permanent != 0 ? permanent : client.getVarbitValue(net.runelite.api.gameval.VarbitID.LOGINLOGOUT_SETTING);
            if (setting == 2) return;
        }
        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        message = message.trim();

        if (!SENDER_TYPES.contains(type)
                && type != ChatMessageType.LOGINLOGOUTNOTIFICATION
                && message.indexOf('@') >= 0) {
            String expanded = client.macroExpand(message);
            if (expanded != null && !expanded.isEmpty()) {
                message = expanded;
            }
        }

        MessageNode messageNode = event.getMessageNode();

        boolean emojiWatched = EMOJI_WATCHED_TYPES.contains(type)
                && !message.startsWith("!")
                && isEmojiPluginEnabled();
        if (emojiWatched && messageNode != null) {
            String nodeValue = messageNode.getValue();
            if (nodeValue != null && !nodeValue.trim().isEmpty()) {
                message = nodeValue.trim();
            }
        }

        message = ChatDisplayText.normalize(type, message);
        if (message.trim().isEmpty()) return;

        String sender = cleanSender(event.getName());
        String channelName = cleanSender(event.getSender());
        boolean isOutgoing = type == ChatMessageType.PRIVATECHATOUT;
        boolean isBossKc = BOSS_KC_PATTERN.matcher(message).find();

        if (!SENDER_TYPES.contains(type) && type != ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            for (MessageMergeRule rule : MESSAGE_MERGE_RULES) {
                if (rule.matchesPreviousPrefix(message)) {
                    message = message.replace("<br>", " ");
                    break;
                }
            }

            if (!messages.isEmpty()) {
                OverlayMessage lastMsg = messages.get(messages.size() - 1);
                String merged = tryMergeMessages(lastMsg.getMessage(), message);
                if (merged != null && lastMsg.getType() == type
                        && clearedThrough.values().stream().noneMatch(boundary -> boundary >= lastMsg.getSequence())) {
                    OverlayMessage mergedMsg = OverlayMessage.gameMessage(
                            merged, System.currentTimeMillis(), lastMsg.getType(), lastMsg.isBossKc());
                    messages.set(messages.size() - 1, mergedMsg);
                    notifyOverlays(mergedMsg);
                    return;
                }
            }
        }

        OverlayMessage newMsg;
        if (type == ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            int maxFade = 5;
            newMsg = OverlayMessage.loginNotification(
                    sender != null ? sender : "System", message, System.currentTimeMillis(), maxFade);
        } else if (SENDER_TYPES.contains(type)) {
            newMsg = OverlayMessage.senderMessage(
                    sender != null ? sender : "Unknown", channelName, message, System.currentTimeMillis(), type, isOutgoing);
        } else {
            newMsg = OverlayMessage.gameMessage(message, System.currentTimeMillis(), type, isBossKc);
        }

        messages.add(newMsg);
        notifyOverlays(newMsg);

        while (messages.size() > MAX_POOL_SIZE) {
            messages.remove(0);
        }

        if (messageNode != null && message.startsWith("!")) {
            pendingUpdates.add(new PendingMessageUpdate(
                    newMsg, messageNode, COMMAND_VALUE, message, COMMAND_UPDATE_TICKS));
        } else if (emojiWatched && messageNode != null) {
            pendingUpdates.add(new PendingMessageUpdate(
                    newMsg, messageNode, EMOJI_VALUE, message, EMOJI_UPDATE_TICKS));
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        reconcilePendingUpdates();
    }

    private void reconcilePendingUpdates() {
        for (int i = pendingUpdates.size() - 1; i >= 0; i--) {
            PendingMessageUpdate pending = pendingUpdates.get(i);
            pending.ticksRemaining--;

            if (!pending.stillRepresentsOriginalMessage()) {
                pendingUpdates.remove(i);
                continue;
            }

            String currentValue = pending.valueAccessor.apply(pending.messageNode);
            if (currentValue != null && !currentValue.equals(pending.originalText)) {
                rebuildPooledMessage(pending.widgetMessage, currentValue);
                pendingUpdates.remove(i);
            } else if (pending.ticksRemaining <= 0) {
                pendingUpdates.remove(i);
            }
        }
    }

    private void rebuildPooledMessage(OverlayMessage old, String newBody) {
        int idx = messages.indexOf(old);
        if (idx < 0) {
            return;
        }
        messages.set(idx, old.withBody(ChatDisplayText.normalize(old.getType(), newBody)));
    }

    private void reconcileNativeChatOwnership() {
        if (!nativeChatActive) return;
        reconcileNativeChatOwnership(modernChatCompatibility.snapshot());
    }

    private void reconcileNativeChatOwnership(ModernChatOwnership ownership) {
        if (!nativeChatActive) return;
        if (ownership.blocksVisibility()) clientThread.invokeLater(chatboxVisibility::reset);
        if (config.enableResizableChat() && !ownership.blocksGeometry()) {
            chatResizeModule.startUp();
        } else {
            chatResizeModule.shutDown();
        }

        if (config.enableCollapsibleChat() && !ownership.blocksVisibility()) {
            chatCollapseModule.startUp();
        } else {
            chatCollapseModule.shutDown();
        }

        if (config.enableChatboxOpacity() && !ownership.blocksNativePresentation()) {
            chatboxOpacityModule.startUp();
        } else {
            chatboxOpacityModule.shutDown();
        }

        if (!ownership.blocksNativePresentation()) {
            cleanChatModule.startUp();
        } else {
            cleanChatModule.shutDown();
        }

        if (config.enableOfflineChatStatus() && !ownership.blocksNativePresentation()) {
            offlineChatStatusModule.startUp();
        } else {
            offlineChatStatusModule.shutDown();
        }
    }

    private void suspendForModernChatOwnership(ModernChatOwnership ownership) {
        if (ownership.blocksNativePresentation()) {
            offlineChatStatusModule.shutDown();
            cleanChatModule.shutDown();
            chatboxOpacityModule.shutDown();
        }
        if (ownership.blocksVisibility()) {
            clientThread.invokeLater(chatboxVisibility::reset);
            chatCollapseModule.shutDown();
        }
        if (ownership.blocksGeometry()) {
            chatResizeModule.shutDown();
        }
    }

    private void suspendForModernChatConfigTransition(String key) {
        clientThread.invokeLater(chatboxVisibility::reset);
        if (modernChatCompatibility.isRedesignKey(key)) {
            offlineChatStatusModule.shutDown();
            cleanChatModule.shutDown();
            chatboxOpacityModule.shutDown();
            chatCollapseModule.shutDown();
            chatResizeModule.shutDown();
        } else if (modernChatCompatibility.isToggleKey(key)) {
            chatCollapseModule.shutDown();
        }
    }

    private void scheduleNativeChatOwnershipReconcile() {

        if (!nativeChatActive) return;
        long generation = nativeChatGeneration;
        clientThread.invokeLater(() -> {
            if (!nativeChatActive || generation != nativeChatGeneration) return;
            clientThread.invokeAtTickEnd(() -> {
                if (nativeChatActive && generation == nativeChatGeneration) reconcileNativeChatOwnership();
            });
        });
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (event.getPlugin() instanceof ChatFilterPlugin) refreshPanel();
        if (!modernChatCompatibility.isModernChatPlugin(event.getPlugin())) {
            return;
        }

        if (event.isLoaded()) {
            suspendForModernChatOwnership(modernChatCompatibility.snapshot());
        }

        scheduleNativeChatOwnershipReconcile();
    }

    @Subscribe(priority = 100f)
    public void onConfigChanged(ConfigChanged event) {
        renderRevision.incrementAndGet();
        if (modernChatCompatibility.isOwnershipConfig(event.getGroup(), event.getKey())) {
            if (modernChatCompatibility.isModernChatActive()) {
                suspendForModernChatConfigTransition(event.getKey());
                scheduleNativeChatOwnershipReconcile();
            }
            return;
        }

        if (modernChatCompatibility.isModernChatEnableConfig(event.getGroup(), event.getKey())) {
            if ("true".equalsIgnoreCase(event.getNewValue())) {
                suspendForModernChatOwnership(modernChatCompatibility.startupSnapshot());
            }
            scheduleNativeChatOwnershipReconcile();
            return;
        }

        if ("chatfilter".equals(event.getGroup())) {
            rebuildChatFilter();
            refreshPanel();
            return;
        }
        if (!event.getGroup().equals(CONFIG_GROUP)) {
            return;
        }
        if ("hideChatboxHotkey".equals(event.getKey())) {
            refreshVisibilityHotkeys();
            if (net.runelite.client.config.Keybind.NOT_SET.equals(config.hideChatboxHotkey()))
                clientThread.invokeLater(chatboxVisibility::reset);
        }
        if ("useChatFilter".equals(event.getKey()) || event.getKey().startsWith("inputDock")) refreshPanel();
        if ("hideSidePanel".equals(event.getKey())) {
            if (config.hideSidePanel()) {
                clientToolbar.removeNavigation(navButton);
            } else {
                clientToolbar.addNavigation(navButton);
            }
        }
        if ("hidePrivateChat".equals(event.getKey())) {
            updatePmWidgetVisibility();
        }
        if ("enableResizableChat".equals(event.getKey())
                || "enableCollapsibleChat".equals(event.getKey())
                || "enableChatboxOpacity".equals(event.getKey())) {
            reconcileNativeChatOwnership();
        }
        if ("enableRemoveChatOptions".equals(event.getKey())) {
            if (config.enableRemoveChatOptions()) {
                removeChatOptionsModule.startUp();
            } else {
                removeChatOptionsModule.shutDown();
            }
        }
        if ("enableOfflineChatStatus".equals(event.getKey())) {
            reconcileNativeChatOwnership();
        }
        if ("enableDialogueFonts".equals(event.getKey())) {
            if (config.enableDialogueFonts()) {
                dialogueFontsModule.startUp();
            } else {
                dialogueFontsModule.shutDown();
            }
        }
    }

    private void rebuildChatFilter() {
        renderRevision.incrementAndGet();
        chatMessageFilter.rebuild(configManager.getConfig(ChatFilterConfig.class));
    }

    private boolean isChatFilterEnabled() {
        if (chatFilterPlugin == null) {
            for (Plugin p : pluginManager.getPlugins()) {
                if (p instanceof ChatFilterPlugin) {
                    chatFilterPlugin = p;
                    break;
                }
            }
        }
        return chatFilterPlugin != null && pluginManager.isPluginEnabled(chatFilterPlugin);
    }

    private boolean isEmojiPluginEnabled() {
        if (emojiPlugin == null) {
            for (Plugin p : pluginManager.getPlugins()) {
                if (p instanceof EmojiPlugin) {
                    emojiPlugin = p;
                    break;
                }
            }
        }
        return emojiPlugin != null && pluginManager.isPluginEnabled(emojiPlugin);
    }

    public Notification customizeNotification(Notification original) {
        if (original.isInitialized()) return original.withOverride(true);
        return new Notification(original.isEnabled(), true, true,
                runeLiteConfig.enableTrayNotifications(), java.awt.TrayIcon.MessageType.NONE,
                runeLiteConfig.notificationRequestFocus(), runeLiteConfig.notificationSound(), null,
                runeLiteConfig.notificationVolume(), runeLiteConfig.notificationTimeout(),
                runeLiteConfig.enableGameMessageNotification(), runeLiteConfig.flashNotification(),
                runeLiteConfig.notificationFlashColor(), runeLiteConfig.sendNotificationsWhenFocused());
    }

    void notifyOverlays(OverlayMessage message) {
        if (client.getGameState() != GameState.LOGGED_IN || message.isOutgoing()) return;
        if (message.getSender() != null && client.getLocalPlayer() != null
                && net.runelite.client.util.Text.removeTags(message.getSender()).equalsIgnoreCase(client.getLocalPlayer().getName())) return;
        for (OverlayConfig overlay : overlayConfigs) {
            if (!overlay.getNotification().isEnabled() || !overlay.isShow()) continue;
            if (OverlayNotifications.eligible(overlay, message, getMessagesForOverlay(overlay))) {
                sendOverlayNotification(overlay.getNotification(), OverlayNotifications.message(overlay, message));
            }
        }
    }

    protected void sendOverlayNotification(Notification notification, String message) { notifier.notify(notification, message); }

    @Override
    public java.awt.event.MouseWheelEvent mouseWheelMoved(java.awt.event.MouseWheelEvent event) {
        if (event.isConsumed() || event.isAltDown() || event.isControlDown() || event.isShiftDown()
                || !overlayWheelReady) return event;
        DynamicChatOverlay target = null;
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.canScrollAt(event.getPoint())
                    && (target == null || overlay.getScrollPaintOrder() > target.getScrollPaintOrder())) target = overlay;
        }
        if (target != null && target.scrollAt(event.getPoint(), event.getWheelRotation())) event.consume();
        return event;
    }

    public List<OverlayMessage> getHistoryForOverlay(OverlayConfig overlayConfig) {
        return selectMessages(overlayConfig, true);
    }

    public List<OverlayMessage> getMessagesForOverlay(OverlayConfig overlayConfig) {
        return selectMessages(overlayConfig, false);
    }

    private List<OverlayMessage> selectMessages(OverlayConfig overlayConfig, boolean history) {
        Set<ChatMessageType> types = overlayConfig.getMessageTypes();
        if (messages.isEmpty() || types.isEmpty()) {
            return new ArrayList<>(0);
        }

        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = history ? 0 : overlayConfig.getFadeOutDuration();
        long fadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;
        boolean gameFilterEnabled = isGameFilterEnabled();
        boolean bossKcFilterEnabled = isBossKcFilterEnabled();
        boolean useGlobalChatFilter = OverlayMessageFilter.usesGlobalChatFilter(
                config.useChatFilter(), overlayConfig);
        boolean globalChatFilterActive = useGlobalChatFilter && isChatFilterEnabled();
        OverlayMessageFilter overlayFilter = useGlobalChatFilter
                ? null
                : overlayMessageFilters.computeIfAbsent(
                        overlayConfig.getId(), key -> new OverlayMessageFilter());

        int maxMessages = history ? overlayConfig.getHistoryLimit() : overlayConfig.getMaxMessages();
        List<OverlayMessage> source = messages.snapshot();
        long boundary = clearedThrough.getOrDefault(overlayConfig.getId(), 0L);
        Object key = java.util.Arrays.asList(new java.util.HashSet<>(types), fadeOutDuration,
                gameFilterEnabled, bossKcFilterEnabled, useGlobalChatFilter, globalChatFilterActive,
                maxMessages, config.collapseDuplicates(), boundary, renderRevision.get(),
                overlayConfig.getFilterMode(), overlayConfig.getFilteredWords(),
                overlayConfig.getFilteredRegex(), overlayConfig.getFilteredNames());
        com.improvedchat.overlay.OverlaySelectionCache cache = selectionCaches.computeIfAbsent(
                overlayConfig.getId() + (history ? ":history" : ":visible"),
                id -> new com.improvedchat.overlay.OverlaySelectionCache());
        List<OverlayMessage> cached = cache.get(source, key, currentTime);
        if (cached != null) return cached;
        long[] expiresAt = {Long.MAX_VALUE};
        List<OverlayMessage> selected = OverlayHistory.select(source, boundary, maxMessages,
                config.collapseDuplicates(), msg -> {
            if (!types.contains(msg.getType())) return false;
            if (fadeOutThreshold > 0) {
                int msgMaxFade = msg.getMaxFadeSeconds();
                long threshold = msgMaxFade > 0 ? (msgMaxFade * 1000L) + 2000 : fadeOutThreshold;
                long expiry = msg.getTimestamp() + threshold;
                if (currentTime >= expiry) return false;
                expiresAt[0] = Math.min(expiresAt[0], expiry);
            }
            if (gameFilterEnabled && msg.getType() == ChatMessageType.SPAM) return false;
            if (bossKcFilterEnabled && msg.isBossKc()) return false;
            if (globalChatFilterActive && chatMessageFilter.matches(msg.getMessage())) return false;
            return overlayFilter == null || !overlayFilter.shouldExclude(overlayConfig, msg);
        });
        return cache.put(source, key, currentTime, expiresAt[0], selected);
    }

    public void clearMessagesForTypes(Set<ChatMessageType> types) {
        messages.removeIf(msg -> types.contains(msg.getType()));
    }

    public void clearAllMessages() {
        messages.clear();
    }

    private String cleanSender(String name) {
        if (name == null) {
            return null;
        }
        return name.replace('\u00A0', ' ').trim();
    }

    private String tryMergeMessages(String previousMessage, String newMessage) {
        for (MessageMergeRule rule : MESSAGE_MERGE_RULES) {
            if (rule.matches(previousMessage, newMessage)) {
                return rule.merge(previousMessage, newMessage);
            }
        }
        return null;
    }

    private void prependDebugType(ChatMessage event) {
        MessageNode node = event.getMessageNode();
        if (node == null) {
            return;
        }
        node.setValue("<col=ff0000>[" + event.getType().name() + "]</col> " + node.getValue());
    }

    private void clearMessagesForCategories(MessageCategory... categories) {
        EnumSet<ChatMessageType> types = EnumSet.noneOf(ChatMessageType.class);
        for (MessageCategory cat : categories) {
            types.addAll(cat.getTypes());
        }
        clearMessagesForTypes(types);
    }

    public boolean isChatboxHidden() {
        return chatboxVisibility.isHidden() || isChatboxMinimized() || isChatboxWidgetHidden();
    }

    private boolean isChatboxMinimized() {
        return client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337;
    }

    private boolean isChatboxWidgetHidden() {
        Widget chatboxWidget = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        return (chatboxWidget != null && chatboxWidget.isHidden());
    }

    public boolean isGameFilterEnabled() {
        return client.getVarbitValue(VarbitID.GAME_FILTER) == 1;
    }

    public boolean isBossKcFilterEnabled() {
        return client.getVarbitValue(VarbitID.BOSS_KILLCOUNT_FILTERED) == 1;
    }

    private void updateDefaultPosition(net.runelite.client.ui.overlay.Overlay overlay) {
        OverlayPosition defaultPos = client.isResized()
                ? OverlayPosition.ABOVE_CHATBOX_RIGHT
                : OverlayPosition.BOTTOM_LEFT;
        overlay.setPosition(defaultPos);
    }

    private void updateSmartPosition(net.runelite.client.ui.overlay.Overlay overlay) {
        if (!config.smartPositioning()) {
            return;
        }

        OverlayPosition currentPos = overlay.getPreferredPosition() != null
                ? overlay.getPreferredPosition()
                : overlay.getPosition();

        if (isTopPosition(currentPos)) {
            return;
        }

        OverlayPosition targetPos;
        if (!client.isResized()) {
            targetPos = OverlayPosition.BOTTOM_LEFT;
        } else if (isChatboxHidden()) {
            targetPos = OverlayPosition.ABOVE_CHATBOX_RIGHT;
        } else {
            return;
        }

        if (currentPos != targetPos) {
            overlay.setPreferredPosition(targetPos);
        }
    }

    private boolean isTopPosition(OverlayPosition position) {
        return position == OverlayPosition.TOP_CENTER
                || position == OverlayPosition.TOP_RIGHT
                || position == OverlayPosition.TOP_LEFT
                || position == OverlayPosition.CANVAS_TOP_RIGHT;
    }

    private final Map<Widget, Boolean> hiddenPmRows = new java.util.IdentityHashMap<>();

    private void setPmWidgetsHidden(boolean hidden) {
        Map<Widget, Boolean> live = new java.util.IdentityHashMap<>();
        Widget container = client.getWidget(InterfaceID.PM_CHAT, 0);
        if (hidden && container != null) {
            Widget[] children = container.getDynamicChildren();
            if (children != null) {

                for (int row = 0; row < children.length; row += 4) {
                    boolean timer = false;
                    for (int i = row; i < Math.min(row + 4, children.length); i++) {
                        Widget child = children[i];
                        if (child != null && child.getText() != null
                            && net.runelite.client.util.Text.removeTags(child.getText()).startsWith("System update in:")) timer = true;
                    }
                    if (!timer) {
                        for (int i = row; i < Math.min(row + 4, children.length); i++) {
                            if (children[i] != null) live.put(children[i], Boolean.TRUE);
                        }
                        Widget[] clickboxes = container.getStaticChildren();
                        int index = row / 4;
                        if (clickboxes != null && index < clickboxes.length && clickboxes[index] != null) live.put(clickboxes[index], Boolean.TRUE);
                    }
                }
            }
        }
        hiddenPmRows.entrySet().removeIf(entry -> {
            if (live.containsKey(entry.getKey())) return false;
            if (entry.getKey().isSelfHidden()) entry.getKey().setHidden(entry.getValue());
            return true;
        });
        live.keySet().forEach(widget -> {
            if (!hiddenPmRows.containsKey(widget) || !widget.isSelfHidden()) hiddenPmRows.put(widget, widget.isSelfHidden());
            widget.setHidden(true);
        });
    }

    @Provides
    ImprovedChatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ImprovedChatConfig.class);
    }

    boolean isHoveringGameChatControls() {
        final Point mouse = client.getMouseCanvasPosition();
        return isHoveringWidget(mouse, client.getWidget(InterfaceID.Chatbox.CHAT_ALL)) ||
                isHoveringWidget(mouse, client.getWidget(InterfaceID.Chatbox.CHAT_GAME)) ||
                isHoveringWidget(mouse, client.getWidget(InterfaceID.Chatbox.CHAT_PUBLIC));
    }

    boolean isHoveringWidget(Point mouse, Widget target) {
        if (target == null || target.isHidden()) {
            return false;
        }
        return target.getBounds().contains(mouse.getX(), mouse.getY());
    }
}
