package com.uimatlas.recommendation;

import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import com.uimatlas.state.ItemContainerState;
import com.uimatlas.state.ItemStack;
import com.uimatlas.state.Observation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static com.uimatlas.recommendation.SyntheticMethods.*;
import static org.junit.Assert.*;

public class SetupScoringInputsTest
{
    private final SetupScoringInputs derivation = new SetupScoringInputs();

    @Test
    public void carriedSetupHasHighFitAndNoRepeatedSetupCharge()
    {
        MethodDefinition method = method(List.of(requirement("carried.item.1001.quantity", 10)), 4, 0);
        Map<MethodScorer.Factor, Double> inputs = derive(method, state(1, 10, true)).getInputs().orElseThrow();
        assertEquals(1, inputs.get(CURRENT_INVENTORY_FIT), 0);
        assertEquals(0, inputs.get(SETUP_COST), 0);
        assertEquals(0, inputs.get(TRANSITION_COST), 0);
    }

    @Test
    public void missingQuantitiesIncreaseCostsMonotonically()
    {
        MethodDefinition method = method(List.of(requirement("inventory.item.1001.quantity", 10)), 4, 0);
        double previous = -1;
        for (int quantity : new int[] {10, 9, 5, 1, 0})
        {
            Map<MethodScorer.Factor, Double> inputs = derive(method, state(1, quantity, true)).getInputs().orElseThrow();
            assertTrue(inputs.get(TRANSITION_COST) > previous);
            previous = inputs.get(TRANSITION_COST);
            assertEquals(quantity / 10.0, inputs.get(CURRENT_INVENTORY_FIT), 0.000001);
        }
        assertTrue(derive(method, state(0, 0, true)).getInputs().orElseThrow().get(SETUP_COST) > 0);
    }

    @Test
    public void additionalMissingSetupIncreasesFriction()
    {
        MethodDefinition method = method(List.of(requirement("inventory.item.1001.quantity", 1),
            requirement("inventory.item.1002.quantity", 1)), 4, 0);
        double oneMissing = derive(method, state(1, 1, true)).getInputs().orElseThrow().get(TRANSITION_COST);
        double twoMissing = derive(method, state(0, 0, true)).getInputs().orElseThrow().get(TRANSITION_COST);
        assertTrue(twoMissing > oneMissing);
    }

    @Test
    public void pressureAndFreeCapacityDetermineDisruptionWithoutRemovingUnrelatedItems()
    {
        MethodDefinition method = method(List.of(requirement("inventory.item.1001.quantity", 1)), 8, 0.5);
        double roomy = derive(method, state(1, 1, true)).getInputs().orElseThrow().get(INVENTORY_DISRUPTION);
        double crowded = derive(method, state(25, 1, true)).getInputs().orElseThrow().get(INVENTORY_DISRUPTION);
        assertTrue(crowded > roomy);
        assertEquals((5 + 0.5 * 25) / 28, crowded, 0.000001);
        MethodDefinition noPressure = method(method.getSetupItems(), 8, 0);
        Map<MethodScorer.Factor, Double> ample = derive(noPressure, state(20, 1, true)).getInputs().orElseThrow();
        assertEquals(0, ample.get(INVENTORY_DISRUPTION), 0);
        assertEquals(0, ample.get(TRANSITION_COST), 0);
        assertEquals(1, ample.get(CURRENT_INVENTORY_FIT), 0);
    }

    @Test
    public void unknownInventoryCannotBecomeEmptyEvenWithoutSetupItems()
    {
        SetupScoringInputs.Result result = derive(method(List.of(), 0, 0), AccountState.empty());
        assertFalse(result.getInputs().isPresent());
        assertEquals("inventory.free_slots", result.getUnresolvedRequirements().get(0).getFact());
        assertFalse(result.withExplicitFactors(explicit(0.9)).isPresent());
    }

    @Test
    public void equipmentIsRequiredOnlyForRelevantScopes()
    {
        AccountState state = state(1, 10, false);
        for (String scope : List.of("equipment", "carried"))
        {
            Requirement item = requirement(scope + ".item.1001.quantity", 1);
            SetupScoringInputs.Result result = derive(method(List.of(item), 0, 0), state);
            assertFalse(result.getInputs().isPresent());
            assertEquals(List.of(item), result.getUnresolvedRequirements());
        }
        assertTrue(derive(method(List.of(requirement("inventory.item.1001.quantity", 1)), 0, 0), state)
            .getInputs().isPresent());
    }

    @Test
    public void observedMissingQuantityIsDifferentFromUnknown()
    {
        MethodDefinition method = method(List.of(requirement("inventory.item.1001.quantity", 10)), 0, 0);
        Map<String, Observation<Double>> facts = ready(method);
        facts.put("inventory.item.1001.quantity", observed(0));
        SetupScoringInputs.Result missing = derivation.derive(method, facts::get, NOW);
        facts.remove("inventory.item.1001.quantity");
        SetupScoringInputs.Result unknown = derivation.derive(method, facts::get, NOW);
        assertEquals(0, missing.getInputs().orElseThrow().get(CURRENT_INVENTORY_FIT), 0);
        assertFalse(unknown.getInputs().isPresent());
        assertFalse(unknown.getObservations().get("inventory.item.1001.quantity").isKnown());
    }

    @Test
    public void freshnessAndInvalidSlotObservationsRemainExplicit()
    {
        MethodDefinition method = method(List.of(), 0, 0);
        for (Observation<Double> invalid : List.of(observed(29), observed(1.5), observed(-1),
            observed(Double.NaN), observed(Double.POSITIVE_INFINITY), observed(28).lastObserved(),
            Observation.verified(28.0, "synthetic source", NOW.minusSeconds(61)),
            Observation.verified(28.0, "synthetic source", NOW.plusSeconds(1))))
        {
            SetupScoringInputs.Result result = derivation.derive(method, fact -> invalid, NOW);
            assertFalse(result.getInputs().isPresent());
            assertEquals(List.of(method.getFreeInventorySlots()), result.getUnresolvedRequirements());
            assertSame(invalid, result.getObservations().get("inventory.free_slots"));
        }
        assertTrue(derivation.derive(method,
            fact -> Observation.verified(28.0, "synthetic source", NOW.minusSeconds(60)), NOW).getInputs().isPresent());
    }

    @Test
    public void itemFreshnessUsesTheExistingRequirementPolicy()
    {
        Requirement item = new Requirement("equipment.item.1001.quantity", Requirement.Comparison.AT_LEAST,
            1, "Synthetic setup", true, 60, false);
        MethodDefinition method = method(List.of(item), 0, 0);
        Map<String, Observation<Double>> facts = ready(method);
        Observation<Double> historical = observed(1).lastObserved();
        facts.put(item.getFact(), historical);
        SetupScoringInputs.Result accepted = derivation.derive(method, facts::get, NOW);
        assertTrue(accepted.getInputs().isPresent());
        assertSame(historical, accepted.getObservations().get(item.getFact()));
        assertFalse(derivation.derive(method, facts::get, NOW.plusSeconds(61)).getInputs().isPresent());
        facts.put(item.getFact(), observed(Double.NaN));
        assertFalse(derivation.derive(method, facts::get, NOW).getInputs().isPresent());
    }

    @Test
    public void batchQuantitiesAndKnownPreparationContributeWithoutSatisfiedPrepCharges()
    {
        MethodDefinition base = method(List.of(), 0, 0);
        Requirement prep = requirement("synthetic.prepared", 1);
        Requirement batch = requirement("inventory.item.1001.quantity", 10);
        MethodDefinition method = copy(base, List.of(prep), List.of(batch));
        Map<String, Observation<Double>> facts = ready(method);
        facts.put(batch.getFact(), observed(5));
        facts.put(prep.getFact(), observed(0));
        Map<MethodScorer.Factor, Double> inputs = derivation.derive(method, facts::get, NOW).getInputs().orElseThrow();
        assertEquals(0.5, inputs.get(CURRENT_INVENTORY_FIT), 0);
        assertEquals(1.5 / 4 + 3.0 / 30, inputs.get(SETUP_COST), 0.000001);
        facts.put(prep.getFact(), observed(1));
        assertTrue(derivation.derive(method, facts::get, NOW).getInputs().orElseThrow().get(SETUP_COST)
            < inputs.get(SETUP_COST));
    }

    @Test
    public void factAndContainerInsertionOrderDoNotAffectImmutableResults()
    {
        MethodDefinition method = method(List.of(requirement("inventory.item.1001.quantity", 10),
            requirement("equipment.item.1002.quantity", 3)), 4, 0.2);
        Map<String, Observation<Double>> facts = ready(method);
        facts.put("inventory.item.1001.quantity", observed(3));
        List<String> ids = new ArrayList<>(facts.keySet());
        Collections.sort(ids);
        Map<String, Observation<Double>> reversed = new LinkedHashMap<>();
        Collections.reverse(ids);
        ids.forEach(id -> reversed.put(id, facts.get(id)));
        SetupScoringInputs.Result result = derivation.derive(method, facts::get, NOW);
        assertEquals(result, derivation.derive(method, reversed::get, NOW));
        reversed.clear();
        assertEquals(result, derivation.derive(method, facts::get, NOW));
        assertThrows(UnsupportedOperationException.class, () -> result.getInputs().orElseThrow().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getObservations().clear());
        Map<Integer, ItemStack> slots = new LinkedHashMap<>();
        slots.put(0, new ItemStack(1001, 3));
        slots.put(1, new ItemStack(1001, 7));
        AccountState first = state(0, 0, true).toBuilder()
            .inventory(Observation.verified(new ItemContainerState(slots), "synthetic inventory", NOW)).build();
        slots.clear();
        slots.put(1, new ItemStack(1001, 7));
        slots.put(0, new ItemStack(1001, 3));
        AccountState second = first.toBuilder()
            .inventory(Observation.verified(new ItemContainerState(slots), "synthetic inventory", NOW)).build();
        assertEquals(derive(method, first), derive(method, second));
        assertEquals(10, derive(method, first).getObservations().get("inventory.item.1001.quantity").getValue(), 0);
    }

    @Test
    public void completeInputsFeedScorerAndRequireAllOtherDimensionsExplicitly()
    {
        MethodDefinition method = method(List.of(), 0, 0);
        FactLookup facts = new AccountStateFacts(state(0, 0, true), NOW);
        SetupScoringInputs.Result result = derivation.derive(method, facts, NOW);
        Map<MethodScorer.Factor, Double> explicit = explicit(0.8);
        explicit.put(UNCERTAINTY, 0.3);
        Map<MethodScorer.Factor, Double> complete = result.withExplicitFactors(explicit).orElseThrow();
        assertEquals(0.3, complete.get(UNCERTAINTY), 0);
        assertTrue(new MethodScorer().score(new MethodEvaluator().evaluate(method, facts, NOW), complete).isPresent());
        assertThrows(IllegalArgumentException.class, () -> result.withExplicitFactors(Map.of()));
        explicit.put(SETUP_COST, 0.0);
        assertThrows(IllegalArgumentException.class, () -> result.withExplicitFactors(explicit));
        explicit.remove(SETUP_COST);
        explicit.put(RISK, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> result.withExplicitFactors(explicit));
    }

    private SetupScoringInputs.Result derive(MethodDefinition method, AccountState state)
    {
        return derivation.derive(method, new AccountStateFacts(state, NOW), NOW);
    }

    private Map<MethodScorer.Factor, Double> explicit(double efficiency)
    {
        Map<MethodScorer.Factor, Double> inputs = scoreInputs(efficiency);
        inputs.keySet().removeAll(List.of(CURRENT_INVENTORY_FIT, SETUP_COST, TRANSITION_COST, INVENTORY_DISRUPTION));
        return inputs;
    }

    private Observation<Double> observed(double value)
    {
        return Observation.verified(value, "synthetic source", NOW);
    }

    private AccountState state(int occupied, int quantity, boolean equipmentKnown)
    {
        Map<Integer, ItemStack> slots = new HashMap<>();
        for (int slot = 0; slot < occupied; slot++)
        {
            slots.put(slot, new ItemStack(slot == 0 && quantity > 0 ? 1001 : 2000 + slot,
                slot == 0 && quantity > 0 ? quantity : 1));
        }
        return AccountState.builder()
            .inventory(Observation.verified(new ItemContainerState(slots), "synthetic inventory", NOW))
            .equipment(equipmentKnown ? Observation.verified(new ItemContainerState(Map.of()), "synthetic equipment", NOW)
                : Observation.unknown()).build();
    }

    private MethodDefinition method(List<Requirement> items, int freeSlots, double pressure)
    {
        MethodDefinition base = candidateMethod("setup", List.of(), List.of(), MethodDefinition.Danger.LOW);
        return new MethodDefinition(base.getId(), base.getDisplayName(), base.getCategory(), base.getActivity(),
            base.getStart(), base.getHardRequirements(), base.getPreparation(), requirement("inventory.free_slots", freeSlots),
            items, base.getConsumes(), base.getProduces(), base.getStopConditions(), base.getStyle(), base.getXpRate(),
            new MethodDefinition.Costs(0, 3, 3, pressure, "Synthetic only"), base.getDanger(), base.getReason());
    }

    private MethodDefinition copy(MethodDefinition base, List<Requirement> prep, List<Requirement> consumes)
    {
        return new MethodDefinition(base.getId(), base.getDisplayName(), base.getCategory(), base.getActivity(),
            base.getStart(), base.getHardRequirements(), prep, base.getFreeInventorySlots(), base.getSetupItems(),
            consumes, base.getProduces(), base.getStopConditions(), base.getStyle(), base.getXpRate(), base.getCosts(),
            base.getDanger(), base.getReason());
    }
}
