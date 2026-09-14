package com.uimatlas.plugin;

import com.google.inject.Provides;
import com.uimatlas.state.AccountStateService;
import com.uimatlas.state.RuneLiteAccountObserver;
import com.uimatlas.ui.AccountSummary;
import com.uimatlas.ui.UimAtlasPanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(name = "UIM Atlas", description = "Observable account state for Ultimate Ironman planning",
    tags = {"ultimate", "ironman", "uim", "planning"})
public class UimAtlasPlugin extends Plugin
{
    @Inject private ClientToolbar toolbar;
    @Inject private ClientThread clientThread;
    @Inject private UimAtlasConfig config;
    @Inject private AccountStateService states;
    @Inject private RuneLiteAccountObserver observer;

    private UimAtlasPanel panel;
    private NavigationButton navigation;
    private volatile boolean running;

    @Provides
    UimAtlasConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(UimAtlasConfig.class);
    }

    @Override
    protected void startUp()
    {
        running = true;
        clientThread.invokeLater(observer::reset);
        SwingUtilities.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }
            panel = new UimAtlasPanel();
            navigation = NavigationButton.builder().tooltip("UIM Atlas").priority(7)
                .icon(UimAtlasPanel.navigationIcon()).panel(panel).build();
            panel.render(AccountSummary.from(states.getSnapshot()));
            updateNavigation();
        });
    }

    @Override
    protected void shutDown()
    {
        running = false;
        clientThread.invokeLater(observer::reset);
        SwingUtilities.invokeLater(() ->
        {
            if (navigation != null)
            {
                toolbar.removeNavigation(navigation);
            }
            navigation = null;
            panel = null;
        });
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (running)
        {
            observer.refresh();
            render();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            observer.reset();
            render();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        observer.skillsChanged();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        observer.containerChanged(event.getContainerId());
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        observer.varbitChanged(event.getVarbitId());
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == ScriptID.QUESTLIST_INIT)
        {
            observer.questsChanged();
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        resetProfile();
        SwingUtilities.invokeLater(this::updateNavigation);
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        resetProfile();
    }

    private void resetProfile()
    {
        clientThread.invokeLater(() ->
        {
            observer.reset();
            render();
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (UimAtlasConfig.GROUP.equals(event.getGroup()))
        {
            SwingUtilities.invokeLater(this::updateNavigation);
        }
    }

    private void updateNavigation()
    {
        if (navigation != null && running)
        {
            if (config.showSidebar())
            {
                toolbar.addNavigation(navigation);
            }
            else
            {
                toolbar.removeNavigation(navigation);
            }
        }
    }

    private void render()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (running && panel != null)
            {
                panel.render(AccountSummary.from(states.getSnapshot()));
            }
        });
    }
}
