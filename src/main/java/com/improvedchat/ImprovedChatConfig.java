package com.improvedchat;

import com.improvedchat.chatbox.clean.data.IndentMode;
import com.improvedchat.dialogue.FontChoice;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("improvedchat")
public interface ImprovedChatConfig extends Config {
    String GROUP = "improvedchat";
    @ConfigSection(name = "Chat input dock", description = "Buttons for the typing destination. Configure attachment in the side panel Tools tab.", position = 5, closedByDefault = true)
    String inputDockSection = "inputDock";

    @ConfigItem(keyName = "inputDockEnabled", name = "Show input dock", description = "Show buttons that switch only your typing destination", section = inputDockSection, position = 0)
    default boolean inputDockEnabled() { return false; }
    @ConfigItem(keyName = "inputDockPublic", name = "Public", description = "Show the Public input button", section = inputDockSection, position = 1)
    default boolean inputDockPublic() { return true; }
    @ConfigItem(keyName = "inputDockClan", name = "Clan", description = "Show the Clan input button", section = inputDockSection, position = 2)
    default boolean inputDockClan() { return true; }
    @ConfigItem(keyName = "inputDockChannel", name = "Channel", description = "Show the Friends Chat input button", section = inputDockSection, position = 3)
    default boolean inputDockChannel() { return true; }
    @ConfigItem(keyName = "inputDockGim", name = "GIM", description = "Show the Group input button when a Group Ironman channel is available", section = inputDockSection, position = 4)
    default boolean inputDockGim() { return true; }
    @Range(min = 60, max = 200)
    @Units(Units.PERCENT)
    @ConfigItem(keyName = "inputDockScale", name = "Button size", description = "Scale icons, text and buttons together", section = inputDockSection, position = 5)
    default int inputDockScale() { return 100; }
    @ConfigItem(hidden = true, keyName = "inputDockPlacement", name = "Dock placement", description = "Choose a floating dock or attach it in the side panel Tools tab")
    default com.improvedchat.input.InputDockPlacement inputDockPlacement() { return com.improvedchat.input.InputDockPlacement.FLOATING; }
    @ConfigItem(hidden = true, keyName = "inputDockTarget", name = "Attach to overlay", description = "Chosen chat overlay")
    default String inputDockTarget() { return ""; }

    @ConfigItem(hidden = true, keyName = "inputDockButtonBackground", name = "Button background", description = "Chat input button background, shared by selected and unselected buttons")
    default Color inputDockButtonBackground() { return new Color(38, 40, 43); }
    @ConfigItem(hidden = true, keyName = "inputDockButtonBorder", name = "Button border", description = "Chat input button border")
    default Color inputDockButtonBorder() { return new Color(61, 64, 68); }
    @ConfigItem(hidden = true, keyName = "inputDockSelectedBorder", name = "Selected border", description = "Outline and indicator for the active chat input mode")
    default Color inputDockSelectedBorder() { return new Color(100, 151, 195); }
    @ConfigItem(hidden = true, keyName = "inputDockButtonForeground", name = "Button text and icons", description = "Chat input button text and icons")
    default Color inputDockButtonForeground() { return new Color(210, 213, 217); }
    @ConfigItem(hidden = true, keyName = "inputDockDisabledForeground", name = "Unavailable text and icons", description = "Text and icons for unavailable input modes")
    default Color inputDockDisabledForeground() { return new Color(117, 119, 122); }

    @ConfigSection(name = "Chatbox", description = "Collapse, resize and adjust chat opacity, including secondary sizing", position = 0, closedByDefault = true)
    String chatboxSection = "chatbox";
    @ConfigSection(name = "Chat cleanup", description = "Independent channel names, instructions, status icons and message cleanup", position = 1, closedByDefault = true)
    String cleanupSection = "cleanup";
    @ConfigSection(name = "Overlay setup: side panel", description = "Open the Improved Chat and Overlays side panel to create overlays and configure colors, RGB effects, sounds and Message highlights. These options apply across overlays.", position = 2, closedByDefault = false)
    String overlayBehaviorSection = "overlayBehavior";
    @ConfigSection(name = "Dialogue text", description = "Fonts and readability for supported dialogue", position = 3, closedByDefault = true)
    String dialogueSection = "dialogue";
    @ConfigSection(name = "Advanced", description = "Resize restoration, channel markers and integration details", position = 4, closedByDefault = true)
    String advancedSection = "advanced";

    @ConfigItem(
        keyName = "hideSidePanel",
        name = "Hide side panel",
        description = "Hide the Improved Chat and Overlays side panel",
        position = 0,
        section = overlayBehaviorSection
    )
    default boolean hideSidePanel() { return false; }

    @ConfigItem(keyName = "textShadow", name = "Text Shadow", description = "Draw shadow behind text", section = overlayBehaviorSection, position = 1)
    default boolean textShadow() { return true; }

    @ConfigItem(keyName = "wrapText", name = "Wrap Text", description = "Wrap long messages to multiple lines", section = overlayBehaviorSection, position = 2)
    default boolean wrapText() { return true; }

    @ConfigItem(keyName = "hidePrivateChat", name = "Hide Default Private Chat", description = "Hide RuneLite's default split private chat", section = overlayBehaviorSection, position = 3)
    default boolean hidePrivateChat() { return true; }

    @ConfigItem(keyName = "smartPositioning", name = "Smart Positioning", description = "Automatically reposition free overlays based on client mode and chatbox state", section = overlayBehaviorSection, position = 4)
    default boolean smartPositioning() { return true; }

    @ConfigItem(keyName = "collapseDuplicates", name = "Collapse Duplicates", description = "Merge consecutive identical messages into one with a count", section = overlayBehaviorSection, position = 5)
    default boolean collapseDuplicates() { return false; }

    @ConfigItem(keyName = "showChannelName", name = "Show Channel Names", description = "Show the channel name prefix for friends and clan chat messages", section = overlayBehaviorSection, position = 6)
    default boolean showChannelName() { return true; }

    @ConfigItem(keyName = "useChatFilter", name = "Use Chat Filter Globally", description = "When enabled, overrides all overlay chat filters with the RuneLite global chat filter", section = overlayBehaviorSection, position = 7)
    default boolean useChatFilter() { return false; }

    @ConfigItem(keyName = "timestampFormat", name = "Timestamp Format", description = "Timestamp format used by overlays", section = overlayBehaviorSection, position = 8)
    default String timestampFormat() { return "[HH:mm]"; }

    @ConfigItem(hidden = true, keyName = "chatColorSource", name = "Chat Color Source", description = "Baseline color source", section = overlayBehaviorSection, position = 9)
    default ChatColorSource chatColorSource() { return ChatColorSource.AUTOMATIC; }

    @ConfigItem(hidden = true, keyName = "gameColour", name = "Game", description = "Fallback text colour for game messages", section = overlayBehaviorSection, position = 10)
    default Color gameColour() { return Color.WHITE; }

    @ConfigItem(hidden = true, keyName = "publicColour", name = "Public", description = "Fallback text colour for public chat messages", section = overlayBehaviorSection, position = 11)
    default Color publicColour() { return new Color(148, 148, 255); }

    @ConfigItem(hidden = true, keyName = "privateColour", name = "Private", description = "Fallback text colour for private messages", section = overlayBehaviorSection, position = 12)
    default Color privateColour() { return new Color(0, 255, 255); }

    @ConfigItem(hidden = true, keyName = "friendsColour", name = "Friends", description = "Fallback text colour for friends chat messages", section = overlayBehaviorSection, position = 13)
    default Color friendsColour() { return new Color(239, 80, 80); }

    @ConfigItem(hidden = true, keyName = "clanColour", name = "Clan", description = "Fallback text colour for clan chat messages", section = overlayBehaviorSection, position = 14)
    default Color clanColour() { return new Color(127, 0, 0); }

    @ConfigItem(hidden = true, keyName = "guestClanColour", name = "Guest Clan", description = "Fallback text colour for guest clan chat messages", section = overlayBehaviorSection, position = 15)
    default Color guestClanColour() { return new Color(0, 211, 0); }

    @ConfigItem(hidden = true, keyName = "gimClanColour", name = "GIM Clan", description = "Fallback text colour for Group Ironman clan chat messages", section = overlayBehaviorSection, position = 16)
    default Color gimClanColour() { return new Color(127, 0, 0); }

    @ConfigItem(hidden = true, keyName = "tradeColour", name = "Trade", description = "Fallback text colour for trade messages", section = overlayBehaviorSection, position = 17)
    default Color tradeColour() { return new Color(223, 32, 255); }

    @ConfigItem(hidden = true, keyName = "challengeColour", name = "Challenge", description = "Fallback text colour for challenge request messages", section = overlayBehaviorSection, position = 18)
    default Color challengeColour() { return new Color(255, 32, 223); }

    @ConfigItem(hidden = true, keyName = "didYouKnowColour", name = "Did You Know?", description = "Fallback text colour for Did You Know messages", section = overlayBehaviorSection, position = 19)
    default Color didYouKnowColour() { return new Color(255, 255, 0); }

    @ConfigItem(hidden = true, keyName = "broadcastColour", name = "Broadcast", description = "Fallback text colour for broadcast messages", section = overlayBehaviorSection, position = 20)
    default Color broadcastColour() { return Color.WHITE; }

    @ConfigItem(hidden = true, keyName = "autoColour", name = "Autochat", description = "Fallback text colour for autochat messages", section = overlayBehaviorSection, position = 21)
    default Color autoColour() { return new Color(64, 64, 255); }

    @ConfigItem(hidden = true, keyName = "enableSolidRecolor", name = "Enable Message Color Rules", description = "Apply matching colors to chatbox and overlays", section = overlayBehaviorSection, position = 22)
    default boolean enableMessageColorRules() { return false; }

    @ConfigItem(hidden = true, keyName = "solidRecolorRules", name = "Message Color Rules", description = "One rule per line: text::1 through text::9; append ::flash to flash matching overlays", section = overlayBehaviorSection, position = 23)
    default String messageColorRules() {
        return "is about to expire::1\nhas expired::1\nachieved a new::2\nhas reached::4";
    }

    @ConfigItem(hidden = true, keyName = "recolorPlayerChat", name = "Include Player Chat", description = "Allow rules to match player-authored chat", section = overlayBehaviorSection, position = 24)
    default boolean includePlayerChatInColorRules() { return false; }

    @ConfigItem(hidden = true, keyName = "solidColor1", name = "Rule Color 1", description = "Message rule palette color 1", section = overlayBehaviorSection, position = 25)
    default Color messageRuleColor1() { return new Color(239, 16, 32); }
    @ConfigItem(hidden = true, keyName = "solidColor2", name = "Rule Color 2", description = "Message rule palette color 2", section = overlayBehaviorSection, position = 26)
    default Color messageRuleColor2() { return new Color(255, 149, 0); }
    @ConfigItem(hidden = true, keyName = "solidColor3", name = "Rule Color 3", description = "Message rule palette color 3", section = overlayBehaviorSection, position = 27)
    default Color messageRuleColor3() { return new Color(255, 214, 10); }
    @ConfigItem(hidden = true, keyName = "solidColor4", name = "Rule Color 4", description = "Message rule palette color 4", section = overlayBehaviorSection, position = 28)
    default Color messageRuleColor4() { return new Color(52, 199, 89); }
    @ConfigItem(hidden = true, keyName = "solidColor5", name = "Rule Color 5", description = "Message rule palette color 5", section = overlayBehaviorSection, position = 29)
    default Color messageRuleColor5() { return new Color(50, 215, 255); }
    @ConfigItem(hidden = true, keyName = "solidColor6", name = "Rule Color 6", description = "Message rule palette color 6", section = overlayBehaviorSection, position = 30)
    default Color messageRuleColor6() { return new Color(10, 132, 255); }
    @ConfigItem(hidden = true, keyName = "solidColor7", name = "Rule Color 7", description = "Message rule palette color 7", section = overlayBehaviorSection, position = 31)
    default Color messageRuleColor7() { return new Color(94, 92, 230); }
    @ConfigItem(hidden = true, keyName = "solidColor8", name = "Rule Color 8", description = "Message rule palette color 8", section = overlayBehaviorSection, position = 32)
    default Color messageRuleColor8() { return new Color(191, 90, 242); }
    @ConfigItem(hidden = true, keyName = "solidColor9", name = "Rule Color 9", description = "Message rule palette color 9", section = overlayBehaviorSection, position = 33)
    default Color messageRuleColor9() { return Color.WHITE; }

    @ConfigItem(hidden = true, keyName = "enableRainbowRecolor", name = "Enable Overlay Rainbow Rules", description = "Apply rainbow styling to overlays only", section = overlayBehaviorSection, position = 34)
    default boolean enableOverlayRainbowRules() { return false; }

    @ConfigItem(hidden = true, keyName = "rainbowPatterns", name = "Overlay Rainbow Rules", description = "One pattern per line; prefix regex: for regular expressions", section = overlayBehaviorSection, position = 35)
    default String overlayRainbowRules() {
        return "achieved a new\nhas reached\nYou have stopped moving!";
    }

    @ConfigItem(hidden = true, keyName = "rainbowStyle", name = "Rainbow Style", description = "Color by word or visible character", section = overlayBehaviorSection, position = 36)
    default RainbowStyle rainbowStyle() { return RainbowStyle.PER_WORD; }

    String HIDE_SCROLLBAR_KEY = "hideScrollbar";
    String DEFAULT_CUSTOM_CHANNEL_NAME = "[<col=0000ff>$</col>]";

    enum CollapsedButtonContent {
        STATIC_TEXT("Static text"),
        REPORT_BUTTON_TEXT("Report button text");

        private final String label;

        CollapsedButtonContent(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum Revert {
        UNGROW, UNSHRINK, BOTH, NEITHER;

        public boolean ungrows() {
            return this == UNGROW || this == BOTH;
        }

        public boolean unshrinks() {
            return this == UNSHRINK || this == BOTH;
        }
    }

    enum Mode {
        HOLD, TOGGLE
    }

    String HEIGHT_CHANGE = "heightChange";
    String WIDTH_CHANGE = "widthChange";
    String REWRAP_PRIVATE_CHAT = "rewrapPrivateChat";
    String RESIZE_TAB_BUTTONS = "resizeTabButtons";
    String GROW_INTERFACES = "growInterfaces";
    String FIXED_HEIGHT_CHANGE = "fixedHeightChange";
    String FIXED_TAB_COLLAPSE = "fixedTabCollapse";
    String FIXED_ADJUST_VIEWPORT = "fixedAdjustViewport";
    String REVERT_FOR_DIALOGS = "revertForDialogs";
    String REVERT_FOR_MODALS = "revertForModals";
    String TOGGLE_SHOW_CHAT = "toggleShowChat";
    String INDICATOR_COLOR = "indicatorColor";
    String DRAG_MODIFIER = "dragModifier";
    String LIVE_REWRAP = "liveRewrap";
    String SWAP_HEIGHT_CHANGE = "swapHeightChange";
    String SWAP_WIDTH_CHANGE = "swapWidthChange";
    String SWAP_SIZE_KEYBIND = "swapSizeKeybind";
    String SWAP_SIZE_MODE = "swapSizeMode";
    String NO_BORDERS = "noBorders";
    String NO_BACKGROUND_ZOOM = "noBackgroundZoom";

    @ConfigItem(keyName = "enableCollapsibleChat", name = "Enable Collapsible Chat", description = "Collapse native chat tabs to one button when chat is hidden", section = chatboxSection, position = 0)
    default boolean enableCollapsibleChat() { return false; }

    @ConfigItem(keyName = "collapsedButtonContent", name = "Button Content", description = "Content shown on the single collapsed chat button", section = chatboxSection, position = 1)
    default CollapsedButtonContent collapsedButtonContent() { return CollapsedButtonContent.STATIC_TEXT; }

    @ConfigItem(keyName = "collapsedButtonTransparent", name = "Transparent Button", description = "Make the collapsed button transparent", section = chatboxSection, position = 2)
    default boolean collapsedButtonTransparent() { return false; }

    @ConfigItem(keyName = "collapsedButtonText", name = "Button Text", description = "Text shown while chat is collapsed", section = chatboxSection, position = 3)
    default String collapsedButtonContentCustomText() { return "-"; }

    @ConfigItem(keyName = "collapsedButtonHoverText", name = "Hover Text", description = "Text shown while hovering the collapsed button", section = chatboxSection, position = 4)
    default String collapsedButtonContentCustomTextHovered() { return "+"; }

    @ConfigItem(keyName = "collapseUnreadPublic", name = "Unread Public", description = "Highlight the collapsed button for unread public messages", section = chatboxSection, position = 5)
    default boolean highlightOnUnreadPublicMessages() { return false; }

    @ConfigItem(keyName = "collapseUnreadPrivate", name = "Unread Private", description = "Highlight the collapsed button for unread private messages", section = chatboxSection, position = 6)
    default boolean highlightOnUnreadPrivateMessages() { return false; }

    @ConfigItem(keyName = "collapseUnreadFriends", name = "Unread Friends Chat", description = "Highlight the collapsed button for unread friends chat messages", section = chatboxSection, position = 7)
    default boolean highlightOnUnreadFriendsChatMessages() { return false; }

    @ConfigItem(keyName = "collapseUnreadClan", name = "Unread Clan", description = "Highlight the collapsed button for unread clan messages", section = chatboxSection, position = 8)
    default boolean highlightOnUnreadClanChatMessages() { return false; }

    @ConfigItem(keyName = "collapseUnreadTrade", name = "Unread Trade", description = "Highlight the collapsed button for unread trade messages", section = chatboxSection, position = 9)
    default boolean highlightOnUnreadTradeMessages() { return false; }

    @ConfigItem(keyName = "enableResizableChat", name = "Enable Resizable Chat", description = "Enable advanced native chat resizing", section = chatboxSection, position = 10)
    default boolean enableResizableChat() { return false; }

    @Range(min = -165, max = 10000)
    @Units(Units.PIXELS)
    @ConfigItem(keyName = HEIGHT_CHANGE, name = "Resizable Height Change", description = "Add or subtract native chat height in resizable layout", section = chatboxSection, position = 11)
    default int heightChange() { return 28; }

    @Range(min = -519, max = 10000)
    @Units(Units.PIXELS)
    @ConfigItem(keyName = WIDTH_CHANGE, name = "Resizable Width Change", description = "Add or subtract native chat width in resizable layout", section = chatboxSection, position = 12)
    default int widthChange() { return 80; }

    @ConfigItem(keyName = REWRAP_PRIVATE_CHAT, name = "Adjust Private Split Width", description = "Match split-private-message width to resized chat", section = chatboxSection, position = 13)
    default boolean rewrapPrivateChat() { return true; }

    @ConfigItem(keyName = RESIZE_TAB_BUTTONS, name = "Resize Chat Tab Buttons", description = "Stretch chat tabs to match adjusted width", section = chatboxSection, position = 14)
    default boolean resizeTabButtons() { return false; }

    @ConfigItem(keyName = GROW_INTERFACES, name = "Grow Interface Height", description = "Let interfaces reclaim space freed by a smaller chatbox", section = advancedSection, position = 0)
    default boolean growInterfaces() { return true; }

    @Range(min = -165, max = 10000)
    @Units(Units.PIXELS)
    @ConfigItem(keyName = FIXED_HEIGHT_CHANGE, name = "Fixed Height Change", description = "Add or subtract native chat height in fixed layout", section = chatboxSection, position = 15)
    default int fixedHeightChange() { return 0; }

    @ConfigItem(keyName = FIXED_TAB_COLLAPSE, name = "Hideable Fixed Chat", description = "Allow fixed-layout chat to be hidden like resizable chat", section = chatboxSection, position = 16)
    default boolean fixedTabCollapse() { return true; }

    @ConfigItem(keyName = FIXED_ADJUST_VIEWPORT, name = "Adjust Camera On Grow", description = "Keep the player centered when fixed chat grows", section = advancedSection, position = 1)
    default boolean fixedAdjustViewport() { return false; }

    @ConfigItem(keyName = REVERT_FOR_DIALOGS, name = "Revert For Dialogs", description = "Temporarily return adjusted dimensions toward stock while chat dialogs are open", section = advancedSection, position = 2)
    default Revert revertForDialogs() { return Revert.BOTH; }

    @ConfigItem(keyName = REVERT_FOR_MODALS, name = "Revert For Interfaces", description = "Temporarily return adjusted dimensions toward stock while top-level interfaces are open", section = advancedSection, position = 3)
    default Revert revertForModals() { return Revert.UNGROW; }

    @ConfigItem(keyName = TOGGLE_SHOW_CHAT, name = "Collapse Chat Keybind", description = "Open or close native chat with a keybind (for example, backtick). Works with Collapsible Chat or Resizable Chat enabled; fixed layout also requires Hideable Fixed Chat.", section = chatboxSection, position = 17)
    default Keybind toggleShowChat() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = NO_BORDERS, name = "Don't Draw Resize Borders", description = "Hide Improved Chat and Overlays's resize frame; native dialogue and option-menu borders remain untouched", section = advancedSection, position = 4)
    default boolean noBorders() { return false; }

    @ConfigItem(keyName = NO_BACKGROUND_ZOOM, name = "Don't Zoom Background", description = "Keep the normal opaque chat background at its native artwork scale", section = advancedSection, position = 5)
    default boolean noBackgroundZoom() { return false; }

    @ConfigItem(keyName = DRAG_MODIFIER, name = "Drag-Resize Modifier", description = "Hold this key while dragging a chat border to resize; unset disables drag resizing", section = chatboxSection, position = 18)
    default Keybind dragModifier() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = LIVE_REWRAP, name = "Live Re-wrap", description = "Re-wrap chat continuously while drag-resizing", section = advancedSection, position = 6)
    default boolean liveRewrap() { return true; }

    @Alpha
    @ConfigItem(keyName = INDICATOR_COLOR, name = "Drag Indicator Color", description = "Color of the active drag-resize border", section = chatboxSection, position = 19)
    default Color indicatorColor() { return Color.GREEN; }

    @Range(min = -165, max = 10000)
    @Units(Units.PIXELS)
    @ConfigItem(keyName = SWAP_HEIGHT_CHANGE, name = "Secondary Height Change", description = "Height change while the secondary size is active", section = chatboxSection, position = 20)
    default int secondaryHeightChange() { return 0; }

    @Range(min = -519, max = 10000)
    @Units(Units.PIXELS)
    @ConfigItem(keyName = SWAP_WIDTH_CHANGE, name = "Secondary Width Change", description = "Width change while the secondary size is active", section = chatboxSection, position = 21)
    default int secondaryWidthChange() { return 0; }

    @ConfigItem(keyName = SWAP_SIZE_MODE, name = "Secondary Size Mode", description = "Hold or toggle the secondary chat size", section = chatboxSection, position = 22)
    default Mode secondaryMode() { return Mode.HOLD; }

    @ConfigItem(keyName = SWAP_SIZE_KEYBIND, name = "Secondary Size Keybind", description = "Switch to the secondary chat size", section = chatboxSection, position = 23)
    default Keybind secondaryKeybind() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "removeWelcome", name = "Remove Welcome Message", description = "Remove the Welcome to Old School RuneScape message", section = cleanupSection, position = 0)
    default boolean removeWelcome() { return false; }

    @ConfigItem(keyName = "lineBreakIndentationMode", name = "Indent Mode", description = "Choose where wrapped channel-message lines begin", section = cleanupSection, position = 1)
    default IndentMode indentationMode() { return IndentMode.MESSAGE; }

    @ConfigItem(keyName = HIDE_SCROLLBAR_KEY, name = "Hide Scrollbar", description = "Hide the chat scrollbar while keeping mouse-wheel scrolling", section = cleanupSection, position = 2)
    default boolean hideScrollbar() { return false; }

    @ConfigItem(keyName = "hideSpecs", name = "Remove Special Attack Text", description = "Remove Dragon and Crystal equipment special-attack chat text", section = cleanupSection, position = 3)
    default boolean hideSpecs() { return false; }

    @ConfigItem(keyName = "improvedTimestamps", name = "Fixed-width Timestamps", description = "Use equal-width digits when chat timestamps are enabled", section = cleanupSection, position = 4)
    default boolean isFixedWidthTimestampEnabled() { return false; }

    @ConfigItem(keyName = "colorBar", name = "Enable Color Bar", description = "Draw a thin channel-colored marker beside each native chat message", section = advancedSection, position = 7)
    default boolean isColorBarEnabled() { return false; }

    @Units(Units.PIXELS)
    @Range(min = -1000, max = 1000)
    @ConfigItem(keyName = "colorBarOffset", name = "Color Bar Offset", description = "Horizontal offset of the message color bar", section = advancedSection, position = 8)
    default int colorBarOffset() { return 0; }

    @Units(Units.PIXELS)
    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "colorBarWidth", name = "Color Bar Width", description = "Width of the message color bar", section = advancedSection, position = 9)
    default int colorBarWidth() { return 1; }

    @Alpha
    @ConfigItem(keyName = "noChannelColor", name = "No Channel", description = "Color bar color for messages without a channel", section = advancedSection, position = 10)
    default Color noChannelColor() { return new Color(0, true); }

    @Alpha
    @ConfigItem(keyName = "clanColor", name = "Clan", description = "Color bar color for clan chat", section = advancedSection, position = 11)
    default Color clanChannelColor() { return new Color(0x0B3CC4); }

    @Alpha
    @ConfigItem(keyName = "friendColor", name = "Friends Chat", description = "Color bar color for friends chat", section = advancedSection, position = 12)
    default Color friendsChannelColor() { return new Color(0xF8EC3B); }

    @Alpha
    @ConfigItem(keyName = "groupIronColor", name = "Group Iron", description = "Color bar color for Group Ironman chat", section = advancedSection, position = 13)
    default Color groupIronChannelColor() { return new Color(0x195985); }

    @Alpha
    @ConfigItem(keyName = "guestClanColor", name = "Guest Clan", description = "Color bar color for guest clan chat", section = advancedSection, position = 14)
    default Color guestClanChannelColor() { return new Color(0x00855E); }

    @ConfigItem(keyName = "removeClanInstruction", name = "Hide Clan Instructions", description = "Remove clan-channel usage instructions", section = cleanupSection, position = 5)
    default boolean removeClanInstruction() { return false; }

    @ConfigItem(keyName = "removeClanName", name = "Hide Clan Name", description = "Remove the clan name prefix from clan messages", section = cleanupSection, position = 6)
    default boolean removeClanName() { return false; }

    @ConfigItem(keyName = "shortClanName", name = "Custom Clan Name", description = "Replacement clan label; use $ for the current clan name", section = cleanupSection, position = 7)
    default String getShortClanName() { return DEFAULT_CUSTOM_CHANNEL_NAME; }

    @ConfigItem(keyName = "removeClanRank", name = "Remove Clan Rank", description = "Remove clan-rank icons from usernames", section = cleanupSection, position = 8)
    default boolean removeClanRank() { return false; }

    @ConfigItem(keyName = "removeGuestClanInstruction", name = "Hide Guest Clan Instructions", description = "Remove guest-clan usage instructions", section = cleanupSection, position = 9)
    default boolean removeGuestClanInstruction() { return false; }

    @ConfigItem(keyName = "removeGuestClanReconnecting", name = "Hide Guest Clan Reconnecting", description = "Remove guest-clan automatic reconnect messages", section = cleanupSection, position = 10)
    default boolean removeGuestClanReconnecting() { return false; }

    @ConfigItem(keyName = "removeGuestClanName", name = "Hide Guest Clan Name", description = "Remove the guest clan name prefix", section = cleanupSection, position = 11)
    default boolean removeGuestClanName() { return false; }

    @ConfigItem(keyName = "shortGuestClanName", name = "Custom Guest Clan Name", description = "Replacement guest-clan label; use $ for the current clan name", section = cleanupSection, position = 12)
    default String getShortGuestClanName() { return DEFAULT_CUSTOM_CHANNEL_NAME; }

    @ConfigItem(keyName = "removeGroupIronInstruction", name = "Hide GIM Instructions", description = "Remove Group Ironman channel usage instructions", section = cleanupSection, position = 13)
    default boolean removeGroupIronInstruction() { return false; }

    @ConfigItem(keyName = "removeGroupIronName", name = "Hide GIM Name", description = "Remove the Group Ironman channel name prefix", section = cleanupSection, position = 14)
    default boolean removeGroupIronName() { return false; }

    @ConfigItem(keyName = "moveGroupIronBroadcasts", name = "Move GIM Broadcasts", description = "Keep GIM broadcasts out of the clan tab", section = cleanupSection, position = 15)
    default boolean removeGroupIronFromClan() { return false; }

    @ConfigItem(keyName = "shortGroupIronName", name = "Custom GIM Name", description = "Replacement GIM label; use $ for the current group name", section = cleanupSection, position = 16)
    default String getShortGroupIronName() { return DEFAULT_CUSTOM_CHANNEL_NAME; }

    @ConfigItem(keyName = "removeFriendsChatInstruction", name = "Hide Friends Chat Instructions", description = "Remove friends-chat usage instructions", section = cleanupSection, position = 17)
    default boolean removeFriendsChatStartup() { return false; }

    @ConfigItem(keyName = "removeFriendsChatName", name = "Hide Friends Chat Name", description = "Remove the friends-chat channel prefix", section = cleanupSection, position = 18)
    default boolean removeFriendsChatName() { return false; }

    @ConfigItem(keyName = "removeFriendsAttempting", name = "Hide Friends Chat Join Attempt", description = "Remove friends-chat join-attempt messages", section = cleanupSection, position = 19)
    default boolean removeFriendsAttempting() { return false; }

    @ConfigItem(keyName = "removeFriendsNowTalking", name = "Hide Friends Chat Joined Message", description = "Remove friends-chat now-talking messages", section = cleanupSection, position = 20)
    default boolean removeFriendsNowTalking() { return false; }

    @ConfigItem(keyName = "shortFriendsName", name = "Custom Friends Chat Name", description = "Replacement friends-chat label; use $ for the current channel name", section = cleanupSection, position = 21)
    default String getShortFriendsName() { return DEFAULT_CUSTOM_CHANNEL_NAME; }

    @ConfigItem(keyName = "enableChatboxOpacity", name = "Enable Chatbox Opacity", description = "Enable transparent-chat background and button opacity controls", section = chatboxSection, position = 24)
    default boolean enableChatboxOpacity() { return false; }

    @Range(min = -1, max = 255)
    @ConfigItem(keyName = "chatboxOpacity", name = "Chatbox Opacity", description = "-1 keeps RuneLite default; 0 is opaque and 255 is fully transparent", section = chatboxSection, position = 25)
    default int chatboxOpacity() { return 150; }

    @Range(min = -1, max = 255)
    @ConfigItem(keyName = "buttonOpacity", name = "Button Opacity", description = "-1 keeps RuneLite default; 0 is opaque and 255 is fully transparent", section = chatboxSection, position = 26)
    default int buttonOpacity() { return -1; }

    @ConfigItem(keyName = "opacityDialogueMenus", name = "Dialogue & Menus", description = "Apply chatbox opacity to dialogue boxes and option menus shown in the chat area", section = chatboxSection, position = 27)
    default boolean opacityDialogueMenus() { return true; }

    @ConfigItem(keyName = "chatboxBorder", name = "Chatbox border", description = "Draw a cosmetic border around the regular chat area while it is visible. Independent of custom overlays and resize guides.", section = chatboxSection, position = 28)
    default boolean chatboxBorder() { return false; }

    @Alpha
    @ConfigItem(keyName = "chatboxBorderColor", name = "Border color", description = "Regular chatbox border color and opacity. Opacity also applies to RGB.", section = chatboxSection, position = 29)
    default Color chatboxBorderColor() { return Color.WHITE; }

    @ConfigItem(keyName = "hideChatboxHotkey", name = "Hide entire chatbox hotkey", description = "Toggle the regular chatbox and every chat button. Dialogues remain usable. Separate from collapse; resets on logout or profile change. Suspended while Modern Chat controls visibility.", section = chatboxSection, position = 34)
    default Keybind hideChatboxHotkey() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "chatboxBorderThickness", name = "Border thickness", description = "Thickness of the regular chatbox border", section = chatboxSection, position = 30)
    default com.improvedchat.model.BorderThickness chatboxBorderThickness() { return com.improvedchat.model.BorderThickness.NORMAL; }

    @ConfigItem(keyName = "chatboxBorderRgb", name = "RGB border", description = "Animate a rainbow around the regular chatbox border", section = chatboxSection, position = 31)
    default boolean chatboxBorderRgb() { return false; }

    @Range(min = 2, max = 30)
    @Units(Units.SECONDS)
    @ConfigItem(keyName = "chatboxBorderRgbSeconds", name = "Border RGB cycle", description = "Seconds for one complete rainbow cycle", section = chatboxSection, position = 32)
    default int chatboxBorderRgbSeconds() { return 8; }

    @ConfigItem(keyName = "chatboxBorderRounded", name = "Rounded border", description = "Round the border and native chat background together", section = chatboxSection, position = 33)
    default boolean chatboxBorderRounded() { return false; }

    @ConfigItem(keyName = "enableRemoveChatOptions", name = "Remove Chat Options", description = "Remove chat-message context-menu options; hold Control to temporarily show them", section = cleanupSection, position = 22)
    default boolean enableRemoveChatOptions() { return false; }

    @ConfigItem(keyName = "removeLookupChatOption", name = "Remove Lookup", description = "Also remove the player Lookup entry from chat-message menus", section = cleanupSection, position = 23)
    default boolean removeLookupChatOption() { return false; }

    @ConfigItem(keyName = "enableOfflineChatStatus", name = "Grey offline clan names", description = "Grey out offline clan member names in native clan chat", section = cleanupSection, position = 24)
    default boolean enableOfflineChatStatus() { return false; }

    @ConfigItem(keyName = "enableOfflineIcon", name = "Show offline icon", description = "Add an icon beside offline clan names. Requires Grey offline clan names; turning this off keeps the grey styling.", section = cleanupSection, position = 25)
    default boolean enableOfflineIcon() { return true; }

    @ConfigItem(hidden = true, keyName = "enableOfflineColor", name = "Color Offline Names", description = "Recolor offline clan member names", section = cleanupSection, position = 26)
    default boolean enableOfflineColor() { return true; }

    @Alpha
    @ConfigItem(hidden = true, keyName = "offlineColor", name = "Offline Color", description = "Name color for offline clan members", section = cleanupSection, position = 27)
    default Color offlineColor() { return Color.DARK_GRAY; }

    @ConfigItem(keyName = "enableDialogueFonts", name = "Enable Dialogue Text Styling", description = "Customize supported dialogue text with configurable fonts and readability controls", section = dialogueSection, position = 0)
    default boolean enableDialogueFonts() { return false; }

    @ConfigItem(keyName = "fontFamily", name = "Font", description = "Font used for dialogue text", section = dialogueSection, position = 1)
    default FontChoice fontFamily() { return FontChoice.SANS_SERIF; }

    @Range(min = 10, max = 24)
    @ConfigItem(keyName = "fontSize", name = "Font Size", description = "Dialogue text size in pixels", section = dialogueSection, position = 2)
    default int fontSize() { return 14; }

    @ConfigItem(keyName = "boldText", name = "Bold", description = "Use bold dialogue text", section = dialogueSection, position = 3)
    default boolean boldText() { return false; }

    @ConfigItem(keyName = "antiAlias", name = "Anti-aliasing", description = "Smooth dialogue font edges", section = dialogueSection, position = 4)
    default boolean antiAlias() { return true; }

    @Range(min = 10, max = 24)
    @ConfigItem(keyName = "dialogueOptionFontSize", name = "Option Font Size", description = "Separate font size for dialogue option rows", section = dialogueSection, position = 5)
    default int dialogueOptionFontSize() { return 13; }

    @Range(min = -2, max = 8)
    @Units(Units.PIXELS)
    @ConfigItem(keyName = "dialogueLineSpacing", name = "Line Spacing", description = "Extra spacing between wrapped dialogue lines", section = dialogueSection, position = 6)
    default int dialogueLineSpacing() { return 0; }

    @ConfigItem(keyName = "dialogueTextShadow", name = "Text Shadow", description = "Draw a subtle shadow behind replacement dialogue text", section = dialogueSection, position = 7)
    default boolean dialogueTextShadow() { return false; }

    @Alpha
    @ConfigItem(keyName = "dialogueShadowColor", name = "Shadow Color", description = "Color and opacity of the optional dialogue text shadow", section = dialogueSection, position = 8)
    default Color dialogueShadowColor() { return new Color(0, 0, 0, 150); }

    @ConfigItem(keyName = "replaceNpc", name = "NPC Dialogue", description = "Replace NPC dialogue text", section = dialogueSection, position = 9)
    default boolean replaceNpc() { return true; }

    @ConfigItem(keyName = "replacePlayer", name = "Player Dialogue", description = "Replace player dialogue text", section = dialogueSection, position = 10)
    default boolean replacePlayer() { return true; }

    @ConfigItem(keyName = "replaceOptions", name = "Option Menus", description = "Replace dialogue option-menu text while preserving 1–5 keyboard selection", section = dialogueSection, position = 11)
    default boolean replaceOptions() { return true; }

    @ConfigItem(keyName = "replaceSprite", name = "Item/Action Dialogue", description = "Replace item and action dialogue text", section = dialogueSection, position = 12)
    default boolean replaceSprite() { return true; }

}
