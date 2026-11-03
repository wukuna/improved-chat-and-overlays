package com.improvedchat.overlay;

import com.improvedchat.ChatColorSource;
import java.awt.Color;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ChatColorConfig;

final class OverlayChatColors {
    private OverlayChatColors() { }

    static ChatColorSource resolveSource(ChatColorSource source, Client client) {
        if (source != null && source != ChatColorSource.AUTOMATIC) return source;
        return client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) != 0
                ? ChatColorSource.TRANSPARENT : ChatColorSource.OPAQUE;
    }

    static Color sender(ChatMessageType type, ChatColorConfig colors, ChatColorSource source) {
        if (colors == null || source == ChatColorSource.WIDGET) return Color.WHITE;
        boolean opaque = source == ChatColorSource.OPAQUE;
        Color color;
        switch (type) {
            case CLAN_CHAT: case CLAN_GIM_CHAT:
                color = opaque ? colors.opaqueClanChatUsernames() : colors.transparentClanChatUsernames(); break;
            case CLAN_GUEST_CHAT:
                color = opaque ? colors.opaqueClanChatGuestUsernames() : colors.transparentClanChatGuestUsernames(); break;
            case FRIENDSCHAT:
                color = opaque ? colors.opaqueFriendsChatUsernames() : colors.transparentFriendsChatUsernames(); break;
            case PRIVATECHAT: case MODPRIVATECHAT: case PRIVATECHATOUT:
                color = opaque ? colors.opaquePrivateUsernames() : colors.transparentPrivateUsernames(); break;
            default:
                color = opaque ? colors.opaqueUsername() : colors.transparentUsername();
        }
        return color == null ? Color.WHITE : color;
    }

    static Color channel(ChatMessageType type, ChatColorConfig colors, ChatColorSource source) {
        Color color = null;
        if (colors != null && source != ChatColorSource.WIDGET) {
            boolean opaque = source == ChatColorSource.OPAQUE;
            switch (type) {
                case CLAN_CHAT: case CLAN_GIM_CHAT:
                    color = opaque ? colors.opaqueClanChannelName() : colors.transparentClanChannelName(); break;
                case CLAN_GUEST_CHAT:
                    color = opaque ? colors.opaqueClanGuestChatChannelName() : colors.transparentClanChannelGuestName(); break;
                case FRIENDSCHAT:
                    color = opaque ? colors.opaqueFriendsChatChannelName() : colors.transparentFriendsChatChannelName(); break;
                default: break;
            }
        }
        return color == null ? new Color(144, 144, 255) : color;
    }
}
