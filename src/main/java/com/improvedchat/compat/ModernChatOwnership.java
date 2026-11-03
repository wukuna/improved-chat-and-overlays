package com.improvedchat.compat;

public final class ModernChatOwnership
{
    private final boolean pluginActive;
    private final boolean redesignEnabled;
    private final boolean toggleEnabled;

    ModernChatOwnership(boolean pluginActive, boolean redesignEnabled, boolean toggleEnabled)
    {
        this.pluginActive = pluginActive;
        this.redesignEnabled = redesignEnabled;
        this.toggleEnabled = toggleEnabled;
    }

    public boolean isPluginActive()
    {
        return pluginActive;
    }

    public boolean blocksGeometry()
    {
        return pluginActive && redesignEnabled;
    }

    public boolean blocksVisibility()
    {
        return pluginActive && (redesignEnabled || toggleEnabled);
    }

    public boolean blocksNativePresentation()
    {
        return pluginActive && redesignEnabled;
    }
}
