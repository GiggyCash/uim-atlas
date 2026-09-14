package com.uimatlas.state;

import com.uimatlas.ui.AccountSummary;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RuneLiteAccountObserverTest
{
    private final Client client = mock(Client.class);
    private final AccountStateService states = new AccountStateService();
    private final RuneLiteAccountObserver observer = new RuneLiteAccountObserver(client, states);
    private final ItemContainer inventory = mock(ItemContainer.class);

    @Before
    public void loggedInAccount()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getAccountHash()).thenReturn(123L);
        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(2);
        when(client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION)).thenReturn(1);
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(10);
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(12);
        when(client.getSkillExperience(any(Skill.class))).thenReturn(1154);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(inventory.getItems()).thenReturn(new Item[]{new Item(995, 1000), new Item(-1, 0), new Item(100, 1)});
        when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        ItemContainer equipment = mock(ItemContainer.class);
        when(equipment.getItems()).thenReturn(new Item[0]);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
        Player player = mock(Player.class);
        WorldView view = mock(WorldView.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        when(player.getWorldView()).thenReturn(view);
        when(view.getId()).thenReturn(-1);
        when(client.getWorld()).thenReturn(301);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
    }

    @Test
    public void normalizesCompleteSnapshotWithoutKeepingClientObjects()
    {
        observer.refresh();
        AccountState state = states.getSnapshot();
        assertTrue(state.isLoaded());
        assertTrue(state.isUltimateIronman());
        assertEquals("Loaded", AccountSummary.from(state).getStatus());
        assertFalse(state.getSkills().getValue().containsKey("OVERALL"));
        assertEquals(state.getSkills().getValue().size() * 10, state.totalLevel().getAsInt());
        assertEquals(12, state.getSkills().getValue().get("ATTACK").getBoostedLevel());
        assertEquals(1154, state.getSkills().getValue().get("ATTACK").getExperience());
        assertEquals(2, state.getInventory().getValue().occupiedSlots());
        assertEquals(0, state.getEquipment().getValue().occupiedSlots());
        assertEquals(301, state.getLocation().getValue().getWorld());
        assertTrue(state.getLocation().getValue().getWorldTypes().contains("MEMBERS"));
        assertEquals(QuestStatus.FINISHED, state.getQuests().getValue().get(Quest.values()[0].getId()));
        assertEquals(Observation.Confidence.LAST_OBSERVED, state.getQuests().getConfidence());
        assertEquals(Boolean.TRUE, state.getCapabilities().getValue().get("capability.poh.owned"));
        assertEquals(Observation.Confidence.VERIFIED_NOW, state.getCapabilities().getConfidence());
        assertEquals("RuneLite: server varbit POH_HOUSE_LOCATION", state.getCapabilities().getSource());
        assertNotNull(state.getCapabilities().getObservedAt());
    }

    @Test
    public void missingContainersRemainUnknownAndAreRetried()
    {
        when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
        observer.refresh();
        assertFalse(states.getSnapshot().getInventory().isKnown());
        assertFalse(states.getSnapshot().isLoaded());
        when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        observer.refresh();
        assertTrue(states.getSnapshot().isLoaded());
    }

    @Test
    public void eventsCoalesceAndUnchangedTicksDoNotRescanSkillsContainersOrQuests()
    {
        observer.refresh();
        clearInvocations(client);
        observer.refresh();
        verify(client, never()).getRealSkillLevel(any());
        verify(client, never()).getItemContainer(anyInt());
        verify(client, never()).runScript(anyInt(), any());
        when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(11);
        when(inventory.getItems()).thenReturn(new Item[0]);
        observer.skillsChanged();
        observer.skillsChanged();
        observer.containerChanged(InventoryID.INV);
        observer.containerChanged(InventoryID.INV);
        observer.refresh();
        assertEquals(11, states.getSnapshot().getSkills().getValue().get("ATTACK").getLevel());
        assertEquals(0, states.getSnapshot().getInventory().getValue().occupiedSlots());
        verify(client, times(1)).getRealSkillLevel(Skill.ATTACK);
        verify(client, times(1)).getItemContainer(InventoryID.INV);
    }

    @Test
    public void questFailureStaysUnknownUntilAnExplicitRefresh()
    {
        doThrow(new IllegalStateException("script unavailable")).when(client).runScript(eq(ScriptID.QUEST_STATUS_GET), any());
        observer.refresh();
        assertFalse(states.getSnapshot().isLoaded());
        assertTrue(states.getSnapshot().getQuests().getValue().values().stream().allMatch(q -> q == QuestStatus.UNKNOWN));
        doNothing().when(client).runScript(eq(ScriptID.QUEST_STATUS_GET), any());
        observer.questsChanged();
        observer.refresh();
        assertTrue(states.getSnapshot().isLoaded());
    }

    @Test
    public void accountSwitchAndLogoutDiscardPreviousState()
    {
        observer.refresh();
        assertTrue(states.getSnapshot().getCapabilities().isKnown());
        when(client.getAccountHash()).thenReturn(456L);
        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(0);
        when(client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION)).thenReturn(0);
        when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
        observer.refresh();
        assertFalse(states.getSnapshot().isUltimateIronman());
        assertFalse(states.getSnapshot().getInventory().isKnown());
        assertFalse(states.getSnapshot().getCapabilities().isKnown());
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        observer.refresh();
        assertEquals(AccountState.empty(), states.getSnapshot());
    }

    @Test
    public void loadingHoppingAndUnavailableIdentityNeverPublishLoadedState()
    {
        for (GameState gameState : new GameState[]{GameState.LOADING, GameState.HOPPING, GameState.CONNECTION_LOST})
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            observer.refresh();
            when(client.getGameState()).thenReturn(gameState);
            observer.refresh();
            assertEquals(AccountState.empty(), states.getSnapshot());
        }
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getAccountHash()).thenReturn(-1L);
        observer.refresh();
        assertEquals(AccountState.empty(), states.getSnapshot());
    }

    @Test
    public void incompleteStatsUnknownModeAndMissingLocationRemainPartial()
    {
        when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(999);
        when(client.getLocalPlayer()).thenReturn(null);
        observer.refresh();
        assertFalse(states.getSnapshot().getSkills().isKnown());
        assertFalse(states.getSnapshot().getAccountMode().isKnown());
        assertFalse(states.getSnapshot().getLocation().isKnown());
        assertEquals("Partial", AccountSummary.from(states.getSnapshot()).getStatus());
    }

    @Test
    public void zeroInvalidAndUnavailableHouseLocationRemainUnknown()
    {
        for (int location : new int[]{0, -1})
        {
            when(client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION)).thenReturn(location);
            observer.refresh();
            assertFalse(states.getSnapshot().getCapabilities().isKnown());
        }
        when(client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION))
            .thenThrow(new IllegalStateException("varbit unavailable"));
        observer.refresh();
        assertFalse(states.getSnapshot().getCapabilities().isKnown());
    }
}
