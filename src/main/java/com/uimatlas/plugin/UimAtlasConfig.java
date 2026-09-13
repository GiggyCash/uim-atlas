package com.uimatlas.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(UimAtlasConfig.GROUP)
public interface UimAtlasConfig extends Config
{
    String GROUP = "uimatlas";

    @ConfigItem(keyName = "showSidebar", name = "Show sidebar", description = "Show the UIM Atlas account summary")
    default boolean showSidebar()
    {
        return true;
    }
}
