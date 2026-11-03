# Improved Chat and Overlays

Give each conversation its own space. Create chat overlays with independent channels, filters, fonts, colours and alerts, including optional RGB text and borders. Resize or collapse the native chatbox, tidy channel labels and menus, distinguish offline clan members, and make dialogue easier to read. Enable only the features you want.

[Features](#features) · [Getting started](#getting-started) · [Usage](#usage) · [Saved settings](#saved-settings) · [Compatibility](#compatibility) · [Inspiration and recipe](#inspiration-and-recipe) · [Development](#development) · [Support](#support) · [License and attribution](#license-and-attribution)

## Features

- **Recent updates:** a compact bulleted list of changes appears in the chatbox once per update in each RuneLite profile.
- **Custom chat overlays** with independent message types, placement, size, fonts, colors, borders, timestamps, fade behavior, attention effects, and input preview.
- **Overlay RGB effects (Beta)** together under **Style > RGB & Effects**: one-click **RGB for all messages**, matching-message effects, border animation and shared timing. Static RGB, two-color or full-spectrum cycling, and a wave are available. Rules are preserved when using the all-message shortcut; enable **Allow rule overrides** to let matches override it.
- **Rounded corners** under **Style > Frame** round both the background and border, including animated RGB and flashing borders. Hover controls for brief explanations.
- **Message replacer (Beta)** with multiple independently enabled rules that show your own text in an overlay while leaving the original chat message unchanged.
- **Per-overlay chat filtering** with Off, Hide Matches, Show Only Matches, word, regex, and sender/name rules.
- **Clan presentation in overlays** with rank icons plus independently configurable offline status, icon, name color, and offline color.
- **Native chat resizing** in resizable and fixed layouts, including drag resizing, private-chat rewrapping, secondary sizes, and keybind control.
- **Collapsible native chat** with a compact single-button mode, unread indicators, hover text, and a collapse keybind.
- **Message color rules and alerts** with literal or regex matching, nine configurable rule colors, flash triggers, and overlay-only rainbow styling.
- **Native chat cleanup** for timestamps, channel labels, ranks, scrollbars, channel color bars, startup/reconnect messages, chat menus, and clan/friends/GIM presentation.
- **Offline clan status** for native chat and overlays.
- **Chatbox opacity controls** for the native chatbox, buttons, dialogue, and menus.
- **Dialogue text styling** for NPC, player, option-menu, and item/action dialogue.
- **Compatibility safeguards** for RuneLite Chat Color, Chat Filter, Emojis, Resource Packs, and Modern Chat.

**Availability:** Install **Improved Chat and Overlays** from RuneLite's Plugin Hub. Experimental overlay effects and message replacement are marked **Beta** in their settings.

**Regular chatbox border:** open the plugin settings → **Chatbox** → **Chatbox border**. It starts off. Choose a solid color and opacity, thickness, optional RGB with cycle speed, and rounded corners. The frame follows the visible native chat area in fixed and resizable layouts, including chat resizing; it disappears when chat is collapsed, hidden or replaced by a dialogue. Rounded corners clip the native background to the same curve as the solid or RGB frame, preserving its color, texture and opacity. Custom overlay borders remain independent.

**More plugins:** [Banking, Looting, Right-Clicks, Interface Layout and Chat](https://github.com/wukuna#runelite-plugins).

## Getting started

1. Use the normal plugin settings for native chatbox size, collapse controls, cleanup, opacity and dialogue styling.
2. Open **Create** in the side panel to start with a preset or a custom overlay.
3. Click an overlay name under **Overlays** to open its resizable editor window. Use **Messages**, **Style** and **Alerts**, then try **Preview** while logged in.

Previously **Improved Chat**. Existing settings, overlay positions and profiles are retained.

## Usage

### Custom overlays

Create and manage multiple overlays from the **Improved Chat and Overlays** side panel.

Start with a preset, then turn on **Enabled**, choose **Message Types**, and set **Show overlay** in the **Messages** tab. **Show overlay** selects whether the overlay appears only with the chatbox hidden or with it open too. New custom overlays have no message types selected until you choose them. An overlay appears only when it has messages or an input preview; choosing “With chatbox open or hidden” does not draw an empty frame.

Use **Preview** while logged in to check appearance for 10 seconds without waiting for chat. Preview bypasses content filters and normal visibility conditions; its sample messages never enter chat history. Use **Settings** for native chat features and global defaults.

The sidebar has three simple tabs: **Overlays**, **Create** and **Tools**. Click an overlay name to open its separate editor window, then use **Messages**, **Style**, or **Alerts** inside it. Opening another overlay reuses the window; each overlay remembers its selected tab. **Tools → Chatbox settings** opens the regular chatbox options. The header has no introductory paragraph; a compact **Settings notice…** button appears only when settings need attention. Each overlay has a direct enable switch. Changes save automatically as you edit. Expand a section for its controls; hover a setting for brief help. Style puts text and colors first, followed by frame, effects, position and layout. The editor supports duplicating an overlay, confirms deletion, and preserves your scroll position when dependent controls change. Deleting every overlay stays empty after restart. Creating Game Alerts uses the same game-message selection as the first-run preset; existing saved selections are preserved.

Turn off **Messages → Show channel names** to remove the bracketed clan/friends-chat channel prefix from that overlay. Sender names, rank icons and messages remain available. Existing overlays inherit the global channel-name preference until you change this checkbox. **Style → Text Size → Small** works with every offered font, including bold styles.

**Clear [overlay] history** clears only that overlay's view. Native chat-tab history clearing still applies to all overlays showing those message types. **Collapse Duplicates** groups consecutive matching messages within each overlay's filtered view and keeps channels, senders, and incoming/outgoing messages separate. Captured history remains intact so clearing or filtering one overlay cannot remove another overlay's messages. History is held for the current plugin session.

### Visibility hotkeys

Set **Messages → Messages & Visibility → Toggle hotkey** in each overlay's editor. Click the field and press a combination; Escape clears it. One press toggles every overlay assigned to that combination, including disabled overlays. The Enabled state is saved with the overlay; its **Show overlay** condition still applies. Holding a key does not repeat the toggle.

In regular chatbox settings, **Hide entire chatbox hotkey** hides the chat area and all chat buttons, including All and Report. Press it again to restore their previous state. This is separate from **Collapse Chat Keybind**, works without enabling collapse, and leaves dialogue prompts usable. Full hiding resets on logout or profile change; clearing its binding or disabling the plugin also restores it. Modern Chat's visibility controls take precedence. Use distinct keys for collapse, resizing and full hiding.

### Scrollable session history

In an overlay's **Messages → Session History** section, enable **Scroll history**. Use the mouse wheel over the visible overlay to read older lines, including long messages that wrap. New messages do not move your place while browsing. Scroll to the bottom or right-click the overlay and choose **Latest** to follow new messages again. History uses the available message rows without an instructional footer.

**History messages** defaults to 1,000 and can be set from 200 to 5,000 per overlay, drawn from a shared pool of the last 5,000 captured messages. Your selected channels and filters still apply. **Visible lines** controls the number of lines shown at once; the frame keeps a steady height and messages remain readable without fading. The input preview stays at the bottom while history scrolls above it. Scroll history starts disabled, so existing fading overlays retain their behavior.

History survives world hops and login/logout while the plugin remains enabled. It starts with messages captured by this plugin and ends when the plugin stops or RuneLite closes; nothing is written to chat log files. Clearing an overlay still affects only that overlay, and clearing a native chat tab clears the corresponding captured messages. Mouse-wheel input outside visible history overlays and modified scrolling (Alt/Ctrl/Shift) keep their normal behavior.

Each overlay can independently control:

- enabled state, name, maximum messages, and fade duration;
- message types shown;
- free placement or placement above/below your character;
- X/Y offsets for player-attached overlays;
- text size, font, alignment, bold styling, and width;
- horizontal/vertical padding inside the border, with at least 3 pixels of clearance for borders and rounded corners; full-height text rows keep letters, icons and shadows clear of the frame;
- background color and transparency;
- border color and thickness;
- timestamps: enable **Style → Text → Timestamps**, then choose **Timestamp style → Time ago (20m)** for ages such as `01s`, `30s`, `20m`, `02h` or `99h`. A fixed pixel column keeps messages aligned as ages update, including with proportional fonts, animated colors and session history. At 100 hours the timestamp disappears while its space remains reserved. **Clock time** keeps the existing configured time format. Each overlay saves its own choice;
- dynamic height and always-visible behavior;
- duplicate-count display;
- input preview and "only while typing" behavior; long input scrolls behind your fixed username to keep the newest text and typing marker visible, adapting as you delete text or resize the overlay;
- overlay-only text-color override;
- attention effects for the message, border, and background.

RuneLite's normal overlay movement system is used for free-position overlays.

#### RGB animation (Beta)

In each overlay's **Style → Frame**, enable **Border** and **RGB border (Beta)** for a rainbow that travels around the perimeter. Under **Effects**, **Text effect** offers solid/channel colors, **RGB Static** by word or character, **RGB Cycling (Beta)**, and **RGB Wave (Beta)**. Cycling can fade between **Color 1** and **Color 2**, or use the full spectrum. Wave uses all colors. **Cycle (seconds)** sets the duration; higher values are slower. Border and text effects are independent and off by default.

Animation affects only the custom overlay, including its names, timestamps and input preview. Migrated overlays preserve their former precedence. **Messages → Message highlights → Allow rule overrides** lets a matching rule override the message body while unmatched messages retain the default effect. Icons retain their original colors. Wrapping, shadows, underline/strikethrough styling, message fading and attention flashes remain available. Border Color retains its transparency setting when RGB is enabled. Use **Preview** while logged in to try the effects. These Beta effects still need live gameplay feedback.

> Earlier overlay list and editor, before the current list-and-tabs layout. The settings instructions above describe the current release.
<img width="276" height="1305" alt="Screenshot 2026-10-03 185108" src="https://github.com/user-attachments/assets/39583041-3ec1-4c62-849f-cceadeef0761" />

> Earlier editor walkthrough: creating an overlay, selecting message types, moving it, and changing its appearance. The current editor groups these controls into tabs.

https://github.com/user-attachments/assets/95c6ba06-64a6-435d-8fb8-ad3857b9e662

### Overlay filtering

#### Message replacer (Beta)

In an overlay's **Messages → Message replacer (Beta)** section, enable the feature and choose **Add replacement** for each message you want to customize. Each rule has **Original message**, **Show instead**, and its own enable checkbox. Edits save automatically. For example, match `Your thrall returns to the grave.` and show `You need to summon a thrall!`.

Rules match the complete message, ignoring case and chat colors. The first enabled, complete rule wins. Replacement text is displayed literally and never triggers another replacement. Blank or disabled rules leave messages unchanged. **Test replacements** previews the result without logging in or adding anything to chat.

Replacement affects only that overlay's displayed message body. The real chatbox, captured history, filters, message highlights and notification triggers still use the original message. Sender labels, timestamps, duplicate counts, wrapping and fading remain available. The feature is off by default and remains Beta pending live feedback.

Filtering can be global or completely independent per overlay.

- **Use Chat Filter Globally** applies the RuneLite global Chat Filter path to every overlay.
- **Use Global Chat Filter** applies that global filter only to the selected overlay.
- Otherwise, each overlay can use:
  - **Off**
  - **Hide Matches**
  - **Show Only Matches**
- Custom filters support:
  - filtered words;
  - case-insensitive regex rules;
  - sender/name rules;
  - literal sender matching;
  - sender regex rules using a `regex:` prefix.

Invalid custom regex lines are ignored safely.

The editor reports invalid filter line numbers as you edit and warns when Show Only Matches has no usable rules. Use the sample player-name and message fields to test custom content rules. Literal names match part of a name; use `regex:^Alice$` for an exact name. The tester does not test channel selection or visibility.

When the global override is active, custom controls are disabled but their saved values are retained. The editor also reports when RuneLite's Chat Filter plugin is disabled, in which case selecting the global filter does not apply content filtering.

Different overlays can therefore show different subsets of the same shared chat history without removing messages from the shared message pool.

### Clan display and offline status

Clan presentation can be configured separately for every overlay.

Per-overlay controls include:

- **Show Clan Rank Icons**
- **Show Offline Status**
- **Show Offline Icon**
- **Color Offline Names**
- **Offline Color**

Clan, guest-clan, and Group Ironman chat are supported.

Overlay offline presentation is independent of the native-chat **Offline Clan Status** setting, so one can be enabled without the other.

Native offline status adds an icon; native name colors remain under RuneLite Chat Color. Custom offline name colors remain available in each overlay.

### Message colors, rules, and alerts

Improved Chat and Overlays can use RuneLite Chat Color as its baseline color source or configured fallback colors for game, public, private, friends, clan, guest clan, Group Ironman, trade, challenge, broadcast, autochat, and other supported message types.

Open **Style → Text colors → Color palette**. **RuneLite (automatic)** follows the current chatbox mode; **RuneLite transparent** and **RuneLite opaque** stay on the selected palette. The palette supplies message colors, highlights, sender names and channel labels. **Overlay palette** uses the **Palette colors** values. Keep **Retain message colors** enabled and **Text Override** off to use a palette; inactive palette controls are disabled without losing their saved choices. Explicit offline-name colors, highlight rules and RGB effects retain their configured precedence. RuneLite's own settings are read, never changed.

#### Message Color Rules

Rules affect only the selected overlay. Open **Messages → Message highlights** to edit its definitions and nine-color palette. Native chat text uses RuneLite Chat Color.

Use one rule per line:

```text
is about to expire::1
has expired::1::flash
regex:^Your .* count is:.*::2
```

- `::1` through `::9` select one of nine configurable rule colors.
- Prefix a rule with `regex:` for a case-insensitive regular expression.
- Add `::flash` to trigger overlay attention behavior.
- Player-authored chat is excluded by default and can be enabled separately.

#### Overlay Rainbow Rules

RGB highlight patterns affect the selected overlay only. Choose Use overlay default, RGB Static (by word or character), RGB Cycling, or RGB Wave. Existing rainbow patterns retain their static style and word/character selection.

```text
achieved a new
has reached
regex:^Collection log.*
```

#### Overlay attention controls

Each overlay can independently configure:

- whether attention flashing is enabled;
- what triggers it;
- whether it can flash while RuneLite is focused;
- stop behavior;
- duration;
- speed;
- message flashing;
- border flashing;
- background flashing.

### Native chat resizing

Improved Chat and Overlays can resize RuneLite's native chatbox in both **resizable** and **fixed** layouts.

Available controls include:

- resizable height and width changes;
- fixed-layout height changes;
- private split-chat width adjustment and live rewrapping;
- optional resizing of chat-tab buttons;
- interface-height growth when the chatbox grows;
- camera adjustment in fixed mode when growing chat;
- hideable fixed chat;
- automatic reversion for dialogue and interface/modal screens;
- Resource Pack compatibility controls to avoid drawing resize borders or stretching custom chatbox artwork.

#### Drag resizing

Set a **Drag-Resize Modifier** and hold it while dragging a chat border.

Drag resizing also supports:

- live rewrapping;
- a configurable drag indicator color.

#### Secondary chat size

Configure a second chat size and switch to it with a keybind.

The secondary size supports its own height/width changes and hold/toggle behavior.

### Collapsible chat

The native chatbox can collapse to a single compact button.

Controls include:

- collapse/restore keybind;
- static button text or Report-button text;
- transparent button mode;
- configurable button and hover text;
- unread colors for public, private, friends chat, clan, and trade activity.

### Native chat cleanup and presentation

Cleanup options work independently and can be enabled only where wanted.

#### General cleanup

- remove the welcome message;
- wrapped-message indentation controls;
- hide the chat scrollbar;
- remove special-attack chat text;
- fixed-width timestamps with a reserved pixel column for consistent alignment.

#### Channel color bar

Add an optional per-message channel color marker with configurable offset and width.

Separate colors can be configured for:

- no-channel messages;
- friends chat;
- clan;
- guest clan;
- Group Ironman.

#### Clan cleanup

Controls include:

- remove clan startup messages;
- remove or replace the clan name;
- remove clan rank icons.

#### Guest clan cleanup

Controls include:

- remove reconnecting messages;
- remove or replace the guest-clan name.

#### Group Ironman cleanup

Controls include:

- remove or replace the Group Ironman name;
- move Group Ironman broadcasts.

#### Friends Chat cleanup

Controls include:

- remove or replace the Friends Chat name;
- remove "Attempting to join" messages;
- remove "Now talking in" messages.

#### Chat menu cleanup

**Remove Chat Options** simplifies right-click menus on chat messages.

An optional **Remove Lookup** setting can remove the player Lookup entry as well. Holding Control provides temporary access to the normal options.

### Chatbox opacity

Native-chat opacity controls include:

- chatbox opacity;
- chat-button opacity;
- optional opacity for dialogue and menus shown in the chat area.

### Dialogue text styling

Customize supported RuneLite/OSRS dialogue text with:

- font family;
- font size;
- bold text;
- anti-aliasing;
- option-menu font size;
- line spacing;
- shadow color.

Styling can be enabled independently for:

- NPC dialogue;
- player dialogue;
- option menus;
- item/action dialogue.

### Per-overlay notifications

Each overlay has an **Alerts** tab in its editor. Notifications start disabled. Enable them for all new matching messages or only messages matching a `::flash` color rule. Overlays switched off with **Enabled**, filtered messages, outgoing messages and RuneLite console notifications do not trigger alerts. Notifications follow the **Enabled** switch; an enabled overlay can still notify while its **Show overlay** visibility condition temporarily hides it. Repainting, loading history and delayed message formatting do not replay notifications. Each enabled matching overlay sends its own alert.

Use RuneLite defaults, or enable **Customize Notification** for independent desktop notifications, Native/Custom/Off sound, volume, focus behavior, window flashing and color, timeout, and chatbox notification. Custom sounds use a WAV filename in RuneLite's notifications folder; blank uses its default custom sound. Playback and operating-system notifications are handled by RuneLite's shared Notifier, as with Idle Notifier. These controls are independent of the existing overlay Attention flashing.

Native private-chat hiding keeps the system-update timer visible. Overlay login/logout messages respect the game's Off setting. Game and clan payload prefixes are removed only for their native message types; literal pipes in player chat stay intact. Overlay text supports native underline, strike, shadow, line-break and quest-name template formatting.

## Saved settings

Custom text rules have safety limits to keep chat responsive. Expressions may contain up to 1,024 characters, with up to 128 rules per list and 32,768 characters per rule source. Matching stops when a rule exceeds its work budget; the rule is skipped until it is recompiled after an edit. If an overlay filter cannot be evaluated safely, its messages remain visible. The side panel’s **Settings notice…** explains when a limit was reached, and saved rule text is retained. These limits apply to Improved Chat's evaluation, not other plugins' matching engines.

Saved overlay lists are limited to 64 overlays and two million JSON characters, with a nesting limit checked before parsing. Unreadable bounded configurations retain a recovery backup. Oversized configurations remain in their original setting and automatic saving pauses; export the RuneLite profile before using the existing Reset overlays action to deliberately replace them.

The native plugin menu has five collapsible sections: **Chatbox**, **Chat cleanup**, **Overlay setup: side panel**, **Dialogue text**, and **Advanced**. Secondary sizing and its hotkey remain in Chatbox. Clan, guest-clan, friends-chat and GIM name controls remain independent of their instruction controls.

Use the side panel for individual overlays, custom text colors, RGB effects, notifications and Message highlights. Existing setting keys, overlay IDs, placements, filters and notification settings are retained. Former global color rules, their nine palette colors, RGB patterns, spread and fallback colors are copied into each existing overlay once. Old cycling remains full spectrum. The old overlay list is backed up under `overlayConfigsBeforeMenuCleanup`; legacy global values remain stored. New overlays have independent defaults. Unreadable configurations keep the existing recovery backup and are not overwritten merely by startup.

A one-time chatbox notice explains the rename. Collection Log keeps its native window handling.

## Compatibility

### RuneLite Chat Color, Chat Filter, and Emojis

Improved Chat and Overlays integrates with RuneLite's built-in chat systems rather than replacing them. Overlay rendering preserves supported emoji/icon tags, can use RuneLite Chat Color as a baseline, and can opt overlays into the RuneLite global Chat Filter path.

### Resource Packs

If a Resource Pack uses custom chatbox artwork, **Don't Draw Resize Borders** and **Don't Zoom Background** help prevent the resize system from framing or stretching that artwork.

### Modern Chat

Improved Chat and Overlays can run alongside **Modern Chat** by **BenDol**. When Modern Chat owns conflicting native-chat geometry, visibility, or presentation, Improved Chat and Overlays yields those conflicting native features while unrelated Improved Chat and Overlays features remain available.

### Chat Widgets and Force Recolor

Improved Chat and Overlays declares conflicts with **Chat Widgets** and **Force Recolor**. Use one plugin for each overlapping overlay or recolouring role. The recipe below explains their contributions and the features available here.

## Inspiration and recipe

The recipe is movable chat widgets, a flexible native chatbox and clear message presentation. Improved Chat and Overlays builds on work from Chat Widgets, Chat Resizer, Collapse Chat, ChatboxOpacity, Dialogue Fonts, Clean Chat, Remove Chat Options and Offline Chat Icon. Force Recolor also informed the familiar text::number colour-rule style. Each contribution is credited in the code notices.

| Project | Connection | What it contributes or complements |
| --- | --- | --- |
| [Chat Widgets](https://github.com/cnnnr/chat-widgets) | Adapted foundation | Independently configurable chat overlays. Credit to the project and retained original author 117. Declared conflict; choose one overlay system. |
| [Chat Resizer](https://github.com/shanktank/better-resizable-chat) | Adapted foundation | Chatbox sizing and private-message layout. Credit to Shanktank; use one controller for overlapping resize features. |
| [Collapse Chat](https://github.com/stutify/runelite-plugins/tree/e5136344122cd2c12390a288b83d3c087946cafd) | Adapted foundation | Single-button chat collapse. Credit to stutify; avoid enabling two collapse controllers. |
| [ChatboxOpacity](https://github.com/Trevor159/runelite-external-plugins/tree/1b998572d00db6aad78251ba2bf9fa1bae3b309a) | Adapted foundation | Chatbox transparency controls. Credit to Trevor; overlapping opacity settings can compete. |
| [Dialogue Fonts](https://github.com/theOranguzang/osrs-dialogue-fonts-plugin) | Adapted foundation | Readable dialogue fonts. Credit to theOranguzang; choose one dialogue-font renderer. |
| [Clean Chat](https://github.com/ldavid432/chat-cleanup) | Adapted foundation | Channel labels, presentation and cleanup. Credit to ldavid432 and the additional authors retained in adapted files. |
| [Remove Chat Options](https://github.com/96jonesa/remove-chat-options) | Adapted foundation | Chatbox menu cleanup. Credit to Andrew Jones; overlapping removal rules can hide options twice. |
| [Offline Chat Icon](https://github.com/fatfingers23/RuneliteOfflineChatIcon) | Adapted foundation | An offline indicator beside clan names. Credit to Bailey Townsend; avoid duplicate indicators. |
| [Force Recolor](https://github.com/Alexsuperfly/RuneLite-Hub-Plugins/tree/01fd487d13a0a66b061d60baea5b0f8b70d98e02) | Rule-design inspiration | Text matching with numbered colour groups. Our rule engine is independent. Declared conflict; native text colour stays under RuneLite Chat Color. |
| [Chat Color (built-in settings)](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ChatColorConfig.java) | Built-in companion | Base chat colours; overlays can use that palette or their own styling. |
| [Chat Filter (built in)](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/chatfilter) | Built-in companion | Global filtering. Each overlay can use that filter or its own content rules. |
| [Chat Notifications (built in)](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/chatnotifications) | Related alerts tool | Chat highlights and notifications. Overlapping triggers can produce duplicate alerts; keep only the rules you need. |
| [Emojis (built in)](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/emojis) | Built-in companion | Emoticon conversion; overlays preserve supported emoji/image tags. |
| [Resource Packs](https://github.com/melkypie/resource-packs) | Optional appearance tool | Chatbox artwork. The resize-border and background-zoom options help accommodate custom packs. |
| [Modern Chat](https://github.com/BenDol/Modern-Chat) | Alternative chat experience | A different chat redesign. Our compatibility guards yield conflicting native-chat features; unrelated overlays can remain active. |

These connections describe specific features, not a promise that every option is duplicated or every combination is interchangeable. Original copyright and license text is retained separately in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and packaged license files.

## Development

Use Java 11 and the repository's Gradle wrapper. Run `./gradlew test build` (Windows: `gradlew.bat test build`). Release commits are selected explicitly in the Plugin Hub manifest; the default branch and the Hub build can differ.

## Support

If something does not look or behave correctly, check whether another enabled plugin also changes RuneLite chat, chatbox geometry, opacity, gameframe artwork, or chat-message formatting.

When reporting a problem, include:

- the Improved Chat and Overlays feature being used;
- fixed or resizable RuneLite layout;
- steps to reproduce;
- a screenshot or short video for visual problems;
- other enabled chat/gameframe plugins;
- whether Modern Chat or a Resource Pack is enabled.

Report issues at:

https://github.com/wukuna/improved-chat-and-overlays/issues

## License and attribution

Improved Chat and Overlays is BSD 2-Clause licensed. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for license and attribution details.
