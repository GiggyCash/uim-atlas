package com.uimatlas.data;

import com.uimatlas.recommendation.FactLookup;
import com.uimatlas.recommendation.GoalContext;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.recommendation.GoalState;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodActionability;
import com.uimatlas.recommendation.PreparationFeasibility;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import com.uimatlas.state.ItemContainerState;
import com.uimatlas.state.ItemStack;
import com.uimatlas.state.Observation;
import com.uimatlas.state.QuestStatus;
import com.uimatlas.state.SkillState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoalContextProductionTest
{
    private static final Instant NOW = GoalEvaluatorTest.NOW;

    @Test
    public void missingHerbloreDerivesProgressOnlyForCurrentlyUsableHerbloreMethods() throws Exception
    {
        List<MethodDefinition> methods = methods();
        GoalContext.Result result = decide(account(3, false, false, 99), methods);
        Map<String, Double> progress = result.getMethods().stream().collect(Collectors.toMap(
            value -> value.getMethod().getId(), GoalContext.MethodRelevance::getGoalProgress));
        assertEquals(1.0, progress.get("method.herblore.clean_guam"), 0);
        assertEquals(1.0, progress.get("method.herblore.attack_potion"), 0);
        assertEquals(0.0, progress.get("method.herblore.energy_potion"), 0);
        assertEquals(0.0, progress.get("method.construction.mahogany_homes.novice"), 0);
        assertTrue(result.getDecision().getBestActionable().isPresent());
        assertEquals("HERBLORE", result.getDecision().getBestActionable().orElseThrow().getMethod().getActivity());
        assertEquals(3.0, result.getDecision().getBestActionable().orElseThrow().getScore().orElseThrow()
            .getContributions().get(com.uimatlas.recommendation.MethodScorer.Factor.GOAL_PROGRESS), 0);
    }

    @Test
    public void metHerbloreMovesAttentionToExplicitUnsupportedCoverage() throws Exception
    {
        AccountState state = account(25, false, false, 1);
        GoalContext.Result result = decide(state, methods());
        assertTrue(result.getMethods().stream().allMatch(method -> method.getGoalProgress() == 0));
        assertTrue(result.getDecision().getBestActionable().isEmpty());
        assertTrue(result.getCoverageGaps().stream().anyMatch(gap ->
            gap.getCheck().getRequirement().getFact().equals("skill.mining.level")));
        assertTrue(result.getCoverageGaps().stream().noneMatch(gap ->
            gap.getCheck().getRequirement().getFact().equals("skill.herblore.level")));
    }

    @Test
    public void unknownQuestStateDoesNotBecomeCompleteIncompleteOrFavorableProgress() throws Exception
    {
        AccountState state = account(25, false, true, 99);
        GoalContext.Result result = decide(state, methods());
        assertEquals(GoalState.Status.UNKNOWN, result.getGoalState().getStatus());
        assertTrue(result.getGoalState().getUnknownRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.74.complete")));
        assertTrue(result.getMethods().stream().allMatch(method -> method.getGoalProgress() == 0));
        assertTrue(result.getDecision().getBestActionable().isEmpty());
    }

    @Test
    public void completedGoalHasNoArtificialGoalDrivenRecommendation() throws Exception
    {
        AccountState state = account(99, true, false, 99);
        GoalContext.Result result = decide(state, methods());
        assertEquals(GoalState.Status.COMPLETE, result.getGoalState().getStatus());
        assertTrue(result.getMethods().stream().allMatch(method -> method.getGoalProgress() == 0));
        assertTrue(result.getDecision().getCandidates().isEmpty());
        assertTrue(result.getDecision().getBestActionable().isEmpty());
    }

    @Test
    public void goalProgressCannotBypassPreparationActionability() throws Exception
    {
        AccountState state = account(3, false, false, 99);
        AccountStateFacts base = new AccountStateFacts(state, NOW);
        FactLookup facts = id -> id.equals("inventory.item.121.occupied_slots")
            ? Observation.unknown() : base.get(id);
        List<MethodDefinition> methods = methods();
        Map<String, GoalContext.ExternalFactors> factors = factors(methods);
        // Explicit hypothetical session uncertainty forces the ranking contrast; it is not production RFD data.
        factors.put("method.herblore.clean_guam", new GoalContext.ExternalFactors(0, 0, 0.1));
        GoalContext.Result result = new GoalContext().decide(ProductionGoalCatalogTest.load(), facts,
            methods, factors, Map.of(), NOW);
        com.uimatlas.recommendation.RecommendationDecision.CandidateResult attack = result.getDecision().getCandidates()
            .stream().filter(candidate -> candidate.getMethod().getId().equals("method.herblore.attack_potion"))
            .findFirst().orElseThrow();
        com.uimatlas.recommendation.RecommendationDecision.CandidateResult clean = result.getDecision().getCandidates()
            .stream().filter(candidate -> candidate.getMethod().getId().equals("method.herblore.clean_guam"))
            .findFirst().orElseThrow();
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, attack.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.NOT_ACTIONABLE, attack.getActionability().getStatus());
        assertEquals(PreparationFeasibility.Status.READY, clean.getPreparation().getStatus());
        assertEquals(MethodActionability.Status.ACTIONABLE, clean.getActionability().getStatus());
        assertEquals("method.herblore.clean_guam",
            result.getDecision().getBestActionable().orElseThrow().getMethod().getId());
        assertTrue(attack.getScore().orElseThrow().getTotal() > clean.getScore().orElseThrow().getTotal());
        assertTrue(attack.getEfficiency().getSelected().orElseThrow().getEfficiency()
            > clean.getEfficiency().getSelected().orElseThrow().getEfficiency());
    }

    @Test
    public void methodInputOrderCannotChangeGoalOrRecommendationOrdering() throws Exception
    {
        AccountState state = account(3, false, false, 99);
        List<MethodDefinition> forward = methods();
        List<MethodDefinition> reversed = new ArrayList<>(forward);
        Collections.reverse(reversed);
        GoalContext.Result left = decide(state, forward);
        GoalContext.Result right = decide(state, reversed);
        assertEquals(left.getMethods().stream().map(value -> value.getMethod().getId()).collect(Collectors.toList()),
            right.getMethods().stream().map(value -> value.getMethod().getId()).collect(Collectors.toList()));
        assertEquals(left.getDecision().getCandidates().stream().map(value -> value.getMethod().getId()).collect(Collectors.toList()),
            right.getDecision().getCandidates().stream().map(value -> value.getMethod().getId()).collect(Collectors.toList()));
        assertEquals(left.getDecision().getBestActionable().map(value -> value.getMethod().getId()),
            right.getDecision().getBestActionable().map(value -> value.getMethod().getId()));
    }

    private static GoalContext.Result decide(AccountState state, List<MethodDefinition> methods) throws Exception
    {
        return new GoalContext().decide(ProductionGoalCatalogTest.load(), state, methods,
            factors(methods), Map.of(), NOW);
    }

    private static Map<String, GoalContext.ExternalFactors> factors(List<MethodDefinition> methods)
    {
        return new HashMap<>(methods.stream().collect(Collectors.toMap(
            MethodDefinition::getId, ignored -> new GoalContext.ExternalFactors(0, 0, 0))));
    }

    private static List<MethodDefinition> methods() throws Exception
    {
        List<MethodDefinition> methods = new ArrayList<>(ProductionConstructionCatalogTest.load());
        methods.addAll(ProductionHerbloreCatalogTest.load());
        return methods;
    }

    private static AccountState account(int herblore, boolean complete, boolean unknownHorror, int mining)
    {
        Map<String, SkillState> skills = new HashMap<>();
        for (String skill : List.of("AGILITY", "COOKING", "CRAFTING", "FIREMAKING", "FISHING", "FLETCHING",
            "HERBLORE", "MAGIC", "MINING", "RANGED", "SMITHING", "THIEVING", "WOODCUTTING", "CONSTRUCTION"))
        {
            int level = skill.equals("HERBLORE") ? herblore : skill.equals("MINING") ? mining : 99;
            skills.put(skill, new SkillState(level, level, 0));
        }
        Map<Integer, QuestStatus> quests = new HashMap<>();
        for (int id : List.of(4, 8, 9, 17, 25, 27, 48, 52, 60, 64, 72, 74, 85, 95, 101, 103, 129, 133,
            154, 158, 160, 2307, 2308, 2309, 2310, 2311, 2312, 2313, 2314, 2315, 2316))
        {
            quests.put(id, id == 2316 && !complete ? QuestStatus.NOT_STARTED : QuestStatus.FINISHED);
        }
        if (unknownHorror)
        {
            quests.put(74, QuestStatus.UNKNOWN);
        }
        return AccountState.builder().loggedIn(true)
            .skills(Observation.map(skills, "RuneLite: skills", NOW))
            .quests(Observation.map(quests, "RuneLite: Quest.getState", NOW).lastObserved())
            .questPoints(Observation.verified(333, "RuneLite: VarPlayerID.QP", NOW).lastObserved())
            .capabilities(Observation.map(Map.of("capability.combat.rfd_no_prayer", true),
                "verified capability fixture", NOW))
            .inventory(Observation.verified(new ItemContainerState(Map.of(
                0, new ItemStack(199, 1), 1, new ItemStack(91, 1), 2, new ItemStack(221, 1))),
                "RuneLite: inventory", NOW))
            .equipment(Observation.verified(new ItemContainerState(Map.of()), "RuneLite: equipment", NOW))
            .build();
    }
}
