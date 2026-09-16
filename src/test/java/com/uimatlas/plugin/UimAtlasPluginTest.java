package com.uimatlas.plugin;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.uimatlas.planning.PlanningService;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateService;
import com.uimatlas.ui.UimAtlasPanel;
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

        events.post(new ScriptPostFired(ScriptID.QUESTLIST_INIT));
        verifyNoInteractions(client); // Script callbacks only mark dirty; they cannot run nested scripts.
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
    }

    private void flushSwing() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
