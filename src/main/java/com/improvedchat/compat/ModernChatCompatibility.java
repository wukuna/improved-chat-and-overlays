package com.improvedchat.compat;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

@Singleton
public final class ModernChatCompatibility
{
    static final String MODERN_CHAT_CLASS = "com.modernchat.ModernChatPlugin";
    static final String MODERN_CHAT_GROUP = "modernchat";
    static final String REDESIGN_KEY = "featureRedesign_Enabled";
    static final String TOGGLE_KEY = "featureToggle_Enabled";
    private static final String RUNELITE_GROUP = "runelite";

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ConfigManager configManager;

    public ModernChatOwnership snapshot()
    {
        Plugin plugin = findModernChatPlugin();
        boolean active = plugin != null && pluginManager.isPluginActive(plugin);
        return ownership(active);
    }

    public ModernChatOwnership startupSnapshot()
    {
        Plugin plugin = findModernChatPlugin();
        boolean activeOrStarting = plugin != null
            && (pluginManager.isPluginActive(plugin) || pluginManager.isPluginEnabled(plugin));
        return ownership(activeOrStarting);
    }

    public boolean isModernChatActive()
    {
        Plugin plugin = findModernChatPlugin();
        return plugin != null && pluginManager.isPluginActive(plugin);
    }

    public boolean isModernChatPlugin(Plugin plugin)
    {
        return plugin != null && MODERN_CHAT_CLASS.equals(plugin.getClass().getName());
    }

    public boolean isOwnershipConfig(String group, String key)
    {
        return MODERN_CHAT_GROUP.equals(group) && (isRedesignKey(key) || isToggleKey(key));
    }

    public boolean isRedesignKey(String key)
    {
        return REDESIGN_KEY.equals(key);
    }

    public boolean isToggleKey(String key)
    {
        return TOGGLE_KEY.equals(key);
    }

    public boolean isModernChatEnableConfig(String group, String key)
    {
        if (!RUNELITE_GROUP.equals(group) || key == null)
        {
            return false;
        }

        Plugin plugin = findModernChatPlugin();
        if (plugin == null)
        {
            return false;
        }

        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null)
        {
            return false;
        }

        String configName = descriptor.configName();
        if (configName == null || configName.isEmpty())
        {
            configName = plugin.getClass().getSimpleName();
        }
        return configName.toLowerCase(Locale.ROOT).equals(key);
    }

    private ModernChatOwnership ownership(boolean active)
    {
        return new ModernChatOwnership(
            active,
            readBoolean(configManager.getConfiguration(MODERN_CHAT_GROUP, REDESIGN_KEY), true),
            readBoolean(configManager.getConfiguration(MODERN_CHAT_GROUP, TOGGLE_KEY), true));
    }

    static boolean readBoolean(String value, boolean defaultValue)
    {
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private Plugin findModernChatPlugin()
    {
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (isModernChatPlugin(plugin))
            {
                return plugin;
            }
        }
        return null;
    }
}
