package com.improvedchat.chatbox.offline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

@Singleton
public final class ClanChatPresentationService
{
    private static final int NO_ICON = -1;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private ChatIconManager chatIconManager;

    private final Map<String, Integer> rankIconCache = new ConcurrentHashMap<>();
    private boolean started;
    private int offlineIconHandle = NO_ICON;

    public synchronized void startUp()
    {
        if (started)
        {
            return;
        }

        started = true;
        eventBus.register(this);
        clientThread.invokeLater(() ->
        {
            ensureOfflineIconRegistered();
            cacheOnlineRanks();
        });
    }

    public synchronized void shutDown()
    {
        if (!started)
        {
            return;
        }

        started = false;
        eventBus.unregister(this);
        rankIconCache.clear();
    }

    public Presentation resolve(String sender, ChatMessageType type)
    {
        ClanContext context = contextFor(type);
        if (context == null || sender == null || sender.isEmpty())
        {
            return Presentation.notClan();
        }

        String cleanName = Text.removeTags(sender).trim();
        if (cleanName.isEmpty())
        {
            return Presentation.notClan();
        }

        ClanChannel channel = context.channel();
        ClanSettings settings = context.settings();
        ClanChannelMember member = channel == null ? null : channel.findMember(cleanName);

        int rankIcon = cachedRankIcon(context.cachePrefix, cleanName);
        if (member != null)
        {
            int resolved = resolveRankIcon(member, settings);
            if (resolved >= 0)
            {
                rankIcon = resolved;
                rankIconCache.put(cacheKey(context.cachePrefix, cleanName), resolved);
            }
        }

        boolean statusKnown = channel != null;
        boolean offline = statusKnown && member == null;
        return new Presentation(true, statusKnown, offline, rankIcon, getOfflineIconIndex());
    }

    public int getOfflineIconIndex()
    {
        if (offlineIconHandle < 0)
        {
            return NO_ICON;
        }
        return chatIconManager.chatIconIndex(offlineIconHandle);
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
        cacheMember(event.getClanChannel(), event.getClanMember());
    }

    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {

        cacheMember(event.getClanChannel(), event.getClanMember());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(this::cacheOnlineRanks);
        }
    }

    private void ensureOfflineIconRegistered()
    {
        if (offlineIconHandle >= 0)
        {
            return;
        }
        offlineIconHandle = chatIconManager.registerChatIcon(createOfflineIconImage());
    }

    private void cacheOnlineRanks()
    {
        cacheChannel("clan", client.getClanChannel(), client.getClanSettings());
        cacheChannel("guest", client.getGuestClanChannel(), client.getGuestClanSettings());
        cacheChannel("gim", client.getClanChannel(ClanID.GROUP_IRONMAN),
            client.getClanSettings(ClanID.GROUP_IRONMAN));
    }

    private void cacheChannel(String prefix, ClanChannel channel, ClanSettings settings)
    {
        if (channel == null || settings == null)
        {
            return;
        }

        for (ClanChannelMember member : channel.getMembers())
        {
            int icon = resolveRankIcon(member, settings);
            if (icon >= 0)
            {
                rankIconCache.put(cacheKey(prefix, member.getName()), icon);
            }
        }
    }

    private void cacheMember(ClanChannel channel, ClanChannelMember member)
    {
        if (channel == null || member == null)
        {
            return;
        }

        String prefix;
        ClanSettings settings;
        if (channel == client.getClanChannel())
        {
            prefix = "clan";
            settings = client.getClanSettings();
        }
        else if (channel == client.getGuestClanChannel())
        {
            prefix = "guest";
            settings = client.getGuestClanSettings();
        }
        else if (channel == client.getClanChannel(ClanID.GROUP_IRONMAN))
        {
            prefix = "gim";
            settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
        }
        else
        {
            return;
        }

        int icon = resolveRankIcon(member, settings);
        if (icon >= 0)
        {
            rankIconCache.put(cacheKey(prefix, member.getName()), icon);
        }
    }

    private int resolveRankIcon(ClanChannelMember member, ClanSettings settings)
    {
        if (member == null || settings == null || member.getRank() == null)
        {
            return NO_ICON;
        }

        ClanTitle title = settings.titleForRank(member.getRank());
        return title == null ? NO_ICON : chatIconManager.getIconNumber(title);
    }

    private int cachedRankIcon(String prefix, String name)
    {
        Integer icon = rankIconCache.get(cacheKey(prefix, name));
        return icon == null ? NO_ICON : icon;
    }

    private static String cacheKey(String prefix, String name)
    {
        return prefix + ":" + Text.standardize(Text.removeTags(name == null ? "" : name));
    }

    private ClanContext contextFor(ChatMessageType type)
    {
        if (type == ChatMessageType.CLAN_CHAT)
        {
            return new ClanContext("clan", client.getClanChannel(), client.getClanSettings());
        }
        if (type == ChatMessageType.CLAN_GUEST_CHAT)
        {
            return new ClanContext("guest", client.getGuestClanChannel(), client.getGuestClanSettings());
        }
        if (type == ChatMessageType.CLAN_GIM_CHAT)
        {
            return new ClanContext("gim", client.getClanChannel(ClanID.GROUP_IRONMAN),
                client.getClanSettings(ClanID.GROUP_IRONMAN));
        }
        return null;
    }

    static BufferedImage createOfflineIconImage()
    {
        BufferedImage image = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {

            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(new Color(150, 150, 150, 255));
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawOval(1, 1, 8, 8);
            graphics.drawLine(3, 3, 8, 8);
        }
        finally
        {
            graphics.dispose();
        }
        return image;
    }

    private static final class ClanContext
    {
        private final String cachePrefix;
        private final ClanChannel channel;
        private final ClanSettings settings;

        private ClanContext(String cachePrefix, ClanChannel channel, ClanSettings settings)
        {
            this.cachePrefix = cachePrefix;
            this.channel = channel;
            this.settings = settings;
        }

        private ClanChannel channel()
        {
            return channel;
        }

        private ClanSettings settings()
        {
            return settings;
        }
    }

    public static final class Presentation
    {
        private final boolean clanMessage;
        private final boolean statusKnown;
        private final boolean offline;
        private final int rankIconId;
        private final int offlineIconId;

        private Presentation(boolean clanMessage, boolean statusKnown, boolean offline,
            int rankIconId, int offlineIconId)
        {
            this.clanMessage = clanMessage;
            this.statusKnown = statusKnown;
            this.offline = offline;
            this.rankIconId = rankIconId;
            this.offlineIconId = offlineIconId;
        }

        private static Presentation notClan()
        {
            return new Presentation(false, false, false, NO_ICON, NO_ICON);
        }

        public boolean isClanMessage()
        {
            return clanMessage;
        }

        public boolean isStatusKnown()
        {
            return statusKnown;
        }

        public boolean isOffline()
        {
            return offline;
        }

        public int getRankIconId()
        {
            return rankIconId;
        }

        public int getOfflineIconId()
        {
            return offlineIconId;
        }
    }
}
