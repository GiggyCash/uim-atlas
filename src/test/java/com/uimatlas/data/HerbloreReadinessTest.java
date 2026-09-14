package com.uimatlas.data;

import com.uimatlas.recommendation.MethodActionability;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.MethodScorer;
import com.uimatlas.recommendation.PreparationFeasibility;
import com.uimatlas.recommendation.RecommendationDecision;
import com.uimatlas.recommendation.ResourceFlow;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.ContainerState;
import com.uimatlas.state.ItemContainerState;
import com.uimatlas.state.ItemStack;
import com.uimatlas.state.Observation;
import com.uimatlas.state.SkillState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static org.junit.Assert.*;

public class HerbloreReadinessTest
{
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final Map<MethodScorer.Factor, Double> EXPLICIT = Map.of(
        GOAL_PROGRESS, 0.5, STORAGE_UNLOCK_VALUE, 0.0, RISK, 0.0, UNCERTAINTY, 0.0);

    @Test
    public void completeCarriedAttackBatchIsReadyAndActionable() throws Exception
    {
        MethodDefinition attack = method("method.herblore.attack_potion");
        AccountState state = state(3, Map.of(), slots(91, 1, 221, 1), 28);
        RecommendationDecision.CandidateResult result = decide(state, attack, EXPLICIT);

        assertEquals(MethodEvaluator.Status.AVAILABLE, result.getEvaluation().getStatus());
        assertEquals(ResourceFlow.Status.READY, result.getResourceFlow().orElseThrow().getStatus());
        assertEquals(2, result.getResourceFlow().orElseThrow().getInputSlotsReleased().orElseThrow().intValue());
        assertEquals(1, result.getResourceFlow().orElseThrow().getOutputSlotsRequired().orElseThrow().intValue());
        assertEquals(1, result.getResourceFlow().orElseThrow().getResultingFreeSlots().orElseThrow().intValue());
        assertEquals(PreparationFeasibility.Status.READY, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.ACTIONABLE, result.getActionability().getStatus());
        assertTrue(result.getScore().isPresent());
        assertEquals("profile.carried_single_batch", result.getEfficiency().getSelected().orElseThrow().getId());
    }

    @Test
    public void missingSecondaryIsAnExactButUnresolvedDeficit() throws Exception
    {
        MethodDefinition attack = method("method.herblore.attack_potion");
        AccountState state = state(3, Map.of(), slots(91, 1), 28);
        RecommendationDecision.CandidateResult result = decide(state, attack, EXPLICIT);

        assertEquals(MethodEvaluator.Status.NEEDS_PREP, result.getEvaluation().getStatus());
        assertEquals(ResourceFlow.Status.MISSING_INPUTS, result.getResourceFlow().orElseThrow().getStatus());
        PreparationFeasibility.Deficit secondary = result.getPreparation().getDeficits().stream()
            .filter(deficit -> deficit.getRequirement().getFact().equals("inventory.item.221.quantity"))
            .findFirst().orElseThrow();
        assertEquals(1.0, secondary.getShortfall(), 0);
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, result.getActionability().getStatus());
        assertTrue(result.getScore().isEmpty());
    }

    @Test
    public void productionStaminaBatchDoesNotTurnCrystalQuantityIntoSlots() throws Exception
    {
        MethodDefinition stamina = method("method.herblore.stamina_potion");
        AccountState state = state(77, Map.of(), slots(3016, 1, 12640, 400), 28);
        RecommendationDecision.CandidateResult result = decide(state, stamina, EXPLICIT);

        assertEquals(ResourceFlow.Status.READY, result.getResourceFlow().orElseThrow().getStatus());
        assertEquals(1, result.getResourceFlow().orElseThrow().getInputSlotsReleased().orElseThrow().intValue());
        assertEquals(1, result.getResourceFlow().orElseThrow().getOutputSlotsRequired().orElseThrow().intValue());
        assertEquals(0, result.getResourceFlow().orElseThrow().getResultingFreeSlots().orElseThrow().intValue());
        assertEquals(MethodActionability.Status.ACTIONABLE, result.getActionability().getStatus());
    }

    @Test
    public void retainedPasteStackAndFullInventoryExposeOutputSlotShortfall() throws Exception
    {
        MethodDefinition mixology = method("method.herblore.mixology.mammoth_might_order");
        AccountState state = mixologyState(28);
        RecommendationDecision.CandidateResult result = decide(state, mixology, EXPLICIT);

        assertEquals(MethodEvaluator.Status.AVAILABLE, result.getEvaluation().getStatus());
        assertEquals(ResourceFlow.Status.INSUFFICIENT_CAPACITY, result.getResourceFlow().orElseThrow().getStatus());
        assertEquals(0, result.getResourceFlow().orElseThrow().getInputSlotsReleased().orElseThrow().intValue());
        assertEquals(1, result.getResourceFlow().orElseThrow().getOutputSlotsRequired().orElseThrow().intValue());
        assertEquals(1, result.getResourceFlow().orElseThrow().getAdditionalFreeSlotsNeeded());
        assertEquals(1.0, result.getPreparation().getDeficits().stream()
            .filter(deficit -> deficit.getRequirement().getFact().equals("inventory.free_slots"))
            .findFirst().orElseThrow().getShortfall(), 0);
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, result.getActionability().getStatus());
        assertTrue(result.getScore().isPresent());
    }

    @Test
    public void oneVerifiedFreePositionMakesTheHopperOrderReady() throws Exception
    {
        MethodDefinition mixology = method("method.herblore.mixology.mammoth_might_order");
        RecommendationDecision.CandidateResult result = decide(mixologyState(27), mixology, EXPLICIT);

        assertEquals(ResourceFlow.Status.READY, result.getResourceFlow().orElseThrow().getStatus());
        assertEquals(0, result.getResourceFlow().orElseThrow().getInputSlotsReleased().orElseThrow().intValue());
        assertEquals(1, result.getResourceFlow().orElseThrow().getOutputSlotsRequired().orElseThrow().intValue());
        assertEquals(0, result.getResourceFlow().orElseThrow().getResultingFreeSlots().orElseThrow().intValue());
        assertEquals(MethodActionability.Status.ACTIONABLE, result.getActionability().getStatus());
    }

    @Test
    public void higherScoredUnresolvedMethodCannotBeatReadyCurrentSetup() throws Exception
    {
        MethodDefinition attack = method("method.herblore.attack_potion");
        MethodDefinition mixology = method("method.herblore.mixology.mammoth_might_order");
        AccountState state = state(77, mixologyCapabilities(), slots(91, 1, 221, 1), 28).toBuilder()
            .container("mixology_hopper", hopper(40)).build();
        Map<MethodScorer.Factor, Double> high = explicit(1.0);
        Map<MethodScorer.Factor, Double> low = explicit(0.0);
        RecommendationDecision.Candidate highCandidate = new RecommendationDecision.Candidate(mixology, high);
        RecommendationDecision.Candidate readyCandidate = new RecommendationDecision.Candidate(attack, low);

        RecommendationDecision.Result forward = new RecommendationDecision().decide(state,
            List.of(highCandidate, readyCandidate), NOW);
        RecommendationDecision.Result reverse = new RecommendationDecision().decide(state,
            List.of(readyCandidate, highCandidate), NOW);

        assertEquals(mixology.getId(), forward.getCandidates().get(0).getMethod().getId());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE,
            forward.getCandidates().get(0).getActionability().getStatus());
        assertEquals(attack.getId(), forward.getBestActionable().orElseThrow().getMethod().getId());
        assertTrue(forward.getCandidates().get(0).getScore().orElseThrow().getTotal()
            > forward.getBestActionable().orElseThrow().getScore().orElseThrow().getTotal());
        assertEquals(ids(forward), ids(reverse));
        assertEquals(attack.getId(), reverse.getBestActionable().orElseThrow().getMethod().getId());
    }

    @Test
    public void constructionAndHerbloreUseTheSameDecisionPath() throws Exception
    {
        MethodDefinition construction = ProductionConstructionCatalogTest.load().get(0);
        MethodDefinition attack = method("method.herblore.attack_potion");
        Map<Integer, ItemStack> inventory = slots(2347, 1, 8794, 1, 2353, 1, 91, 1, 221, 1);
        for (int count = 0; count < 14; count++)
        {
            add(inventory, 8778, 1);
        }
        AccountState state = state(Map.of("CONSTRUCTION", new SkillState(20, 20, 1),
            "HERBLORE", new SkillState(3, 3, 1)), Map.of("capability.poh.owned", true), inventory, 28);
        RecommendationDecision.Result result = new RecommendationDecision().decide(state,
            List.of(new RecommendationDecision.Candidate(construction, EXPLICIT),
                new RecommendationDecision.Candidate(attack, EXPLICIT)), NOW);

        assertEquals(Set.of("CONSTRUCTION", "HERBLORE"), result.getCandidates().stream()
            .map(candidate -> candidate.getMethod().getActivity()).collect(Collectors.toSet()));
        assertTrue(result.getCandidates().stream().allMatch(candidate -> candidate.getScore().isPresent()));
        assertTrue(result.getCandidates().stream().allMatch(candidate -> candidate.getActionability().isActionable()));
        assertTrue(result.getBestActionable().isPresent());
    }

    @Test
    public void unavailableInventoryKeepsFlowAndRecommendationUnknown() throws Exception
    {
        MethodDefinition attack = method("method.herblore.attack_potion");
        AccountState state = state(3, Map.of(), Map.of(), 0).toBuilder().inventory(Observation.unknown()).build();
        RecommendationDecision.CandidateResult result = decide(state, attack, EXPLICIT);

        assertEquals(MethodEvaluator.Status.UNKNOWN, result.getEvaluation().getStatus());
        assertEquals(ResourceFlow.Status.UNKNOWN, result.getResourceFlow().orElseThrow().getStatus());
        assertFalse(result.getResourceFlow().orElseThrow().getUnresolvedRequirements().isEmpty());
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, result.getActionability().getStatus());
    }

    private static RecommendationDecision.CandidateResult decide(AccountState state, MethodDefinition method,
        Map<MethodScorer.Factor, Double> explicit)
    {
        return new RecommendationDecision().decide(state,
            List.of(new RecommendationDecision.Candidate(method, explicit)), NOW).getCandidates().get(0);
    }

    private static MethodDefinition method(String id) throws Exception
    {
        return ProductionHerbloreCatalogTest.load().stream()
            .filter(method -> method.getId().equals(id)).findFirst().orElseThrow();
    }

    private static AccountState state(int herblore, Map<String, Boolean> capabilities,
        Map<Integer, ItemStack> inventory, int occupied)
    {
        return state(Map.of("HERBLORE", new SkillState(herblore, herblore, 1)), capabilities, inventory, occupied);
    }

    private static AccountState state(Map<String, SkillState> skills, Map<String, Boolean> capabilities,
        Map<Integer, ItemStack> inventory, int occupied)
    {
        Map<Integer, ItemStack> filled = new LinkedHashMap<>(inventory);
        while (filled.size() < occupied)
        {
            add(filled, 40000 + filled.size(), 1);
        }
        return AccountState.builder().loggedIn(true)
            .skills(Observation.map(skills, "scenario skills", NOW))
            .capabilities(Observation.map(capabilities, "scenario capabilities", NOW))
            .inventory(Observation.verified(new ItemContainerState(filled), "scenario inventory", NOW))
            .equipment(Observation.verified(new ItemContainerState(Map.of()), "scenario equipment", NOW)).build();
    }

    private static Map<String, Boolean> mixologyCapabilities()
    {
        return Map.of("capability.quest.children_of_the_sun.complete", true,
            "capability.activity.mixology.order_mmm", true);
    }

    private static AccountState mixologyState(int occupied)
    {
        return state(60, mixologyCapabilities(), Map.of(), occupied).toBuilder()
            .container("mixology_hopper", hopper(40)).build();
    }

    private static ContainerState hopper(int mox)
    {
        return new ContainerState(Observation.unknown(),
            Observation.map(Map.of(30005, mox), "scenario Mixology hopper", NOW), Observation.unknown());
    }

    private static Map<MethodScorer.Factor, Double> explicit(double goal)
    {
        return Map.of(GOAL_PROGRESS, goal, STORAGE_UNLOCK_VALUE, 0.0, RISK, 0.0, UNCERTAINTY, 0.0);
    }

    private static List<String> ids(RecommendationDecision.Result result)
    {
        return result.getCandidates().stream().map(candidate -> candidate.getMethod().getId())
            .collect(Collectors.toList());
    }

    private static Map<Integer, ItemStack> slots(int... itemAndQuantity)
    {
        Map<Integer, ItemStack> slots = new LinkedHashMap<>();
        for (int index = 0; index < itemAndQuantity.length; index += 2)
        {
            add(slots, itemAndQuantity[index], itemAndQuantity[index + 1]);
        }
        return slots;
    }

    private static void add(Map<Integer, ItemStack> slots, int itemId, int quantity)
    {
        slots.put(slots.size(), new ItemStack(itemId, quantity));
    }
}
