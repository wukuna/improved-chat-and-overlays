package com.improvedchat.input;

import com.improvedchat.ImprovedChatConfig;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanID;

public enum ChatInputMode {
    PUBLIC("Public", 0), CLAN("Clan", 2), CHANNEL("Channel", 1), GIM("GIM", 4);

    public final String label;
    public final int value;
    ChatInputMode(String label, int value) { this.label = label; this.value = value; }

    public boolean selected(ImprovedChatConfig config) {
        switch (this) {
            case PUBLIC: return config.inputDockPublic();
            case CLAN: return config.inputDockClan();
            case CHANNEL: return config.inputDockChannel();
            default: return config.inputDockGim();
        }
    }

    public boolean available(Client client) {
        switch (this) {
            case CLAN: return client.getClanChannel(ClanID.CLAN) != null;
            case CHANNEL: return client.getFriendsChatManager() != null;
            case GIM: return client.getClanChannel(ClanID.GROUP_IRONMAN) != null;
            default: return true;
        }
    }
}
