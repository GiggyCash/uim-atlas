package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResourceFlowTest
{
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    public void stackQuantityNeverBecomesOccupiedSlotCount()
    {
        ResourceFlow flow = flow(List.of(entry(100, 4, ResourceFlow.SlotSemantics.ONE_SHARED_STACK)),
            List.of(entry(200, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)));
        ResourceFlow.Analysis retainedStack = flow.analyze(facts(Map.of(
            "inventory.free_slots", 0.0,
            quantity(100), 500.0, occupied(100), 1.0,
            quantity(200), 0.0, occupied(200), 0.0)), NOW);

        assertEquals(ResourceFlow.Status.INSUFFICIENT_CAPACITY, retainedStack.getStatus());
        assertEquals(0, retainedStack.getInputSlotsReleased().orElseThrow().intValue());
        assertEquals(1, retainedStack.getOutputSlotsRequired().orElseThrow().intValue());
        assertEquals(1, retainedStack.getAdditionalFreeSlotsNeeded());

        ResourceFlow.Analysis exhaustedStack = flow.analyze(facts(Map.of(
            "inventory.free_slots", 0.0,
            quantity(100), 4.0, occupied(100), 1.0,
            quantity(200), 0.0, occupied(200), 0.0)), NOW);
        assertEquals(ResourceFlow.Status.READY, exhaustedStack.getStatus());
        assertEquals(1, exhaustedStack.getInputSlotsReleased().orElseThrow().intValue());
        assertEquals(0, exhaustedStack.getResultingFreeSlots().orElseThrow().intValue());
    }

    @Test
    public void declaredPerUnitSemanticsMustMatchDirectlyObservedPositions()
    {
        ResourceFlow flow = flow(List.of(entry(100, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)),
            List.of(entry(200, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)));
        ResourceFlow.Analysis result = flow.analyze(facts(Map.of(
            "inventory.free_slots", 26.0,
            quantity(100), 500.0, occupied(100), 1.0,
            quantity(200), 0.0, occupied(200), 0.0)), NOW);

        assertEquals(ResourceFlow.Status.UNKNOWN, result.getStatus());
        assertTrue(result.getUnresolvedRequirements().stream()
            .anyMatch(requirement -> requirement.getFact().equals(occupied(100))));
    }

    @Test
    public void anObservedCompatibleOutputStackAvoidsASecondSlot()
    {
        ResourceFlow flow = flow(List.of(entry(100, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)),
            List.of(entry(200, 15, ResourceFlow.SlotSemantics.ONE_SHARED_STACK)));
        ResourceFlow.Analysis result = flow.analyze(facts(Map.of(
            "inventory.free_slots", 0.0,
            quantity(100), 1.0, occupied(100), 1.0,
            quantity(200), 500.0, occupied(200), 1.0)), NOW);

        assertEquals(ResourceFlow.Status.READY, result.getStatus());
        assertEquals(0, result.getOutputSlotsRequired().orElseThrow().intValue());
        assertEquals(1, result.getResultingFreeSlots().orElseThrow().intValue());
    }

    @Test
    public void unknownOrStaleOccupancyRemainsUnknownWithItsObservation()
    {
        ResourceFlow flow = flow(List.of(entry(100, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)),
            List.of(entry(200, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)));
        Map<String, Observation<Double>> observations = Map.of(
            "inventory.free_slots", known(26),
            quantity(100), known(1), occupied(100), known(1),
            quantity(200), known(0), occupied(200), Observation.verified(0.0, "stale inventory", NOW.minusSeconds(301)));
        ResourceFlow.Analysis result = flow.analyze(fact -> observations.getOrDefault(fact, Observation.unknown()), NOW);

        assertEquals(ResourceFlow.Status.UNKNOWN, result.getStatus());
        ResourceFlow.Check check = result.getChecks().stream()
            .filter(value -> value.getFact().equals(occupied(200))).findFirst().orElseThrow();
        assertEquals("stale inventory", check.getObservation().getSource());
        assertEquals(NOW.minusSeconds(301), check.getObservation().getObservedAt());
    }

    @Test
    public void analysisOrderDoesNotDependOnDefinitionOrFactMapOrder()
    {
        ResourceFlow.Entry first = entry(100, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT);
        ResourceFlow.Entry second = entry(101, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT);
        ResourceFlow a = flow(List.of(first, second), List.of(entry(200, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)));
        ResourceFlow b = flow(List.of(second, first), List.of(entry(200, 1, ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT)));
        Map<String, Double> values = Map.of(
            "inventory.free_slots", 25.0,
            quantity(100), 1.0, occupied(100), 1.0,
            quantity(101), 1.0, occupied(101), 1.0,
            quantity(200), 0.0, occupied(200), 0.0);

        ResourceFlow.Analysis left = a.analyze(facts(values), NOW);
        ResourceFlow.Analysis right = b.analyze(facts(values), NOW);
        assertEquals(left.getStatus(), right.getStatus());
        assertEquals(left.getResultingFreeSlots(), right.getResultingFreeSlots());
        assertEquals(left.getChecks().stream().map(ResourceFlow.Check::getFact).collect(Collectors.toList()),
            right.getChecks().stream().map(ResourceFlow.Check::getFact).collect(Collectors.toList()));
    }

    private static ResourceFlow flow(List<ResourceFlow.Entry> inputs, List<ResourceFlow.Entry> outputs)
    {
        return new ResourceFlow(inputs, outputs,
            new Requirement("inventory.free_slots", Requirement.Comparison.AT_LEAST, 0,
                "Current ordinary free slots.", false, 300, false));
    }

    private static ResourceFlow.Entry entry(int itemId, int quantity, ResourceFlow.SlotSemantics semantics)
    {
        return new ResourceFlow.Entry(quantity(itemId), quantity, semantics, 300);
    }

    private static FactLookup facts(Map<String, Double> values)
    {
        return fact -> values.containsKey(fact) ? known(values.get(fact)) : Observation.unknown();
    }

    private static Observation<Double> known(double value)
    {
        return Observation.verified(value, "observed inventory", NOW);
    }

    private static String quantity(int itemId)
    {
        return "inventory.item." + itemId + ".quantity";
    }

    private static String occupied(int itemId)
    {
        return "inventory.item." + itemId + ".occupied_slots";
    }
}
