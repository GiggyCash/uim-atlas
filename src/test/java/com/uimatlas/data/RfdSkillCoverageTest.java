package com.uimatlas.data;

import com.uimatlas.recommendation.*;
import com.uimatlas.state.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static org.junit.Assert.*;

public class RfdSkillCoverageTest
{
    private static final Instant NOW = GoalEvaluatorTest.NOW;
    private static final String FLY = "method.fishing.fly_barbarian_village";
    private static final String TEMPOROSS = "method.fishing.tempoross_mass";
    private static final String PIRATE = "milestone.rfd.pirate_pete";
    private static final Map<String, Integer> LEVELS = Map.of("MINING", 41, "FISHING", 40, "AGILITY", 35, "CRAFTING", 33);

    @Test
    public void allFourFamiliesAreDiscoveredAndCanBecomeActionableWithoutCandidateRules() throws Exception
    {
        List<MethodDefinition> methods = ProductionSkillCoverageCatalogTest.load();
        for (String family : LEVELS.keySet())
        {
            StrategicDecision.Result result = decide(account(Map.of(family, LEVELS.get(family))), methods, Map.of(), Map.of());
            List<GoalContext.MethodRelevance> relevant = relevant(result);
            assertFalse(family, relevant.isEmpty());
            assertTrue(family, relevant.stream().allMatch(value -> value.getMethod().getActivity().equals(family)));
            assertTrue(result.getContext().getMethods().stream().filter(value -> !value.getMethod().getActivity().equals(family))
                .allMatch(value -> value.getGoalProgress() == 0));
            assertEquals(family, ((StrategicAction.Method) result.getBestActionable().orElseThrow().getAction())
                .getResult().getMethod().getActivity());
            assertEquals(3, result.getBestActionable().orElseThrow().getScore().orElseThrow()
                .getContributions().get(GOAL_PROGRESS), 0);
        }
    }

    @Test
    public void readyFishingSetupBeatsStrongerUnresolvedLoadoutAndReversesAfterPreparation() throws Exception
    {
        List<MethodDefinition> methods = ProductionSkillCoverageCatalogTest.load();
        AccountState state = account(Map.of("FISHING", 40));
        Map<String, GoalContext.ExternalFactors> estimates = Map.of(FLY, new GoalContext.ExternalFactors(0, 0, .05));
        StrategicDecision.Result result = decide(state, methods, estimates, Map.of());
        RecommendationDecision.CandidateResult fly = method(result, FLY);
        RecommendationDecision.CandidateResult tempoross = method(result, TEMPOROSS);
        assertTrue(tempoross.getMethod().getEfficiencyProfiles().get(0).getEfficiency()
            > fly.getEfficiency().getSelected().orElseThrow().getEfficiency());
        assertEquals(MethodEvaluator.Status.NEEDS_PREP, tempoross.getEvaluation().getStatus());
        assertEquals(4, tempoross.getPreparation().getDeficits().size());
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, tempoross.getPreparation().getStatus());
        assertFalse(tempoross.getActionability().isActionable());
        assertTrue(tempoross.getScore().isEmpty());
        assertEquals(PreparationFeasibility.Status.READY, fly.getPreparation().getStatus());
        assertEquals(FLY, result.getBestActionable().orElseThrow().getAction().getId());
        Map<Integer, ItemStack> inventory = new HashMap<>(state.getInventory().getValue().getSlots());
        inventory.put(8, new ItemStack(311, 1)); inventory.put(9, new ItemStack(954, 1));
        inventory.put(10, new ItemStack(2347, 1)); inventory.put(11, new ItemStack(1929, 1));
        inventory.put(12, new ItemStack(1929, 1));
        StrategicDecision.Result prepared = decide(withInventory(state, inventory), methods, estimates, Map.of());
        assertEquals(TEMPOROSS, prepared.getBestActionable().orElseThrow().getAction().getId());
        assertEquals(PreparationFeasibility.Status.READY, method(prepared, TEMPOROSS).getPreparation().getStatus());
    }

    @Test
    public void satisfiedTargetRemovesRewardAndMovesToAnotherCoveredSkill() throws Exception
    {
        List<MethodDefinition> methods = ProductionSkillCoverageCatalogTest.load();
        AccountState state = account(Map.of("FISHING", 53, "HERBLORE", 3));
        StrategicDecision.Result result = decide(state, methods, Map.of(), Map.of());
        assertTrue(result.getContext().getMethods().stream().filter(m -> m.getMethod().getActivity().equals("FISHING"))
            .allMatch(m -> m.getGoalProgress() == 0));
        assertEquals("HERBLORE", ((StrategicAction.Method) result.getBestActionable().orElseThrow().getAction())
            .getResult().getMethod().getActivity());
        for (Requirement target : ProductionGoalCatalogTest.load().getRequirements())
        {
            if (target.getFact().startsWith("skill."))
            {
                String family = target.getFact().split("\\.")[1].toUpperCase(java.util.Locale.ROOT);
                if (LEVELS.containsKey(family))
                {
                    StrategicDecision.Result satisfied = decide(account(Map.of(family, (int) target.getTarget())), methods, Map.of(), Map.of());
                    assertTrue(satisfied.getContext().getMethods().stream().allMatch(m -> m.getGoalProgress() == 0));
                }
            }
        }
    }

    @Test
    public void frontierQuestPriorityPrecedesSharedFactorComparison() throws Exception
    {
        List<MethodDefinition> methods = ProductionSkillCoverageCatalogTest.load();
        AccountState base = account(Map.of("FISHING", 40));
        Map<Integer, QuestStatus> quests = new HashMap<>(base.getQuests().getValue());
        quests.put(2310, QuestStatus.NOT_STARTED);
        AccountState state = base.toBuilder().quests(Observation.map(quests, "scenario quests", NOW).lastObserved()).build();
        StrategicDecision.Result questWins = decide(state, methods,
            Map.of(FLY, new GoalContext.ExternalFactors(0, 0, .1)), Map.of(PIRATE, questCosts(0)));
        assertEquals(PIRATE, questWins.getBestActionable().orElseThrow().getAction().getId());
        assertEquals(StrategicAction.Readiness.READY_TO_HANDOFF, questWins.getBestActionable().orElseThrow().getAction().getReadiness());
        assertEquals(3, questWins.getBestActionable().orElseThrow().getScore().orElseThrow().getTotal(), 0);
        assertTrue(method(questWins, FLY).getActionability().isActionable());
        StrategicDecision.Result priorityWins = decide(state, methods, Map.of(), Map.of(PIRATE, questCosts(.2)));
        StrategicDecision.Candidate quest = priorityWins.getCandidates().stream()
            .filter(c -> c.getAction().getId().equals(PIRATE)).findFirst().orElseThrow();
        assertEquals(2.6, quest.getScore().orElseThrow().getTotal(), .00001);
        assertTrue(method(priorityWins, FLY).getScore().orElseThrow().getTotal()
            > quest.getScore().orElseThrow().getTotal());
        assertEquals(StrategicDecision.Priority.FRONTIER_HANDOFF, quest.getPriority());
        assertEquals(PIRATE, priorityWins.getBestActionable().orElseThrow().getAction().getId());
    }

    @Test
    public void multipleMissingFamiliesAndHerbloreCompeteIndependentlyOfInputOrder() throws Exception
    {
        List<MethodDefinition> methods = ProductionSkillCoverageCatalogTest.load();
        AccountState state = account(Map.of("FISHING", 40, "CRAFTING", 33, "HERBLORE", 3));
        Map<String, GoalContext.ExternalFactors> estimates = new HashMap<>();
        for (MethodDefinition method : methods)
        {
            estimates.put(method.getId(), new GoalContext.ExternalFactors(0, 0,
                method.getId().equals(FLY) ? 0 : .1));
        }
        StrategicDecision.Result forward = decide(state, methods, estimates, Map.of());
        assertEquals(Set.of("FISHING", "CRAFTING", "HERBLORE"), relevant(forward).stream()
            .map(m -> m.getMethod().getActivity()).collect(Collectors.toSet()));
        assertEquals(FLY, forward.getBestActionable().orElseThrow().getAction().getId());
        Collections.reverse(methods = new ArrayList<>(methods));
        StrategicDecision.Result reverse = decide(state, methods, estimates, Map.of());
        assertEquals(forward.getCandidates().stream().map(c -> c.getAction().getId()).collect(Collectors.toList()),
            reverse.getCandidates().stream().map(c -> c.getAction().getId()).collect(Collectors.toList()));
        assertEquals(forward.getCandidates().stream().map(c -> c.getScore().map(MethodScorer.Score::getTotal)).collect(Collectors.toList()),
            reverse.getCandidates().stream().map(c -> c.getScore().map(MethodScorer.Score::getTotal)).collect(Collectors.toList()));
        assertEquals(FLY, reverse.getBestActionable().orElseThrow().getAction().getId());
    }

    @Test
    public void unknownInventoryCannotWinFromRelevanceAndKnownAbsenceIsDifferent() throws Exception
    {
        List<MethodDefinition> methods = ProductionSkillCoverageCatalogTest.load();
        AccountState state = account(Map.of("FISHING", 40));
        StrategicDecision.Result absent = decide(withInventory(state, Map.of()), methods, Map.of(), Map.of());
        StrategicDecision.Result unknown = decide(state.toBuilder().inventory(Observation.unknown()).build(), methods, Map.of(), Map.of());
        assertEquals(1, relevant(unknown).stream().filter(m -> m.getMethod().getId().equals(FLY))
            .findFirst().orElseThrow().getGoalProgress(), 0);
        assertTrue(unknown.getBestActionable().isEmpty());
        assertEquals(MethodEvaluator.Status.NEEDS_PREP, method(absent, FLY).getEvaluation().getStatus());
        assertEquals(MethodEvaluator.Status.UNKNOWN, method(unknown, FLY).getEvaluation().getStatus());
        assertTrue(method(absent, FLY).getPreparation().getDeficits().stream().anyMatch(d -> d.getShortfall() == 1));
        assertTrue(method(unknown, FLY).getPreparation().getDeficits().isEmpty());
        assertFalse(method(unknown, FLY).getPreparation().getUnresolvedRequirements().isEmpty());
    }

    @Test
    public void catalogMiningCoverageUsesObservedCarriedToolInsteadOfCapabilityGuess() throws Exception
    {
        AccountState state = account(Map.of("MINING", 41)).toBuilder().capabilities(Observation.unknown()).build();
        StrategicDecision.Result result = decide(state, ProductionSkillCoverageCatalogTest.load(), Map.of(), Map.of());
        assertEquals(3, relevant(result).size());
        assertEquals("method.mining.iron_mount_karuulm", result.getBestActionable().orElseThrow().getAction().getId());
        assertEquals(MethodEvaluator.GroupResult.SATISFIED,
            method(result, "method.mining.iron_mount_karuulm").getEvaluation().getPreparationAnyOf().get(0).getResult());
    }

    @Test
    public void coverageImprovesFromOneToThirteenOfThirteen() throws Exception
    {
        Set<String> targets = ProductionGoalCatalogTest.load().getRequirements().stream().map(Requirement::getFact)
            .filter(f -> f.startsWith("skill.")).map(f -> f.split("\\.")[1].toUpperCase(java.util.Locale.ROOT))
            .collect(Collectors.toSet());
        assertEquals(13, targets.size());
        List<MethodDefinition> before = new ArrayList<>(ProductionConstructionCatalogTest.load());
        before.addAll(ProductionHerbloreCatalogTest.load());
        assertEquals(1, covered(targets, before).size());
        List<MethodDefinition> after = ProductionSkillCoverageCatalogTest.load();
        assertEquals(targets, covered(targets, after));
        targets.removeAll(covered(targets, after));
        assertTrue(targets.isEmpty());
        // Catalog coverage is not a claim of coverage from every starting level.
        StrategicDecision.Result low = decide(account(Map.of("FISHING", 1, "MINING", 1)), after, Map.of(), Map.of());
        assertTrue(low.getContext().getCoverageGaps().stream().anyMatch(g -> g.getCheck().getRequirement().getFact().equals("skill.fishing.level")));
        assertTrue(low.getContext().getCoverageGaps().stream().anyMatch(g -> g.getCheck().getRequirement().getFact().equals("skill.mining.level")));
    }

    static AccountState account(Map<String, Integer> deficits)
    {
        Map<String, SkillState> skills = new HashMap<>();
        for (String skill : List.of("AGILITY", "COOKING", "CRAFTING", "FIREMAKING", "FISHING", "FLETCHING",
            "HERBLORE", "MAGIC", "MINING", "RANGED", "SMITHING", "THIEVING", "WOODCUTTING", "CONSTRUCTION"))
        {
            int level = deficits.getOrDefault(skill, 99);
            skills.put(skill, new SkillState(level, level, 0));
        }
        Map<Integer, QuestStatus> quests = new HashMap<>();
        for (Quest quest : Quest.values()) { quests.put(quest.getId(), QuestStatus.FINISHED); }
        quests.put(2316, QuestStatus.NOT_STARTED);
        return AccountState.builder().loggedIn(true).skills(Observation.map(skills, "scenario skills", NOW))
            .quests(Observation.map(quests, "scenario quests", NOW).lastObserved())
            .questPoints(Observation.verified(175, "scenario quest points", NOW).lastObserved())
            .capabilities(Observation.map(Map.of("capability.combat.rfd_no_prayer", false,
                "capability.access.cam_torum_mine", false, "capability.activity.star.accessible_layer", false,
                "capability.setup.agility.failure_recovery", true, "capability.quest.priest_in_peril.complete", true),
                "scenario capability proof; not a live provider", NOW))
            .inventory(Observation.verified(new ItemContainerState(Map.of(
                0, new ItemStack(309, 1), 1, new ItemStack(314, 500), 2, new ItemStack(1275, 1),
                3, new ItemStack(1785, 1), 4, new ItemStack(1775, 1), 5, new ItemStack(1783, 1),
                6, new ItemStack(1781, 1), 7, new ItemStack(199, 1))), "scenario inventory", NOW))
            .equipment(Observation.verified(new ItemContainerState(Map.of()), "scenario equipment", NOW)).build();
    }

    static AccountState withInventory(AccountState state, Map<Integer, ItemStack> inventory)
    {
        return state.toBuilder().inventory(Observation.verified(new ItemContainerState(inventory), "scenario inventory", NOW)).build();
    }

    private static Set<String> covered(Set<String> targets, List<MethodDefinition> methods)
    {
        return methods.stream().map(MethodDefinition::getActivity).filter(targets::contains).collect(Collectors.toSet());
    }

    private static List<GoalContext.MethodRelevance> relevant(StrategicDecision.Result result)
    {
        return result.getContext().getMethods().stream().filter(m -> m.getGoalProgress() == 1).collect(Collectors.toList());
    }

    private static RecommendationDecision.CandidateResult method(StrategicDecision.Result result, String id)
    {
        return result.getContext().getDecision().getCandidates().stream().filter(c -> c.getMethod().getId().equals(id)).findFirst().orElseThrow();
    }

    private static Map<MethodScorer.Factor, Double> questCosts(double transition)
    {
        return Map.of(SETUP_COST, 0.0, TRANSITION_COST, transition, INVENTORY_DISRUPTION, 0.0, RISK, 0.0, UNCERTAINTY, 0.0);
    }

    private static StrategicDecision.Result decide(AccountState account, List<MethodDefinition> methods,
        Map<String, GoalContext.ExternalFactors> overrides, Map<String, Map<MethodScorer.Factor, Double>> quests) throws Exception
    {
        Map<String, GoalContext.ExternalFactors> factors = new HashMap<>();
        methods.forEach(m -> factors.put(m.getId(), new GoalContext.ExternalFactors(0, 0, 0)));
        factors.putAll(overrides);
        GoalContext.Result context = new GoalContext().decide(ProductionGoalCatalogTest.load(), account, methods, factors, Map.of(), NOW);
        return new StrategicDecision().decide(context, quests);
    }
}
