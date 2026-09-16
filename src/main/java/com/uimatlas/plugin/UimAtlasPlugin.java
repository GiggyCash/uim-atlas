package com.uimatlas.plugin;

import com.google.inject.Provides;
import com.uimatlas.integration.ShortestPathBridge;
import com.uimatlas.planning.PlanningService;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.state.AccountStateService;
import com.uimatlas.state.RuneLiteAccountObserver;
import com.uimatlas.ui.AccountSummary;
import com.uimatlas.ui.PlannerViewModel;
import com.uimatlas.ui.UimAtlasPanel;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(name = "UIM Atlas", description = "Observable account state for Ultimate Ironman planning",
    tags = {"ultimate", "ironman", "uim", "planning"})
public class UimAtlasPlugin extends Plugin
{
    private static final int PINNED_MAXIMUM_QUEST_POINTS = 333;

    @Inject private ClientToolbar toolbar;
    @Inject private ClientThread clientThread;
    @Inject private UimAtlasConfig config;
    @Inject private AccountStateService states;
    @Inject private RuneLiteAccountObserver observer;
    @Inject private PlanningService planningService;
    @Inject private ShortestPathBridge shortestPath;

    private UimAtlasPanel panel;
    private NavigationButton navigation;
    private volatile boolean running;
    private volatile String selectedGoalId;
    private volatile PlanningService.Result planning;
    private String lastPlanningLog;

    @Provides
    UimAtlasConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(UimAtlasConfig.class);
    }

    @Provides
    @Singleton
    PlanningService providePlanningService()
    {
        return PlanningService.loadProduction(UimAtlasPlugin.class.getClassLoader(),
            Arrays.stream(Quest.values()).map(Quest::getId).collect(Collectors.toSet()),
            PINNED_MAXIMUM_QUEST_POINTS);
    }

    @Override
    protected void startUp()
    {
        running = true;
        selectedGoalId = initialGoalId();
        planning = planningService.plan(states.getSnapshot(), selectedGoalId, Instant.now());
        clientThread.invokeLater(() ->
        {
            observer.reset();
            recalculate(Instant.now());
            render();
        });
        SwingUtilities.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }
            panel = new UimAtlasPanel(this::selectGoal, this::requestRoute);
            navigation = NavigationButton.builder().tooltip("UIM Atlas").priority(7)
                .icon(UimAtlasPanel.navigationIcon()).panel(panel).build();
            panel.render(viewModel());
            updateNavigation();
        });
    }

    @Override
    protected void shutDown()
    {
        running = false;
        planning = null;
        selectedGoalId = null;
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
            Instant refreshCheck = Instant.now();
            boolean due = planning != null && planning.getRefreshAt() != null
                && !refreshCheck.isBefore(planning.getRefreshAt());
            if (due)
            {
                observer.factsChanged(planning.getRefreshFacts());
            }
            if (observer.refresh() || due)
            {
                // refresh() timestamps observations, so planning must capture its cutoff afterwards.
                recalculate(Instant.now());
            }
            render();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            observer.reset();
            recalculate(Instant.now());
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
            recalculate(Instant.now());
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
        PlannerViewModel view = viewModel();
        SwingUtilities.invokeLater(() ->
        {
            if (running && panel != null)
            {
                panel.render(view);
            }
        });
    }

    private PlannerViewModel viewModel()
    {
        PlanningService.Result result = planning;
        if (result == null)
        {
            result = planningService.plan(states.getSnapshot(), selectedGoalId, Instant.now());
        }
        return PlannerViewModel.from(AccountSummary.from(states.getSnapshot()), result);
    }

    private void selectGoal(String goalId)
    {
        if (running)
        {
            clientThread.invokeLater(() ->
            {
                selectedGoalId = goalId;
                recalculate(Instant.now());
                render();
            });
        }
    }

    private String initialGoalId()
    {
        return automaticGoalId(planningService.getGoals());
    }

    static String automaticGoalId(java.util.List<GoalDefinition> goals)
    {
        return goals.size() == 1 ? goals.get(0).getId() : null;
    }

    private void requestRoute(MethodDefinition.RouteTarget target)
    {
        if (running)
        {
            clientThread.invokeLater(() ->
            {
                boolean requested = shortestPath.requestRoute(target);
                SwingUtilities.invokeLater(() ->
                {
                    if (running && panel != null)
                    {
                        panel.routeResult(target, requested);
                    }
                });
            });
        }
    }

    private void recalculate(Instant now)
    {
        planning = planningService.plan(states.getSnapshot(), selectedGoalId, now);
        String selected = planning.getPrimary() == null ? "none" : planning.getPrimary().getAction().getId();
        String summary = String.format("goal=%s methods=%d relevant=%d actionable=%d selected=%s",
            selectedGoalId, planning.getMethodCount(), planning.getRelevantMethodCount(),
            planning.getActionableCount(), selected);
        if (!summary.equals(lastPlanningLog))
        {
            log.debug("Planner {}", summary);
            lastPlanningLog = summary;
        }
    }
}
