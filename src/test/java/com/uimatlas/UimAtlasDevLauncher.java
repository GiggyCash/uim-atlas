package com.uimatlas;

import com.uimatlas.plugin.UimAtlasPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UimAtlasDevLauncher
{
    // RuneLite's loadBuiltin signature exposes a generic varargs array.
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(UimAtlasPlugin.class);
        RuneLite.main(args);
    }
}
