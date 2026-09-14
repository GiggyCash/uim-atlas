package com.uimatlas.data;

import com.uimatlas.recommendation.*;
import com.uimatlas.state.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkillCoverageReadinessTest
{
    private static final Instant NOW = GoalEvaluatorTest.NOW;

    @Test
    public void everyRecordSeparatesHardGatesPreparationAndOptionalSetup() throws Exception
    {
        for (MethodDefinition method : pack())
        {
            Map<String, Observation<Double>> facts = readyFacts(method);
            assertEquals(method.getId(), MethodEvaluator.Status.AVAILABLE, evaluate(method, facts).getStatus());
            for (Requirement hard : method.getHardRequirements())
            {
                Map<String, Observation<Double>> missing = new HashMap<>(facts);
                missing.put(hard.getFact(), known(0));
                assertEquals(method.getId(), MethodEvaluator.Status.BLOCKED, evaluate(method, missing).getStatus());
                missing.remove(hard.getFact());
                assertEquals(method.getId(), MethodEvaluator.Status.UNKNOWN, evaluate(method, missing).getStatus());
            }
            for (Requirement prep : preparation(method))
            {
                if (prep.getTarget() == 0) { continue; }
                Map<String, Observation<Double>> missing = new HashMap<>(facts);
                missing.put(prep.getFact(), known(0));
                assertEquals(method.getId(), MethodEvaluator.Status.NEEDS_PREP, evaluate(method, missing).getStatus());
                missing.remove(prep.getFact());
                assertEquals(method.getId(), MethodEvaluator.Status.UNKNOWN, evaluate(method, missing).getStatus());
            }
            for (Requirement optional : method.getOptionalSetup())
            {
                for (Observation<Double> value : List.of(known(0), Observation.<Double>unknown()))
                {
                    facts.put(optional.getFact(), value);
                    assertEquals(method.getId(), MethodEvaluator.Status.AVAILABLE, evaluate(method, facts).getStatus());
                    assertTrue(evaluate(method, facts).getMissingPreparation().isEmpty());
                }
            }
        }
    }

    @Test
    public void unknownOrUnusableOptionalRunePickaxeDoesNotAwardTheBetterProfile() throws Exception
    {
        for (MethodDefinition method : pack().stream().filter(m -> m.getActivity().equals("MINING")).collect(Collectors.toList()))
        {
            Map<String, Observation<Double>> facts = readyFacts(method);
            facts.put("skill.mining.level", known(41));
            facts.put("carried.item.1275.quantity", known(1));
            assertEquals(1, new MethodEfficiency().derive(method, facts::get, NOW).getSelected().orElseThrow().getPriority());
            for (Observation<Double> unavailable : List.of(known(0), Observation.<Double>unknown(), known(1).lastObserved()))
            {
                facts.put("carried.item.1275.quantity", unavailable);
                MethodEfficiency.Result result = new MethodEfficiency().derive(method, facts::get, NOW);
                assertEquals(0, result.getSelected().orElseThrow().getPriority());
                assertTrue(result.getTrustedXpRate().isEmpty());
                assertEquals(MethodEvaluator.Status.AVAILABLE, result.getEvaluation().getStatus());
            }
        }
    }

    @Test
    public void staleStarOrUnknownToolIsNotInventedFromCarriedRunePickaxe() throws Exception
    {
        MethodDefinition star = find("method.mining.shooting_star");
        Map<String, Observation<Double>> facts = readyFacts(star);
        facts.put("capability.activity.star.accessible_layer", Observation.verified(1.0, "old star observation", NOW.minusSeconds(31)));
        assertEquals(MethodEvaluator.Status.UNKNOWN, evaluate(star, facts).getStatus());
        facts = readyFacts(star);
        facts.put("carried.item.1275.quantity", known(1));
        facts.remove("capability.tool.usable_pickaxe");
        assertEquals(MethodEvaluator.Status.UNKNOWN, evaluate(star, facts).getStatus());
    }

    @Test
    public void craftingFlowsReuseRealPositionsIncludingReturnedBucketsAtFullInventory() throws Exception
    {
        for (MethodDefinition method : pack().stream().filter(m -> m.getActivity().equals("CRAFTING")).collect(Collectors.toList()))
        {
            AccountState state = RfdSkillCoverageTest.account(Map.of("CRAFTING", 33));
            Map<Integer, ItemStack> inventory = new HashMap<>(state.getInventory().getValue().getSlots());
            for (int slot = inventory.size(); slot < 28; slot++) { inventory.put(slot, new ItemStack(229, 1)); }
            AccountStateFacts facts = new AccountStateFacts(RfdSkillCoverageTest.withInventory(state, inventory), NOW);
            ResourceFlow.Analysis flow = method.getResourceFlow().orElseThrow().analyze(facts, NOW);
            assertEquals(method.getId(), ResourceFlow.Status.READY, flow.getStatus());
            assertEquals(Integer.valueOf(0), flow.getResultingFreeSlots().orElseThrow());
            assertEquals(flow.getInputSlotsReleased(), flow.getOutputSlotsRequired());
            assertEquals(Integer.valueOf(method.getId().endsWith("molten_glass") ? 2 : 1), flow.getOutputSlotsRequired().orElseThrow());
        }
    }

    @Test
    public void quantityNeverTurnsIntoPositionsAndUnknownOutputFlowBlocksHighScore() throws Exception
    {
        MethodDefinition vial = find("method.crafting.vial");
        MethodDefinition lamp = find("method.crafting.oil_lamp");
        AccountState state = RfdSkillCoverageTest.account(Map.of("CRAFTING", 33));
        Map<Integer, ItemStack> inventory = new HashMap<>(state.getInventory().getValue().getSlots());
        inventory.put(4, new ItemStack(1775, 500));
        AccountStateFacts contradictory = new AccountStateFacts(RfdSkillCoverageTest.withInventory(state, inventory), NOW);
        assertEquals(1, contradictory.get("inventory.item.1775.occupied_slots").getValue(), 0);
        assertEquals(ResourceFlow.Status.UNKNOWN, vial.getResourceFlow().orElseThrow().analyze(contradictory, NOW).getStatus());
        AccountStateFacts base = new AccountStateFacts(state, NOW);
        // Relevance and known input materials cannot conceal unobserved output positions.
        FactLookup facts = id -> id.equals("inventory.item.229.occupied_slots") ? Observation.unknown() : base.get(id);
        Map<String, GoalContext.ExternalFactors> factors = Map.of(vial.getId(), new GoalContext.ExternalFactors(0, 0, 0),
            lamp.getId(), new GoalContext.ExternalFactors(0, 0, .05));
        GoalContext.Result context = new GoalContext().decide(ProductionGoalCatalogTest.load(), facts,
            List.of(vial, lamp), factors, Map.of(), NOW);
        StrategicDecision.Result result = new StrategicDecision().decide(context, Map.of());
        StrategicAction.Method highest = (StrategicAction.Method) result.getCandidates().get(0).getAction();
        assertEquals(vial.getId(), highest.getId());
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, highest.getResult().getPreparation().getStatus());
        assertFalse(highest.isActionable());
        assertEquals(lamp.getId(), result.getBestActionable().orElseThrow().getAction().getId());
    }

    @Test
    public void featherStackIsOnePositionAndCalcifiedRequiresAnActuallyEmptyPosition() throws Exception
    {
        AccountState state = RfdSkillCoverageTest.account(Map.of("FISHING", 40));
        AccountStateFacts facts = new AccountStateFacts(state, NOW);
        assertEquals(500, facts.get("inventory.item.314.quantity").getValue(), 0);
        assertEquals(1, facts.get("inventory.item.314.occupied_slots").getValue(), 0);
        assertEquals(20, facts.get("inventory.free_slots").getValue(), 0);
        MethodDefinition calcified = find("method.mining.calcified_cam_torum");
        Map<String, Observation<Double>> inputs = readyFacts(calcified);
        inputs.put("inventory.free_slots", known(0));
        assertEquals(MethodEvaluator.Status.NEEDS_PREP, evaluate(calcified, inputs).getStatus());
        assertTrue(calcified.getResourceFlow().isEmpty());
        assertTrue(find("method.fishing.fly_barbarian_village").getResourceFlow().isEmpty());
    }

    private static List<MethodDefinition> pack() throws Exception
    {
        return ProductionSkillCoverageCatalogTest.load().stream()
            .filter(m -> ProductionSkillCoverageCatalogTest.FAMILIES.contains(m.getActivity())).collect(Collectors.toList());
    }

    private static MethodDefinition find(String id) throws Exception
    {
        return pack().stream().filter(m -> m.getId().equals(id)).findFirst().orElseThrow();
    }

    private static List<Requirement> preparation(MethodDefinition method)
    {
        List<Requirement> result = new java.util.ArrayList<>(method.getPreparation());
        result.addAll(method.getSetupItems()); result.addAll(method.getConsumes()); result.add(method.getFreeInventorySlots());
        return result;
    }

    private static Map<String, Observation<Double>> readyFacts(MethodDefinition method)
    {
        Map<String, Observation<Double>> facts = new HashMap<>();
        method.getHardRequirements().forEach(r -> facts.put(r.getFact(), known(r.getTarget())));
        preparation(method).forEach(r -> facts.put(r.getFact(), known(r.getTarget())));
        return facts;
    }

    private static MethodEvaluator.Evaluation evaluate(MethodDefinition method, Map<String, Observation<Double>> facts)
    {
        return new MethodEvaluator().evaluate(method, facts, NOW);
    }

    private static Observation<Double> known(double value) { return Observation.verified(value, "scenario fact", NOW); }
}
