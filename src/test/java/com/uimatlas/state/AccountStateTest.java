package com.uimatlas.state;

import com.uimatlas.ui.AccountSummary;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountStateTest
{
    private static final Instant TIME = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    public void unknownDoesNotMeanEmptyOrNormal()
    {
        AccountState state = AccountState.empty();
        assertFalse(state.isLoaded());
        assertFalse(state.isUltimateIronman());
        assertFalse(state.totalLevel().isPresent());
        assertFalse(state.getInventory().isKnown());
        assertEquals("Unknown", AccountSummary.from(state).getInventory());
        assertEquals("Unknown", AccountSummary.from(state).getAccount());
    }

    @Test
    public void snapshotDefensivelyCopiesCollectionsAndPreservesProvenance()
    {
        Map<String, SkillState> skills = new HashMap<>();
        skills.put("ATTACK", new SkillState(30, 35, 13363));
        Observation<Map<String, SkillState>> observation = Observation.map(skills, "test skills", TIME);
        skills.clear();
        AccountState state = AccountState.builder().skills(observation).build();
        assertEquals(30, state.totalLevel().getAsInt());
        assertThrows(UnsupportedOperationException.class, () -> state.getSkills().getValue().clear());
        assertEquals(TIME, observation.lastObserved().getObservedAt());
        assertEquals("test skills", observation.lastObserved().getSource());
        assertEquals(Observation.Confidence.LAST_OBSERVED, observation.lastObserved().getConfidence());
        assertEquals(Observation.Confidence.UNKNOWN, Observation.unknown().lastObserved().getConfidence());
    }

    @Test
    public void inventoryCountsOccupiedSlotsInsteadOfQuantityOrUniqueItems()
    {
        Map<Integer, ItemStack> slots = new HashMap<>();
        slots.put(0, new ItemStack(995, 100000));
        slots.put(3, new ItemStack(100, 1));
        slots.put(27, new ItemStack(100, 1));
        ItemContainerState inventory = new ItemContainerState(slots);
        slots.clear();
        assertEquals(3, inventory.occupiedSlots());
        assertEquals(100000, inventory.getSlots().get(0).getQuantity());
        assertThrows(UnsupportedOperationException.class, () -> inventory.getSlots().clear());
        assertThrows(IllegalArgumentException.class,
            () -> new ItemContainerState(Map.of(0, new ItemStack(-1, 0))));
    }

    @Test
    public void unknownAccountModeFailsClosed()
    {
        assertEquals(AccountMode.ULTIMATE_IRONMAN, AccountMode.fromId(2));
        assertEquals(AccountMode.UNRANKED_GROUP_IRONMAN, AccountMode.fromId(6));
        assertEquals(AccountMode.UNKNOWN, AccountMode.fromId(999));
        for (int id : new int[]{0, 1, 3, 4, 5, 6, 999})
        {
            assertFalse(AccountState.builder().accountMode(
                Observation.verified(AccountMode.fromId(id), "test", TIME)).build().isUltimateIronman());
        }
    }

    @Test
    public void resetClearsEveryAccountFieldWithoutChangingOldSnapshots()
    {
        AccountStateService service = new AccountStateService();
        AccountState old = AccountState.builder().loggedIn(true)
            .accountMode(Observation.verified(AccountMode.ULTIMATE_IRONMAN, "test", TIME))
            .inventory(Observation.verified(new ItemContainerState(Map.of()), "test", TIME)).build();
        service.publish(old);
        service.reset();
        assertEquals(AccountState.empty(), service.getSnapshot());
        assertTrue(old.isUltimateIronman());
        assertEquals("0 / 28", AccountSummary.from(old).getInventory());
        assertEquals("Partial", AccountSummary.from(old).getStatus());
    }
}
