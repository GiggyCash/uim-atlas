package com.uimatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.*;
import com.uimatlas.state.*;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static org.junit.Assert.*;

public class StrategicDecisionProductionTest
{
    private static final Instant NOW = GoalEvaluatorTest.NOW;
    private static final String PIRATE = "milestone.rfd.pirate_pete";
    private static final String FINALE = "milestone.rfd.culinaromancer";

    @Test
    public void readyQuestWinsWhenRelevantSkillTrainingIsFinished() throws Exception
    {
        AccountState account = account(25, Map.of(2310, QuestStatus.NOT_STARTED));
        // Later unsupported combat must not prevent an independently verified earlier frontier stage.
        account = account.toBuilder().capabilities(Observation.unknown()).build();
        StrategicDecision.Result result = decide(account);
        StrategicDecision.Candidate best = result.getBestActionable().orElseThrow();
        assertEquals(PIRATE, best.getAction().getId());
        assertEquals(StrategicAction.Kind.QUEST_MILESTONE, best.getAction().getKind());
        assertEquals(StrategicAction.Readiness.READY_TO_HANDOFF, best.getAction().getReadiness());
        assertEquals(1, best.getGoalProgress(), 0);
        assertEquals(3, best.getScore().orElseThrow().getTotal(), 0);
        assertTrue(result.getContext().getDecision().getCandidates().isEmpty());
        assertTrue(result.getCandidates().stream().noneMatch(value ->
            value.getAction().getId().equals("milestone.rfd.introduction")));
        assertFalse(quest(result, FINALE).isActionable());
    }

    @Test
    public void readyRelevantTrainingBeatsBlockedQuestWithStructuredMissingPrerequisite() throws Exception
    {
        StrategicDecision.Result result = decide(account(3, Map.of(74, QuestStatus.NOT_STARTED)));
        StrategicAction.Method best = (StrategicAction.Method) result.getBestActionable().orElseThrow().getAction();
        assertEquals("HERBLORE", best.getResult().getMethod().getActivity());
        assertEquals(StrategicAction.Readiness.READY, best.getReadiness());
        assertEquals(1, best.getRelevance().getGoalProgress(), 0);
        assertTrue(best.getRelevance().getMatchedRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("skill.herblore.level")));
        QuestAction finale = quest(result, FINALE);
        assertEquals(StrategicAction.Readiness.BLOCKED, finale.getReadiness());
        assertTrue(finale.getBlockers().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.74.complete")));
        assertTrue(result.getContext().getMethods().stream().filter(value ->
            value.getMethod().getActivity().equals("CONSTRUCTION")).allMatch(value -> value.getGoalProgress() == 0));
    }

    @Test
    public void unknownQuestCannotWinOrBecomeKnownMissingAndTrainingCanStillWin() throws Exception
    {
        StrategicDecision.Result result = decide(account(3, Map.of(74, QuestStatus.UNKNOWN)));
        QuestAction finale = quest(result, FINALE);
        assertEquals(StrategicAction.Readiness.UNRESOLVED, finale.getReadiness());
        assertFalse(finale.isActionable());
        assertTrue(finale.getUnresolvedRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.74.complete")));
        assertTrue(finale.getBlockers().isEmpty());
        assertEquals(StrategicAction.Kind.METHOD, result.getBestActionable().orElseThrow().getAction().getKind());
        assertTrue(candidate(result, FINALE).getScore().isEmpty());
    }

    @Test
    public void uncoveredPrerequisiteAndSkillStayGapsWithNoUnrelatedSubstitute() throws Exception
    {
        AccountState account = account(25, Map.of(74, QuestStatus.NOT_STARTED));
        Map<String, SkillState> skills = new LinkedHashMap<>(account.getSkills().getValue());
        skills.put("MINING", new SkillState(1, 1, 0));
        StrategicDecision.Result result = decide(account.toBuilder()
            .skills(Observation.map(skills, "scenario skills", NOW)).build());
        assertTrue(result.getBestActionable().isEmpty());
        assertEquals(1, result.getCandidates().size());
        assertEquals(StrategicAction.Readiness.BLOCKED, quest(result, FINALE).getReadiness());
        assertTrue(result.getContext().getCoverageGaps().stream().anyMatch(gap ->
            gap.getCheck().getRequirement().getFact().equals("skill.mining.level")));
        assertTrue(result.getContext().getCoverageGaps().stream().anyMatch(gap ->
            gap.getCheck().getRequirement().getFact().equals("quest.74.complete")));
        assertTrue(result.getContext().getMethods().stream().allMatch(value -> value.getGoalProgress() == 0));
    }

    @Test
    public void highTheoreticalIrrelevantTrainingDoesNotEnterStrategicCompetition() throws Exception
    {
        AccountState account = account(25, Map.of(2310, QuestStatus.NOT_STARTED));
        StrategicDecision.Result result = decide(account);
        MethodDefinition attack = methods().stream().filter(method ->
            method.getId().equals("method.herblore.attack_potion")).findFirst().orElseThrow();
        Map<MethodScorer.Factor, Double> explicit = new EnumMap<>(MethodScorer.Factor.class);
        for (MethodScorer.Factor factor : MethodScorer.Factor.values())
        {
            explicit.put(factor, 0.0);
        }
        // A hypothetical global comparison with external storage value, not an RFD-derived benefit.
        explicit.put(STORAGE_UNLOCK_VALUE, 1.0);
        explicit.put(CURRENT_INVENTORY_FIT, 1.0);
        explicit.put(METHOD_EFFICIENCY, 1.0);
        MethodScorer.Score theoretical = new MethodScorer().score(new MethodEvaluator().evaluate(attack,
            new AccountStateFacts(account, NOW), NOW), explicit).orElseThrow();
        assertTrue(theoretical.getTotal() > result.getBestActionable().orElseThrow().getScore().orElseThrow().getTotal());
        assertEquals(PIRATE, result.getBestActionable().orElseThrow().getAction().getId());
        assertEquals(0, result.getContext().getMethods().stream().filter(value -> value.getMethod().equals(attack))
            .findFirst().orElseThrow().getGoalProgress(), 0);
    }

    @Test
    public void readyQuestBeatsHigherScoringUnresolvedMethodWithoutDeletingIt() throws Exception
    {
        AccountStateFacts base = new AccountStateFacts(account(3, Map.of(2310, QuestStatus.NOT_STARTED)), NOW);
        FactLookup facts = id -> id.equals("inventory.item.121.occupied_slots") ? Observation.unknown() : base.get(id);
        List<MethodDefinition> methods = methods().stream().filter(method ->
            !method.getId().equals("method.herblore.clean_guam")).collect(Collectors.toList());
        GoalContext.Result context = context(ProductionGoalCatalogTest.load(), facts, methods);
        Map<String, Map<MethodScorer.Factor, Double>> factors = questFactors();
        factors.put(PIRATE, Map.of(SETUP_COST, 0.2, TRANSITION_COST, 0.0,
            INVENTORY_DISRUPTION, 0.0, RISK, 0.0, UNCERTAINTY, 0.0));
        StrategicDecision.Result result = new StrategicDecision().decide(context, factors);
        StrategicDecision.Candidate attack = candidate(result, "method.herblore.attack_potion");
        assertEquals(StrategicAction.Readiness.UNRESOLVED, attack.getAction().getReadiness());
        assertTrue(attack.getScore().orElseThrow().getTotal() > candidate(result, PIRATE).getScore().orElseThrow().getTotal());
        assertEquals(PIRATE, result.getBestActionable().orElseThrow().getAction().getId());
        StrategicAction.Method detail = (StrategicAction.Method) attack.getAction();
        assertEquals(PreparationFeasibility.Status.UNRESOLVED_PREP, detail.getResult().getPreparation().getStatus());
        assertTrue(detail.getResult().getScore().isPresent());
        assertTrue(detail.getResult().getEfficiency().getSelected().isPresent());
    }

    @Test
    public void questHandoffIsManualDeterministicAndIndependentOfPluginPresence() throws Exception
    {
        QuestAction quest = quest(decide(account(25, Map.of(2310, QuestStatus.NOT_STARTED))), PIRATE);
        assertEquals(2310, quest.getHandoff().getQuestId());
        assertEquals(quest.getState().getMilestone().getDisplayName(), quest.getHandoff().getDisplayName());
        assertEquals(QuestAction.Handoff.Target.QUEST_HELPER, quest.getHandoff().getTarget());
        assertEquals(QuestAction.Handoff.Availability.MANUAL_ONLY, quest.getHandoff().getAvailability());
        assertEquals(quest.getHandoff(), quest(decide(account(25, Map.of(2310, QuestStatus.NOT_STARTED))), PIRATE).getHandoff());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.questhelper.QuestHelperPlugin"));
    }

    @Test
    public void missingQuestScoringInputsRemainExplicitAndCannotBecomeFavorableDefaults() throws Exception
    {
        GoalContext.Result context = decide(account(25, Map.of(2310, QuestStatus.NOT_STARTED))).getContext();
        StrategicDecision.Result result = new StrategicDecision().decide(context, Map.of(PIRATE, Map.of(RISK, 0.0)));
        assertEquals(StrategicAction.Readiness.READY_TO_HANDOFF, quest(result, PIRATE).getReadiness());
        assertTrue(candidate(result, PIRATE).getScore().isEmpty());
        assertEquals(Set.of(SETUP_COST, TRANSITION_COST, INVENTORY_DISRUPTION, UNCERTAINTY),
            candidate(result, PIRATE).getMissingExternalFactors());
        assertTrue(result.getBestActionable().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new StrategicDecision().decide(context,
            Map.of(PIRATE, Map.of(RISK, Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> new StrategicDecision().decide(context,
            Map.of(PIRATE, Map.of(GOAL_PROGRESS, 1.0))));
    }

    @Test
    public void unknownOwnCompletionGetsNoGoalProgressAndCompletedGoalGetsNoCandidates() throws Exception
    {
        StrategicDecision.Result unknown = decide(account(25, Map.of(2316, QuestStatus.UNKNOWN)));
        assertEquals(0, candidate(unknown, FINALE).getGoalProgress(), 0);
        assertTrue(unknown.getBestActionable().isEmpty());
        StrategicDecision.Result complete = decide(account(25, Map.of(2316, QuestStatus.FINISHED)));
        assertTrue(complete.getCandidates().isEmpty());
        assertTrue(complete.getBestActionable().isEmpty());
    }

    @Test
    public void unknownDependencyBlocksHandoffAndRetainsDependencyObservation() throws Exception
    {
        StrategicDecision.Result result = decide(account(25,
            Map.of(2307, QuestStatus.UNKNOWN, 2310, QuestStatus.NOT_STARTED)));
        QuestAction pirate = quest(result, PIRATE);
        assertFalse(pirate.isActionable());
        assertTrue(pirate.getUnresolvedRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.2307.complete")));
        assertEquals("milestone.rfd.introduction", pirate.getDependencies().get(0).getMilestone().getId());
        assertTrue(result.getBestActionable().isEmpty());
        assertTrue(quest(result, FINALE).getUnresolvedRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.2307.complete")));
    }

    @Test
    public void completedDependencyProvesItsOwnPrerequisitesWithoutReopeningUnknowns() throws Exception
    {
        StrategicDecision.Result result = decide(account(25,
            Map.of(17, QuestStatus.UNKNOWN, 2310, QuestStatus.NOT_STARTED)));
        QuestAction pirate = quest(result, PIRATE);
        assertTrue(pirate.isActionable());
        assertTrue(pirate.getUnresolvedRequirements().isEmpty());
        assertEquals(PIRATE, result.getBestActionable().orElseThrow().getAction().getId());
        assertTrue(result.getContext().getGoalState().getUnknownRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.17.complete")));
    }

    @Test
    public void questPointAndCombatGatesCannotBeOffsetByScores() throws Exception
    {
        AccountState account = account(25, Map.of());
        StrategicDecision.Result lowPoints = decide(account.toBuilder()
            .questPoints(Observation.verified(174, "scenario Quest points", NOW)).build());
        assertEquals(StrategicAction.Readiness.BLOCKED, quest(lowPoints, FINALE).getReadiness());
        assertTrue(quest(lowPoints, FINALE).getBlockers().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("account.quest_points")));
        assertTrue(lowPoints.getBestActionable().isEmpty());
        StrategicDecision.Result combat = decide(account.toBuilder().capabilities(Observation.unknown()).build());
        assertFalse(quest(combat, FINALE).isActionable());
        assertTrue(quest(combat, FINALE).getUnresolvedRequirements().stream().anyMatch(check ->
            check.getRequirement().isSafetyRelevant()));
        assertTrue(combat.getContext().getCoverageGaps().stream().anyMatch(gap ->
            gap.getCheck().getRequirement().isSafetyRelevant()));
        assertTrue(combat.getBestActionable().isEmpty());
    }

    @Test
    public void staleAndFutureQuestObservationsCannotBecomeReadyHandoffs() throws Exception
    {
        for (Instant time : List.of(NOW.minusSeconds(86401), NOW.plusSeconds(1)))
        {
            AccountState account = account(25, Map.of(2310, QuestStatus.NOT_STARTED));
            StrategicDecision.Result result = decide(account.toBuilder().quests(Observation.map(
                account.getQuests().getValue(), "scenario quest snapshot", time).lastObserved()).build());
            assertTrue(result.getBestActionable().isEmpty());
            assertFalse(quest(result, PIRATE).isActionable());
        }
        QuestAction known = quest(decide(account(25, Map.of(2310, QuestStatus.NOT_STARTED))), PIRATE);
        GoalState.Check dependency = known.getDependencies().get(0).getChecks().get(0);
        assertEquals(Observation.Confidence.LAST_OBSERVED, dependency.getObservation().getConfidence());
        assertEquals(NOW, dependency.getObservation().getObservedAt());
        assertEquals("scenario quests", dependency.getObservation().getSource());
    }

    @Test
    public void reversedMethodsMilestonesAndFactMapsProduceIdenticalDecision() throws Exception
    {
        JsonObject json = new JsonParser().parse(ProductionGoalCatalogTest.json()).getAsJsonObject();
        JsonObject goalJson = json.getAsJsonArray("goals").get(0).getAsJsonObject();
        List<com.google.gson.JsonElement> order = new ArrayList<>();
        goalJson.getAsJsonArray("milestones").forEach(order::add);
        Collections.reverse(order);
        JsonArray reversed = new JsonArray();
        order.forEach(reversed::add);
        goalJson.add("milestones", reversed);
        GoalDefinition reverseGoal = ProductionGoalCatalogTest.loader().loadProduction(new StringReader(json.toString())).get(0);
        List<MethodDefinition> methods = methods();
        Map<String, Observation<Double>> facts = GoalEvaluatorTest.facts(ProductionGoalCatalogTest.load(), 3, false);
        facts.put("quest.2310.complete", Observation.verified(0.0, "scenario quest", NOW));
        AccountStateFacts inventory = new AccountStateFacts(account(3, Map.of()), NOW);
        FactLookup left = id -> facts.getOrDefault(id, inventory.get(id));
        List<String> keys = new ArrayList<>(facts.keySet());
        Collections.reverse(keys);
        Map<String, Observation<Double>> reverseFacts = new LinkedHashMap<>();
        keys.forEach(key -> reverseFacts.put(key, facts.get(key)));
        StrategicDecision.Result first = new StrategicDecision().decide(
            context(ProductionGoalCatalogTest.load(), left, methods), questFactors());
        Collections.reverse(methods);
        StrategicDecision.Result second = new StrategicDecision().decide(context(reverseGoal,
            id -> reverseFacts.getOrDefault(id, inventory.get(id)), methods), questFactors());
        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, first.getCandidates()::clear);
    }

    @Test
    public void sharedScoreUsesOriginalSignedContributionsAndLeavesMethodDecisionIntact() throws Exception
    {
        StrategicDecision.Result result = decide(account(3, Map.of(74, QuestStatus.NOT_STARTED)));
        for (StrategicDecision.Candidate candidate : result.getCandidates())
        {
            if (candidate.getAction() instanceof StrategicAction.Method && candidate.getScore().isPresent())
            {
                StrategicAction.Method method = (StrategicAction.Method) candidate.getAction();
                Map<MethodScorer.Factor, Double> original = method.getResult().getScore().orElseThrow().getContributions();
                assertEquals(9, original.size());
                assertEquals(6, candidate.getScore().orElseThrow().getContributions().size());
                candidate.getScore().orElseThrow().getContributions().forEach((factor, value) ->
                    assertEquals(original.get(factor), value));
                assertFalse(candidate.getScore().orElseThrow().getContributions().containsKey(METHOD_EFFICIENCY));
                assertFalse(candidate.getScore().orElseThrow().getContributions().containsKey(STORAGE_UNLOCK_VALUE));
            }
        }
    }

    private static StrategicDecision.Result decide(AccountState account) throws Exception
    {
        return new StrategicDecision().decide(context(ProductionGoalCatalogTest.load(),
            new AccountStateFacts(account, NOW), methods()), questFactors());
    }

    private static GoalContext.Result context(GoalDefinition goal, FactLookup facts, List<MethodDefinition> methods)
    {
        return new GoalContext().decide(goal, facts, methods, methods.stream().collect(Collectors.toMap(
            MethodDefinition::getId, ignored -> new GoalContext.ExternalFactors(0, 0, 0))), Map.of(), NOW);
    }

    private static Map<String, Map<MethodScorer.Factor, Double>> questFactors() throws Exception
    {
        // Explicit scenario estimates for beginning the handoff, not item/combat completion guarantees.
        Map<MethodScorer.Factor, Double> factors = Map.of(SETUP_COST, 0.0, TRANSITION_COST, 0.0,
            INVENTORY_DISRUPTION, 0.0, RISK, 0.0, UNCERTAINTY, 0.0);
        return ProductionGoalCatalogTest.load().getMilestones().stream().collect(Collectors.toMap(
            GoalDefinition.Milestone::getId, ignored -> factors));
    }

    private static StrategicDecision.Candidate candidate(StrategicDecision.Result result, String id)
    {
        return result.getCandidates().stream().filter(value -> value.getAction().getId().equals(id))
            .findFirst().orElseThrow();
    }

    private static QuestAction quest(StrategicDecision.Result result, String id)
    {
        return (QuestAction) candidate(result, id).getAction();
    }

    private static List<MethodDefinition> methods() throws Exception
    {
        List<MethodDefinition> methods = new ArrayList<>(ProductionHerbloreCatalogTest.load());
        methods.addAll(ProductionConstructionCatalogTest.load());
        return methods;
    }

    private static AccountState account(int herblore, Map<Integer, QuestStatus> overrides) throws Exception
    {
        Map<String, SkillState> skills = new LinkedHashMap<>();
        Map<Integer, QuestStatus> quests = new LinkedHashMap<>();
        GoalEvaluatorTest.facts(ProductionGoalCatalogTest.load(), herblore, false).forEach((id, observation) ->
        {
            if (id.startsWith("skill."))
            {
                int level = observation.getValue().intValue();
                skills.put(id.split("\\.")[1].toUpperCase(Locale.ROOT), new SkillState(level, level, 0));
            }
            else if (id.startsWith("quest."))
            {
                quests.put(Integer.parseInt(id.split("\\.")[1]), observation.getValue() == 1
                    ? QuestStatus.FINISHED : QuestStatus.NOT_STARTED);
            }
        });
        quests.putAll(overrides);
        return AccountState.builder().loggedIn(true)
            .skills(Observation.map(skills, "scenario skills", NOW))
            .quests(Observation.map(quests, "scenario quests", NOW).lastObserved())
            .questPoints(Observation.verified(333, "scenario Quest points", NOW).lastObserved())
            .capabilities(Observation.map(Map.of("capability.combat.rfd_no_prayer", true), "scenario capability", NOW))
            .inventory(Observation.verified(new ItemContainerState(Map.of(0, new ItemStack(199, 1),
                1, new ItemStack(91, 1), 2, new ItemStack(221, 1))), "scenario inventory", NOW))
            .equipment(Observation.verified(new ItemContainerState(Map.of()), "scenario equipment", NOW)).build();
    }
}
