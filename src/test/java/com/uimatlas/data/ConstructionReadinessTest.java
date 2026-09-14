package com.uimatlas.data;

import com.uimatlas.recommendation.MethodActionability;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.MethodScorer;
import com.uimatlas.recommendation.PreparationFeasibility;
import com.uimatlas.recommendation.RecommendationDecision;
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
import org.junit.Test;
import static com.uimatlas.data.ProductionConstructionCatalogTest.load;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static org.junit.Assert.*;

public class ConstructionReadinessTest
{
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final Map<MethodScorer.Factor, Double> EXPLICIT = Map.of(
        GOAL_PROGRESS, 0.5, STORAGE_UNLOCK_VALUE, 0.0, RISK, 0.0, UNCERTAINTY, 0.0);

    @Test
    public void fiveFreeSlotsAndUnknownHelperAreUsableButNotReadyOrActionable() throws Exception
    {
        MethodDefinition novice = load().get(0);
        AccountState state = mahoganyState(20, 5, 8778, 1, false, Map.of(), Map.of());
        RecommendationDecision.CandidateResult result = decide(state, novice);

        assertEquals(MethodEvaluator.Status.AVAILABLE, result.getEvaluation().getStatus());
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, result.getPreparation().getStatus());
        assertEquals(8.0, result.getPreparation().getDeficits().get(0).getShortfall(), 0);
        assertTrue(result.getEfficiency().getSelected().isEmpty());
        assertTrue(result.getEfficiency().getTrustedXpRate().isEmpty());
        assertTrue(result.getScore().isEmpty());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, result.getActionability().getStatus());
        assertTrue(decision(state, List.of(novice)).getBestActionable().isEmpty());
    }

    @Test
    public void verifiedSackImprovesEfficiencyWithoutSolvingOrdinaryWorkingSpace() throws Exception
    {
        MethodDefinition novice = load().get(0);
        AccountState state = mahoganyState(20, 5, 8778, 1, true, Map.of(8778, 28), Map.of());
        RecommendationDecision.CandidateResult result = decide(state, novice);

        assertEquals("profile.loaded_helper", result.getEfficiency().getSelected().orElseThrow().getId());
        assertEquals(0.5, result.getEfficiency().getSelected().orElseThrow().getEfficiency(), 0);
        assertTrue(result.getEfficiency().getTrustedXpRate().isEmpty());
        assertTrue(result.getScore().isPresent());
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, result.getActionability().getStatus());
    }

    @Test
    public void completeTrustedNoviceSetupBecomesActionableWithConditionalXp() throws Exception
    {
        MethodDefinition novice = load().get(0);
        AccountState state = mahoganyState(20, 10, 8778, 14, true, Map.of(8778, 28), Map.of(
            "capability.travel.contract_teleports", true,
            "capability.setup.oak_collection_benchmark", true));
        RecommendationDecision.Result decision = decision(state, List.of(novice));
        RecommendationDecision.CandidateResult result = decision.getBestActionable().orElseThrow();

        assertEquals(MethodEvaluator.Status.AVAILABLE, result.getEvaluation().getStatus());
        assertEquals(PreparationFeasibility.Status.READY, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.ACTIONABLE, result.getActionability().getStatus());
        assertEquals("profile.collection_benchmark", result.getEfficiency().getSelected().orElseThrow().getId());
        assertEquals(60000, result.getEfficiency().getTrustedXpRate().orElseThrow().getMinimum(), 0);
        assertEquals(72000, result.getEfficiency().getTrustedXpRate().orElseThrow().getMaximum(), 0);
        assertFalse(result.getScore().orElseThrow().getContributions().isEmpty());
    }

    @Test
    public void limestoneFiveSlotShortfallIsKnownButNotAPlan() throws Exception
    {
        MethodDefinition limestone = load().get(2);
        AccountState state = limestoneState();
        RecommendationDecision.CandidateResult result = decide(state, limestone);

        assertEquals(MethodEvaluator.Status.NEEDS_PREP, result.getEvaluation().getStatus());
        assertEquals("inventory.item.3420.usable_slots",
            result.getPreparation().getDeficits().get(0).getRequirement().getFact());
        assertEquals(5.0, result.getPreparation().getDeficits().get(0).getShortfall(), 0);
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, result.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, result.getActionability().getStatus());
        assertTrue(result.getScore().isEmpty());
    }

    @Test
    public void lowerScoringReadyMethodBeatsHigherScoringUnresolvedMethodDeterministically() throws Exception
    {
        List<MethodDefinition> methods = load();
        MethodDefinition novice = methods.get(0);
        MethodDefinition adept = methods.get(1);
        AccountState state = mahoganyMixedState();
        RecommendationDecision.Result forward = decision(state, List.of(novice, adept));
        RecommendationDecision.Result reverse = decision(state, List.of(adept, novice));

        assertEquals(novice.getId(), forward.getCandidates().get(0).getMethod().getId());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE,
            forward.getCandidates().get(0).getActionability().getStatus());
        assertEquals(adept.getId(), forward.getBestActionable().orElseThrow().getMethod().getId());
        assertTrue(forward.getCandidates().get(0).getScore().orElseThrow().getTotal()
            > forward.getBestActionable().orElseThrow().getScore().orElseThrow().getTotal());
        assertEquals(ids(forward), ids(reverse));
        assertEquals(forward.getBestActionable().orElseThrow().getMethod().getId(),
            reverse.getBestActionable().orElseThrow().getMethod().getId());
    }

    @Test
    public void explicitPreparationSupportIsRequiredForActionableWithPrep() throws Exception
    {
        MethodDefinition limestone = load().get(2);
        RecommendationDecision.Result unresolved = decision(limestoneState(), List.of(limestone));
        assertTrue(unresolved.getBestActionable().isEmpty());

        RecommendationDecision.Candidate candidate = new RecommendationDecision.Candidate(limestone, EXPLICIT,
            Map.of("inventory.item.3420.usable_slots",
                Observation.verified(true, "scenario preparation provider", NOW)));
        RecommendationDecision.CandidateResult supported = new RecommendationDecision()
            .decide(limestoneState(), List.of(candidate), NOW).getCandidates().get(0);
        assertEquals(PreparationFeasibility.Status.FEASIBLE_PREP, supported.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.ACTIONABLE_WITH_PREP, supported.getActionability().getStatus());
        // It is still unranked without an applicable efficiency profile, so no recommendation is fabricated.
        assertTrue(supported.getScore().isEmpty());

        RecommendationDecision.Candidate stale = new RecommendationDecision.Candidate(limestone, EXPLICIT,
            Map.of("inventory.item.3420.usable_slots",
                Observation.verified(true, "scenario preparation provider", NOW).lastObserved()));
        RecommendationDecision.CandidateResult rejected = new RecommendationDecision()
            .decide(limestoneState(), List.of(stale), NOW).getCandidates().get(0);
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, rejected.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, rejected.getActionability().getStatus());
    }

    @Test
    public void blockedAndUnknownMethodsNeverBecomeActionable() throws Exception
    {
        MethodDefinition novice = load().get(0);
        AccountState blocked = mahoganyState(19, 10, 8778, 14, true, Map.of(8778, 28), Map.of());
        assertEquals(MethodActionability.Reason.BLOCKED, decide(blocked, novice).getActionability().getReason());
        AccountState unknown = blocked.toBuilder().skills(Observation.unknown()).build();
        assertEquals(MethodActionability.Reason.UNKNOWN, decide(unknown, novice).getActionability().getReason());
    }

    private static RecommendationDecision.CandidateResult decide(AccountState state, MethodDefinition method)
    {
        return decision(state, List.of(method)).getCandidates().get(0);
    }

    private static RecommendationDecision.Result decision(AccountState state, List<MethodDefinition> methods)
    {
        List<RecommendationDecision.Candidate> candidates = new ArrayList<>();
        methods.forEach(method -> candidates.add(new RecommendationDecision.Candidate(method, EXPLICIT)));
        return new RecommendationDecision().decide(state, candidates, NOW);
    }

    private static List<String> ids(RecommendationDecision.Result result)
    {
        List<String> ids = new ArrayList<>();
        result.getCandidates().forEach(candidate -> ids.add(candidate.getMethod().getId()));
        return ids;
    }

    private static AccountState mahoganyState(int level, int free, int plankId, int planks,
        boolean sack, Map<Integer, Integer> contents, Map<String, Boolean> extraCapabilities)
    {
        Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
        add(inventory, 2347, 1);
        add(inventory, 8794, 1);
        add(inventory, 2353, 1);
        for (int i = 0; i < planks; i++)
        {
            add(inventory, plankId, 1);
        }
        if (sack)
        {
            add(inventory, 24882, 1);
        }
        fill(inventory, 28 - free);
        Map<String, Boolean> capabilities = new LinkedHashMap<>(extraCapabilities);
        capabilities.put("capability.poh.owned", true);
        ContainerState container = sack ? observedContainer(contents) : ContainerState.unknown();
        return base(level, inventory, Map.of(), capabilities).toBuilder().container("plank_sack", container).build();
    }

    private static AccountState mahoganyMixedState()
    {
        Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
        add(inventory, 2347, 1);
        add(inventory, 8794, 1);
        add(inventory, 2353, 1);
        add(inventory, 8778, 1);
        for (int i = 0; i < 14; i++)
        {
            add(inventory, 8780, 1);
        }
        add(inventory, 24882, 1);
        return base(50, inventory, Map.of(), Map.of("capability.poh.owned", true)).toBuilder()
            .container("plank_sack", observedContainer(Map.of(8778, 28))).build();
    }

    private static AccountState limestoneState()
    {
        Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
        add(inventory, 2347, 1);
        add(inventory, 8794, 1);
        add(inventory, 12854, 1);
        for (int i = 0; i < 10; i++)
        {
            add(inventory, 3420, 1);
        }
        fill(inventory, 23);
        Map<String, Boolean> capabilities = Map.of(
            "capability.poh.owned", true,
            "capability.poh.games_room", true,
            "capability.diary.morytania_hard", true,
            "capability.razmire.serum_208", true,
            "capability.travel.house_teleport", true,
            "capability.supplies.limestone_restock_budget", true);
        return base(59, inventory, Map.of(0, new ItemStack(13114, 1)), capabilities);
    }

    private static AccountState base(int level, Map<Integer, ItemStack> inventory,
        Map<Integer, ItemStack> equipment, Map<String, Boolean> capabilities)
    {
        return AccountState.builder().loggedIn(true)
            .skills(Observation.map(Map.of("CONSTRUCTION", new SkillState(level, level, 1)), "scenario skills", NOW))
            .capabilities(Observation.map(capabilities, "scenario capabilities", NOW))
            .inventory(Observation.verified(new ItemContainerState(inventory), "scenario inventory", NOW))
            .equipment(Observation.verified(new ItemContainerState(equipment), "scenario equipment", NOW)).build();
    }

    private static ContainerState observedContainer(Map<Integer, Integer> contents)
    {
        int used = contents.values().stream().mapToInt(Integer::intValue).sum();
        return new ContainerState(Observation.verified(true, "scenario inventory", NOW),
            Observation.map(contents, "scenario container varbits", NOW),
            Observation.verified(28 - used, "scenario container varbits", NOW));
    }

    private static void add(Map<Integer, ItemStack> inventory, int itemId, int quantity)
    {
        inventory.put(inventory.size(), new ItemStack(itemId, quantity));
    }

    private static void fill(Map<Integer, ItemStack> inventory, int occupied)
    {
        while (inventory.size() < occupied)
        {
            add(inventory, 40000 + inventory.size(), 1);
        }
    }
}
