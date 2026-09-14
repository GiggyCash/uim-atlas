package com.uimatlas.state;

import com.uimatlas.recommendation.FactLookup;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodEvaluator.Status.*;
import static org.junit.Assert.*;

public class AccountStateFactsTest
{
    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    public void realSkillLevelAndXpKeepTheirObservationMetadata()
    {
        Observation<Map<String, SkillState>> skills = Observation.map(Map.of(
            "CONSTRUCTION", new SkillState(42, 47, 50000),
            "FISHING", new SkillState(20, 18, 6000)), "test skills", NOW.minusSeconds(10));
        FactLookup facts = new AccountStateFacts(AccountState.builder().skills(skills).build(), NOW);
        assertEquals(derived(42, skills), facts.get("skill.construction.level"));
        assertEquals(derived(50000, skills), facts.get("skill.construction.xp"));
        assertEquals(derived(20, skills), facts.get("skill.fishing.level"));
        assertEquals(derived(6000, skills), facts.get("skill.fishing.xp"));
        assertUnknown(facts.get("skill.missing.level"));
        FactLookup historical = new AccountStateFacts(AccountState.builder().skills(skills.lastObserved()).build(), NOW);
        assertEquals(derived(42, skills).lastObserved(), historical.get("skill.construction.level"));
        assertEquals(derived(50000, skills).lastObserved(), historical.get("skill.construction.xp"));
    }

    @Test
    public void unknownSectionsNeverBecomeZeroOrFreeSpace()
    {
        FactLookup facts = new AccountStateFacts(AccountState.empty(), NOW);
        for (String id : List.of("skill.construction.level", "skill.construction.xp",
            "inventory.occupied_slots", "inventory.free_slots", "inventory.item.1234.quantity",
            "equipment.item.1234.quantity", "carried.item.1234.quantity"))
        {
            assertUnknown(facts.get(id));
        }
    }

    @Test
    public void observedEmptyContainersEstablishEmptySlotsAndAbsentItems()
    {
        Observation<ItemContainerState> empty = container(Map.of(), "test empty", NOW);
        FactLookup facts = new AccountStateFacts(AccountState.builder().inventory(empty).equipment(empty).build(), NOW);
        assertEquals(derived(0, empty), facts.get("inventory.occupied_slots"));
        assertEquals(derived(28, empty), facts.get("inventory.free_slots"));
        assertEquals(derived(0, empty), facts.get("inventory.item.1234.quantity"));
        assertEquals(derived(0, empty), facts.get("equipment.item.1234.quantity"));
        assertEquals(0.0, facts.get("carried.item.1234.quantity").getValue(), 0);
    }

    @Test
    public void quantitiesSumDuplicateStacksWithoutConfusingSlotsOrScopes()
    {
        Observation<ItemContainerState> inventory = container(Map.of(
            0, new ItemStack(1234, 1000), 7, new ItemStack(1234, 5),
            27, new ItemStack(5678, 1)), "test inventory", NOW);
        Observation<ItemContainerState> equipment = container(Map.of(
            3, new ItemStack(1234, 20), 8, new ItemStack(1234, 2)), "test equipment", NOW);
        FactLookup facts = new AccountStateFacts(AccountState.builder().inventory(inventory).equipment(equipment).build(), NOW);
        assertEquals(derived(3, inventory), facts.get("inventory.occupied_slots"));
        assertEquals(derived(25, inventory), facts.get("inventory.free_slots"));
        assertEquals(derived(1005, inventory), facts.get("inventory.item.1234.quantity"));
        assertEquals(derived(1, inventory), facts.get("inventory.item.5678.quantity"));
        assertEquals(derived(22, equipment), facts.get("equipment.item.1234.quantity"));
        assertEquals(derived(0, equipment), facts.get("equipment.item.5678.quantity"));
        Observation<Double> carried = facts.get("carried.item.1234.quantity");
        assertEquals(1027.0, carried.getValue(), 0);
        assertEquals(Observation.Confidence.VERIFIED_NOW, carried.getConfidence());
        assertEquals(NOW, carried.getObservedAt());
        assertEquals("carried sum [inventory: test inventory; equipment: test equipment]", carried.getSource());
    }

    @Test
    public void largeQuantitiesDoNotOverflowAnInteger()
    {
        Observation<ItemContainerState> items = container(Map.of(
            0, new ItemStack(Integer.MAX_VALUE, Integer.MAX_VALUE),
            1, new ItemStack(Integer.MAX_VALUE, Integer.MAX_VALUE)), "test stacks", NOW);
        FactLookup facts = new AccountStateFacts(AccountState.builder().inventory(items).equipment(items).build(), NOW);
        assertEquals(4294967294.0, facts.get("inventory.item.2147483647.quantity").getValue(), 0);
        assertEquals(8589934588.0, facts.get("carried.item.2147483647.quantity").getValue(), 0);
    }

    @Test
    public void carriedRequiresBothSourcesAndUsesWeakestConfidenceInEitherOrder()
    {
        Observation<ItemContainerState> observed = container(Map.of(0, new ItemStack(1234, 2)), "test container", NOW);
        List<Observation<ItemContainerState>> observations = List.of(observed, observed.lastObserved(), Observation.unknown());
        for (Observation<ItemContainerState> inventory : observations)
        {
            for (Observation<ItemContainerState> equipment : observations)
            {
                FactLookup facts = new AccountStateFacts(AccountState.builder().inventory(inventory).equipment(equipment).build(), NOW);
                Observation<Double> carried = facts.get("carried.item.1234.quantity");
                if (!inventory.isKnown() || !equipment.isKnown())
                {
                    assertUnknown(carried);
                    assertUnknown(facts.get("carried.item.9999.quantity"));
                }
                else
                {
                    assertEquals(4.0, carried.getValue(), 0);
                    boolean bothVerified = inventory.getConfidence() == Observation.Confidence.VERIFIED_NOW
                        && equipment.getConfidence() == Observation.Confidence.VERIFIED_NOW;
                    assertEquals(bothVerified ? Observation.Confidence.VERIFIED_NOW : Observation.Confidence.LAST_OBSERVED,
                        carried.getConfidence());
                    assertEquals(bothVerified ? Requirement.Result.SATISFIED : Requirement.Result.UNKNOWN,
                        requirement("carried.item.1234.quantity", 4, false).evaluate(carried, NOW));
                    assertEquals(Requirement.Result.SATISFIED,
                        requirement("carried.item.1234.quantity", 4, true).evaluate(carried, NOW));
                }
            }
        }
    }

    @Test
    public void carriedAgeUsesOldestSourceInEitherOrderAndDoesNotRefreshIt()
    {
        Observation<ItemContainerState> recent = container(Map.of(), "test recent", NOW);
        Observation<ItemContainerState> old = container(Map.of(), "test old", NOW.minusSeconds(61));
        for (boolean oldInventory : List.of(true, false))
        {
            FactLookup facts = new AccountStateFacts(AccountState.builder()
                .inventory(oldInventory ? old : recent).equipment(oldInventory ? recent : old).build(), NOW);
            Observation<Double> carried = facts.get("carried.item.1234.quantity");
            assertEquals(NOW.minusSeconds(61), carried.getObservedAt());
            assertEquals(Requirement.Result.UNKNOWN, requirement("carried.item.1234.quantity", 0, true).evaluate(carried, NOW));
        }
    }

    @Test
    public void historicalInventoryRetainsConfidenceForSlotsAndObservedAbsence()
    {
        Observation<ItemContainerState> old = container(Map.of(), "test inventory", NOW.minusSeconds(60)).lastObserved();
        FactLookup facts = new AccountStateFacts(AccountState.builder().inventory(old).build(), NOW);
        assertEquals(derived(0, old).lastObserved(), facts.get("inventory.occupied_slots"));
        assertEquals(derived(28, old).lastObserved(), facts.get("inventory.free_slots"));
        assertEquals(derived(0, old).lastObserved(), facts.get("inventory.item.1234.quantity"));
        assertEquals(Requirement.Result.UNKNOWN, requirement("inventory.free_slots", 1, false)
            .evaluate(facts.get("inventory.free_slots"), NOW));
        assertEquals(Requirement.Result.SATISFIED, requirement("inventory.free_slots", 1, true)
            .evaluate(facts.get("inventory.free_slots"), NOW));
        assertEquals(Requirement.Result.UNKNOWN, requirement("inventory.free_slots", 1, true)
            .evaluate(facts.get("inventory.free_slots"), NOW.plusSeconds(1)));
    }

    @Test
    public void futureObservationsStayUnknownEvenWhenCombinedWithAnOlderSource()
    {
        Observation<ItemContainerState> old = container(Map.of(), "test old", NOW.minusSeconds(1));
        Observation<ItemContainerState> future = container(Map.of(), "test future", NOW.plusSeconds(1));
        for (boolean futureInventory : List.of(true, false))
        {
            FactLookup facts = new AccountStateFacts(AccountState.builder()
                .skills(Observation.map(Map.of("CONSTRUCTION", new SkillState(42, 42, 50000)),
                    "test future skills", NOW.plusSeconds(1)))
                .inventory(futureInventory ? future : old).equipment(futureInventory ? old : future).build(), NOW);
            assertUnknown(facts.get("carried.item.1234.quantity"));
            assertUnknown(facts.get("skill.construction.level"));
            assertUnknown(facts.get("skill.construction.xp"));
            assertUnknown(facts.get((futureInventory ? "inventory" : "equipment") + ".item.1234.quantity"));
            if (futureInventory)
            {
                assertUnknown(facts.get("inventory.free_slots"));
                assertUnknown(facts.get("inventory.occupied_slots"));
            }
        }
    }

    @Test
    public void malformedAndUnsupportedFactIdsRemainUnknown()
    {
        FactLookup facts = new AccountStateFacts(AccountState.builder()
            .inventory(container(Map.of(), "test inventory", NOW)).build(), NOW);
        for (String id : List.of("inventory.item.-1.quantity", "inventory.item.01234.quantity",
            "inventory.item.2147483648.quantity", "inventory.item.1.0.quantity", "inventory.item.1234",
            "storage.item.1234.quantity", "Inventory.item.1234.quantity", "skill.CONSTRUCTION.level", "unknown"))
        {
            assertUnknown(facts.get(id));
        }
        assertEquals(0.0, facts.get("inventory.item.0.quantity").getValue(), 0);
    }

    @Test
    public void invalidInventorySlotCannotEstablishFreeSpace()
    {
        FactLookup facts = new AccountStateFacts(AccountState.builder()
            .inventory(container(Map.of(28, new ItemStack(1234, 1)), "test invalid slot", NOW)).build(), NOW);
        assertUnknown(facts.get("inventory.occupied_slots"));
        assertUnknown(facts.get("inventory.free_slots"));
    }

    @Test
    public void accountSnapshotFeedsGenericMethodEvaluatorAndRemainsIndependentOfLaterSnapshots()
    {
        Requirement skill = requirement("skill.construction.level", 42, false);
        MethodDefinition method = new MethodDefinition("synthetic.method.observed_state", "Synthetic observation exercise",
            "SYNTHETIC", "SYNTHETIC", new MethodDefinition.Start("Synthetic area", "Synthetic contact", "Synthetic instruction"),
            List.of(skill), List.of(), requirement("inventory.free_slots", 2, false), List.of(), List.of(), List.of(),
            List.of(requirement("skill.construction.xp", 60000, false)), new MethodDefinition.Style(0, "Synthetic", false),
            new MethodDefinition.XpRate(0, 0, "Synthetic only"), new MethodDefinition.Costs(0, 0, 0, 0, "Synthetic only"),
            MethodDefinition.Danger.LOW, "Synthetic adapter exercise");
        AccountState state = AccountState.builder().loggedIn(true)
            .skills(Observation.map(Map.of("CONSTRUCTION", new SkillState(42, 50, 50000)), "test skills", NOW))
            .inventory(container(Map.of(), "test inventory", NOW)).build();
        FactLookup facts = new AccountStateFacts(state, NOW);
        MethodEvaluator evaluator = new MethodEvaluator();
        assertEquals(AVAILABLE, evaluator.evaluate(method, facts, NOW).getStatus());
        AccountState lower = state.toBuilder().skills(Observation.map(
            Map.of("CONSTRUCTION", new SkillState(41, 50, 49000)), "test skills", NOW)).build();
        assertEquals(BLOCKED, evaluator.evaluate(method, new AccountStateFacts(lower, NOW), NOW).getStatus());
        AccountState unknownInventory = state.toBuilder().inventory(Observation.unknown()).build();
        MethodEvaluator.Evaluation unknown = evaluator.evaluate(method, new AccountStateFacts(unknownInventory, NOW), NOW);
        assertEquals(UNKNOWN, unknown.getStatus());
        assertEquals(List.of(method.getFreeInventorySlots()), unknown.getUnknownRequirements());
        assertTrue(unknown.getMissingPreparation().isEmpty());
        assertEquals(UNKNOWN, evaluator.evaluate(method, new AccountStateFacts(AccountState.empty(), NOW), NOW).getStatus());
        assertEquals(AVAILABLE, evaluator.evaluate(method, facts, NOW).getStatus());
        assertEquals(UNKNOWN, evaluator.evaluate(method, facts, NOW.plusSeconds(61)).getStatus());
    }

    @Test
    public void usablePositionsCountStacksOncePreserveProvenanceAndNeverInspectContainers()
    {
        Observation<ItemContainerState> inventory = container(Map.of(0, new ItemStack(1234, 500),
            1, new ItemStack(1234, 1), 2, new ItemStack(5678, 1)), "capacity inventory", NOW.minusSeconds(60));
        AccountState state = AccountState.builder().inventory(inventory).build();
        FactLookup facts = new AccountStateFacts(state, NOW);
        assertEquals(derived(27, inventory), facts.get("inventory.item.1234.usable_slots"));
        assertEquals(derived(26, inventory), facts.get("inventory.item.5678.usable_slots"));
        assertEquals(derived(25, inventory), facts.get("inventory.item.9999.usable_slots"));
        Requirement need = requirement("inventory.item.1234.usable_slots", 27, false);
        assertEquals(Requirement.Result.SATISFIED, need.evaluate(facts.get(need.getFact()), NOW));
        assertEquals(Requirement.Result.UNKNOWN, need.evaluate(facts.get(need.getFact()), NOW.plusSeconds(1)));
        FactLookup historical = new AccountStateFacts(state.toBuilder().inventory(inventory.lastObserved()).build(), NOW);
        assertEquals(derived(27, inventory).lastObserved(), historical.get(need.getFact()));
        assertEquals(Requirement.Result.UNKNOWN, need.evaluate(historical.get(need.getFact()), NOW));
        for (String id : List.of("container.example.owned", "container.example.free_capacity",
            "container.example.contents.1234.quantity", "equipment.item.1234.usable_slots",
            "carried.item.1234.usable_slots", "inventory.item.01234.usable_slots"))
        {
            assertUnknown(facts.get(id));
        }
        assertUnknown(new AccountStateFacts(AccountState.empty(), NOW).get(need.getFact()));
        assertUnknown(new AccountStateFacts(state.toBuilder().inventory(container(Map.of(28, new ItemStack(1234, 1)),
            "invalid capacity", NOW)).build(), NOW).get(need.getFact()));
        assertUnknown(new AccountStateFacts(state.toBuilder().inventory(container(Map.of(),
            "future capacity", NOW.plusSeconds(1))).build(), NOW).get(need.getFact()));
        assertEquals(28, new AccountStateFacts(state.toBuilder().inventory(container(Map.of(), "empty capacity", NOW))
            .build(), NOW).get(need.getFact()).getValue(), 0);
    }

    private static Observation<ItemContainerState> container(Map<Integer, ItemStack> slots, String source, Instant time)
    {
        return Observation.verified(new ItemContainerState(slots), source, time);
    }

    private static Observation<Double> derived(double value, Observation<?> source)
    {
        return Observation.verified(value, source.getSource(), source.getObservedAt());
    }

    private static Requirement requirement(String fact, double target, boolean allowLastObserved)
    {
        return new Requirement(fact, Requirement.Comparison.AT_LEAST, target, "Synthetic requirement", allowLastObserved, 60, false);
    }

    private static void assertUnknown(Observation<Double> observation)
    {
        assertEquals(Observation.unknown(), observation);
    }
}
