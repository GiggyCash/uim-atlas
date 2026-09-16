package com.uimatlas.plugin;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.uimatlas.planning.PlanningService;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateService;
import com.uimatlas.ui.UimAtlasPanel;
import com.uimatlas.ui.PlannerViewModel;
import com.uimatlas.recommendation.MethodDefinition;
import java.util.List;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UimAtlasPluginTest
{
    @Test
    public void automaticGoalSelectionIsCardinalityBasedAndGoalAgnostic()
    {
        PlanningService planning = PlanningService.loadProduction(getClass().getClassLoader(),
            java.util.Arrays.stream(net.runelite.api.Quest.values()).map(net.runelite.api.Quest::getId)
                .collect(java.util.stream.Collectors.toSet()), 333);
        GoalDefinition only = planning.getGoals().get(0);
        assertEquals(only.getId(), UimAtlasPlugin.automaticGoalId(java.util.List.of(only)));
        assertNull(UimAtlasPlugin.automaticGoalId(java.util.List.of()));
        assertNull(UimAtlasPlugin.automaticGoalId(java.util.List.of(only, only)));
    }

    @Test
    public void injectionEventBusSidebarAndRepeatedLifecycleWork() throws Exception
    {
        Client client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        ClientToolbar toolbar = mock(ClientToolbar.class);
        ConfigManager manager = mock(ConfigManager.class);
        UimAtlasConfig config = mock(UimAtlasConfig.class);
        EventBus routeEvents = mock(EventBus.class);
        when(config.showSidebar()).thenReturn(true);
        when(manager.getConfig(UimAtlasConfig.class)).thenReturn(config);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; })
            .when(clientThread).invokeLater(any(Runnable.class));
        UimAtlasPlugin plugin = new UimAtlasPlugin();
        Injector injector = Guice.createInjector(plugin, binder ->
        {
            binder.bind(Client.class).toInstance(client);
            binder.bind(ClientThread.class).toInstance(clientThread);
            binder.bind(ClientToolbar.class).toInstance(toolbar);
            binder.bind(ConfigManager.class).toInstance(manager);
            binder.bind(EventBus.class).toInstance(routeEvents);
        });
        injector.injectMembers(plugin);
        AccountStateService states = injector.getInstance(AccountStateService.class);
        EventBus events = new EventBus();
        events.register(plugin);
        plugin.startUp();
        flushSwing();
        ArgumentCaptor<NavigationButton> navigation = ArgumentCaptor.forClass(NavigationButton.class);
        verify(toolbar).addNavigation(navigation.capture());
        assertTrue(navigation.getValue().getPanel() instanceof UimAtlasPanel);
        assertEquals(24, navigation.getValue().getIcon().getWidth());
        UimAtlasPanel panel = (UimAtlasPanel) navigation.getValue().getPanel();
        String goalId = injector.getInstance(PlanningService.class).getGoals().get(0).getId();
        assertEquals(goalId, panel.getDisplayed().getSelectedGoalId());
        assertEquals("WAITING", panel.getDisplayed().getStatus());
        verifyNoInteractions(routeEvents);

        events.post(new ScriptPostFired(ScriptID.QUESTLIST_INIT));
        verifyNoInteractions(client); // Script callbacks only mark dirty; they cannot run nested scripts.
        // Render route intents through the actual plugin callback and injected bridge.
        MethodDefinition.RouteTarget target = new com.uimatlas.data.ProductionMethodCatalog()
            .loadBundled(getClass().getClassLoader()).stream().flatMap(method -> method.getStart().getRouteTarget().stream())
            .findFirst().orElseThrow();
        PlannerViewModel waiting = panel.getDisplayed();
        PlannerViewModel choice = new PlannerViewModel(waiting.getAccount(), waiting.getAccountState(),
            waiting.getGoals(), goalId, waiting.getGoalName(), null, PlannerViewModel.SurfaceState.READY,
            "QUEST", "Example quest", null, null, "Available milestone", List.of(), null,
            List.of(new PlannerViewModel.Option("Example training", "Example method", "SETUP READY",
                "The quest has higher priority.", target),
                new PlannerViewModel.Option("Example alternative", "Another method", "SETUP READY",
                    "Lower priority.", null)));
        Player player = mock(Player.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3201, 0));
        AccountState beforeRoute = states.getSnapshot();
        SwingUtilities.invokeAndWait(() -> { panel.render(choice); panel.toggleOptions(); });
        verifyNoInteractions(routeEvents, player);
        SwingUtilities.invokeAndWait(() -> alternativeRoute(panel).doClick());
        flushSwing();
        ArgumentCaptor<Object> messages = ArgumentCaptor.forClass(Object.class);
        verify(routeEvents).post(messages.capture());
        assertRoute(messages.getValue(), target, new WorldPoint(3200, 3201, 0));
        assertSame(choice, panel.getDisplayed());
        assertSame(beforeRoute, states.getSnapshot());
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3210, 3211, 1));
        SwingUtilities.invokeAndWait(() -> alternativeRoute(panel).doClick());
        flushSwing();
        verify(routeEvents, times(2)).post(messages.capture());
        assertRoute(messages.getAllValues().get(2), target, new WorldPoint(3210, 3211, 1));
        assertSame(choice, panel.getDisplayed());
        assertEquals(choice.getAlternatives(), panel.getDisplayed().getAlternatives());
        assertSame(beforeRoute, states.getSnapshot());
        verify(player, times(2)).getWorldLocation();
        // The existing primary route still uses the same controller and reports only a request.
        PlannerViewModel primaryMethod = new PlannerViewModel(choice.getAccount(), choice.getAccountState(),
            choice.getGoals(), goalId, choice.getGoalName(), null, PlannerViewModel.SurfaceState.READY,
            "SETUP READY", "Example training", "Example method", null, "Ready setup", List.of(), target, List.of());
        SwingUtilities.invokeAndWait(() -> { panel.render(primaryMethod); panel.clickRoute(); });
        flushSwing();
        verify(routeEvents, times(3)).post(any(PluginMessage.class));
        assertSame(primaryMethod, panel.getDisplayed());
        clearInvocations(routeEvents);
        SwingUtilities.invokeAndWait(() -> panel.render(choice));
        states.publish(AccountState.builder().loggedIn(true).build());
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        events.post(logout);
        assertEquals(AccountState.empty(), states.getSnapshot());
        flushSwing();
        assertEquals("Waiting for account", panel.getDisplayed().getNext());

        states.publish(AccountState.builder().loggedIn(true).build());
        GameStateChanged hop = new GameStateChanged();
        hop.setGameState(GameState.HOPPING);
        events.post(hop);
        assertEquals(AccountState.empty(), states.getSnapshot());
        flushSwing();
        assertEquals("Waiting for account", panel.getDisplayed().getNext());

        states.publish(AccountState.builder().loggedIn(true).build());
        events.post(new ProfileChanged());
        assertEquals(AccountState.empty(), states.getSnapshot());
        flushSwing();

        when(config.showSidebar()).thenReturn(false);
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(UimAtlasConfig.GROUP);
        events.post(changed);
        flushSwing();
        verify(toolbar).removeNavigation(navigation.getValue());
        plugin.shutDown();
        flushSwing();
        assertEquals(AccountState.empty(), states.getSnapshot());
        when(config.showSidebar()).thenReturn(true);
        plugin.startUp();
        flushSwing();
        plugin.shutDown();
        flushSwing();
        events.unregister(plugin);
        verifyNoInteractions(routeEvents); // Rendering, logout, hop, profile reset and shutdown never route or clear.
    }

    private static void assertRoute(Object event, MethodDefinition.RouteTarget target, WorldPoint start)
    {
        assertTrue(event instanceof PluginMessage);
        PluginMessage message = (PluginMessage) event;
        assertEquals("shortestpath", message.getNamespace());
        assertEquals("path", message.getName());
        assertEquals(java.util.Map.of("start", start, "target",
            new WorldPoint(target.getX(), target.getY(), target.getPlane())), message.getData());
    }

    private static javax.swing.JButton alternativeRoute(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if ("alternativeRouteAction".equals(child.getName())) { return (javax.swing.JButton) child; }
            if (child instanceof java.awt.Container)
            {
                javax.swing.JButton found = alternativeRoute((java.awt.Container) child);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private void flushSwing() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
