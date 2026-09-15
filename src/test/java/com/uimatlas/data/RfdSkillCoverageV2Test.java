package com.uimatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.*;
import com.uimatlas.state.*;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static org.junit.Assert.*;

public class RfdSkillCoverageV2Test
{
    private static final Instant NOW = GoalEvaluatorTest.NOW;
    private static final String PIRATE = "milestone.rfd.pirate_pete";
    private static final Set<String> METHOD_IDS = Set.of(
        "method.cooking.hosidius_mess_meat_pies", "method.cooking.jugs_of_wine_carried",
        "method.cooking.hosidius_mess_pineapple_pizzas", "method.thieving.east_ardougne_cake_stall",
        "method.thieving.hosidius_fruit_stalls", "method.thieving.stealing_artefacts",
        "method.magic.arceuus_library_books", "method.magic.mta_telekinetic_theatre",
        "method.magic.fire_strike_sand_crabs", "method.smithing.bronze_bar_edgeville",
        "method.smithing.bronze_knives_varrock", "method.smithing.iron_platebody_varrock",
        "method.firemaking.carried_regular_logs", "method.firemaking.carried_oak_logs",
        "method.ranged.bronze_darts_sand_crabs", "method.ranged.dorgeshuun_crossbow_ammonite_crabs",
        "method.fletching.arrow_shafts_regular_logs", "method.fletching.headless_arrows",
        "method.woodcutting.regular_trees_lumbridge", "method.woodcutting.oak_trees_draynor",
        "method.woodcutting.teak_castle_wars");

    @Test
    public void allEightCatalogsLoadWithTheExactReviewedMethodSet() throws Exception
    {
        List<MethodDefinition> methods = methods().stream()
            .filter(method -> ProductionSkillCoverageCatalogTest.V2_FAMILIES.contains(method.getActivity()))
            .collect(Collectors.toList());
        assertEquals(21, methods.size());
        assertEquals(METHOD_IDS, methods.stream().map(MethodDefinition::getId).collect(Collectors.toSet()));
        assertEquals(Map.of("COOKING", 3L, "THIEVING", 3L, "MAGIC", 3L, "SMITHING", 3L,
            "FIREMAKING", 2L, "RANGED", 2L, "FLETCHING", 2L, "WOODCUTTING", 3L),
            methods.stream().collect(Collectors.groupingBy(MethodDefinition::getActivity, Collectors.counting())));
        assertTrue(methods.stream().allMatch(method -> !method.getSources().isEmpty()
            && method.getSources().stream().allMatch(source -> source.getReviewedAt().toString().equals("2026-09-14"))));
    }

    @Test
    public void scorerWeightsRemainUnchanged()
    {
        assertEquals(Map.of(GOAL_PROGRESS, 3.0, STORAGE_UNLOCK_VALUE, 3.0, CURRENT_INVENTORY_FIT, 2.0,
            METHOD_EFFICIENCY, 1.0, SETUP_COST, -1.0, TRANSITION_COST, -2.0,
            INVENTORY_DISRUPTION, -2.0, RISK, -4.0, UNCERTAINTY, -3.0), MethodScorer.defaultWeights());
    }

    @Test
    public void cookingThenThievingDeficitsShiftThroughGenericGoalContext() throws Exception
    {
        List<MethodDefinition> methods = methods();
        GoalContext.Result cooking = context(RfdSkillCoverageTest.account(Map.of("COOKING", 20)), methods, Map.of());
        assertEquals("method.cooking.hosidius_mess_meat_pies",
            cooking.getDecision().getBestActionable().orElseThrow().getMethod().getId());
        assertEquals(1, relevance(cooking, "method.cooking.hosidius_mess_meat_pies").getGoalProgress(), 0);
        assertTrue(cooking.getMethods().stream().filter(value -> !value.getMethod().getActivity().equals("COOKING"))
            .allMatch(value -> value.getGoalProgress() == 0));

        GoalContext.Result thieving = context(RfdSkillCoverageTest.account(Map.of("THIEVING", 25)), methods, Map.of());
        assertEquals("method.thieving.hosidius_fruit_stalls",
            thieving.getDecision().getBestActionable().orElseThrow().getMethod().getId());
        assertTrue(thieving.getMethods().stream().filter(value -> value.getMethod().getActivity().equals("COOKING"))
            .allMatch(value -> value.getGoalProgress() == 0));
    }

    @Test
    public void missingOrUnknownCombatResourcesCannotBecomeActionableFromGoalProgress() throws Exception
    {
        AccountState emptyMagic = RfdSkillCoverageTest.withInventory(
            RfdSkillCoverageTest.account(Map.of("MAGIC", 13)), Map.of());
        GoalContext.Result magic = context(emptyMagic, methods(), Map.of());
        RecommendationDecision.CandidateResult strike = method(magic, "method.magic.fire_strike_sand_crabs");
        assertEquals(1, relevance(magic, strike.getMethod().getId()).getGoalProgress(), 0);
        assertEquals(MethodEvaluator.Status.NEEDS_PREP, strike.getEvaluation().getStatus());
        assertFalse(strike.getActionability().isActionable());
        assertEquals("method.magic.arceuus_library_books",
            magic.getDecision().getBestActionable().orElseThrow().getMethod().getId());
        AccountState unknownMagic = emptyMagic.toBuilder().inventory(Observation.unknown()).build();
        assertEquals(MethodEvaluator.Status.UNKNOWN,
            method(context(unknownMagic, methods(), Map.of()), strike.getMethod().getId()).getEvaluation().getStatus());

        AccountState emptyRanged = RfdSkillCoverageTest.withInventory(
            RfdSkillCoverageTest.account(Map.of("RANGED", 1)), Map.of());
        RecommendationDecision.CandidateResult darts = method(context(emptyRanged, methods(), Map.of()),
            "method.ranged.bronze_darts_sand_crabs");
        assertEquals(MethodEvaluator.Status.NEEDS_PREP, darts.getEvaluation().getStatus());
        assertFalse(darts.getActionability().isActionable());
        assertEquals(MethodEvaluator.Status.UNKNOWN, method(context(emptyRanged.toBuilder()
            .inventory(Observation.unknown()).build(), methods(), Map.of()), darts.getMethod().getId())
            .getEvaluation().getStatus());
    }

    @Test
    public void carriedSmithingAndFletchingBatchesBeatUnresolvedHigherTierBatches() throws Exception
    {
        AccountState smith = RfdSkillCoverageTest.withInventory(RfdSkillCoverageTest.account(Map.of("SMITHING", 33)),
            Map.of(0, new ItemStack(2347, 1), 1, new ItemStack(2349, 1)));
        GoalContext.Result smithing = context(smith, methods(), Map.of());
        RecommendationDecision.CandidateResult knives = method(smithing, "method.smithing.bronze_knives_varrock");
        RecommendationDecision.CandidateResult plate = method(smithing, "method.smithing.iron_platebody_varrock");
        assertEquals(ResourceFlow.Status.READY, knives.getResourceFlow().orElseThrow().getStatus());
        assertTrue(knives.getActionability().isActionable());
        assertFalse(plate.getActionability().isActionable());
        assertTrue(plate.getMethod().getEfficiencyProfiles().get(0).getEfficiency()
            > knives.getEfficiency().getSelected().orElseThrow().getEfficiency());
        assertEquals(knives.getMethod().getId(), smithing.getDecision().getBestActionable().orElseThrow().getMethod().getId());

        AccountState fletch = RfdSkillCoverageTest.withInventory(RfdSkillCoverageTest.account(Map.of("FLETCHING", 1)),
            Map.of(0, new ItemStack(946, 1), 1, new ItemStack(1511, 1)));
        GoalContext.Result fletching = context(fletch, methods(), Map.of());
        RecommendationDecision.CandidateResult shafts = method(fletching, "method.fletching.arrow_shafts_regular_logs");
        RecommendationDecision.CandidateResult headless = method(fletching, "method.fletching.headless_arrows");
        assertEquals(ResourceFlow.Status.READY, shafts.getResourceFlow().orElseThrow().getStatus());
        assertTrue(shafts.getActionability().isActionable());
        assertFalse(headless.getActionability().isActionable());
        assertEquals(shafts.getMethod().getId(), fletching.getDecision().getBestActionable().orElseThrow().getMethod().getId());
    }

    @Test
    public void firemakingResourceSinkReleasesTheConsumedLogPosition() throws Exception
    {
        MethodDefinition oak = find("method.firemaking.carried_oak_logs");
        Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
        inventory.put(0, new ItemStack(590, 1));
        inventory.put(1, new ItemStack(1521, 1));
        for (int slot = 2; slot < 28; slot++)
        {
            inventory.put(slot, new ItemStack(229, 1));
        }
        AccountState state = RfdSkillCoverageTest.withInventory(
            RfdSkillCoverageTest.account(Map.of("FIREMAKING", 15)), inventory);
        ResourceFlow.Analysis flow = oak.getResourceFlow().orElseThrow().analyze(new AccountStateFacts(state, NOW), NOW);
        assertEquals(ResourceFlow.Status.READY, flow.getStatus());
        assertEquals(Integer.valueOf(1), flow.getInputSlotsReleased().orElseThrow());
        assertEquals(Integer.valueOf(0), flow.getOutputSlotsRequired().orElseThrow());
        assertEquals(Integer.valueOf(1), flow.getResultingFreeSlots().orElseThrow());
    }

    @Test
    public void toolAlternativesAreLevelAwareUnknownSafeAndOrderIndependent() throws Exception
    {
        MethodDefinition oak = find("method.woodcutting.oak_trees_draynor");
        Map<String, Observation<Double>> facts = toolFacts(oak, 15);
        facts.put("carried.item.1353.quantity", known(1));
        MethodEvaluator.Evaluation usable = new MethodEvaluator().evaluate(oak, facts, NOW);
        assertEquals(MethodEvaluator.Status.AVAILABLE, usable.getStatus());
        assertEquals(MethodEvaluator.GroupResult.SATISFIED, usable.getPreparationAnyOf().get(0).getResult());

        facts.put("carried.item.1353.quantity", known(0));
        facts.put("carried.item.1359.quantity", known(1));
        MethodEvaluator.Evaluation unusable = new MethodEvaluator().evaluate(oak, facts, NOW);
        assertEquals(MethodEvaluator.Status.NEEDS_PREP, unusable.getStatus());
        assertEquals(MethodEvaluator.GroupResult.MISSING, unusable.getPreparationAnyOf().get(0).getResult());
        assertTrue(unusable.getPreparationAnyOf().get(0).getAlternatives().stream()
            .filter(value -> value.getAlternative().getId().endsWith("rune_axe"))
            .allMatch(value -> value.getResult() == MethodEvaluator.GroupResult.MISSING));

        for (MethodDefinition.RequirementGroup.Alternative alternative : oak.getPreparationAnyOf().get(0).getAlternatives())
        {
            facts.remove(alternative.getRequirements().get(0).getFact());
        }
        assertEquals(MethodEvaluator.Status.UNKNOWN, new MethodEvaluator().evaluate(oak, facts, NOW).getStatus());

        JsonObject reversed = resource("woodcutting-v1.json");
        JsonArray alternatives = reversed.getAsJsonArray("requirementGroups").get(0).getAsJsonObject()
            .getAsJsonArray("alternatives");
        List<com.google.gson.JsonElement> copy = new ArrayList<>();
        alternatives.forEach(copy::add);
        Collections.reverse(copy);
        JsonArray replacement = new JsonArray();
        copy.forEach(replacement::add);
        reversed.getAsJsonArray("requirementGroups").get(0).getAsJsonObject().add("alternatives", replacement);
        MethodDefinition reordered = load(reversed).stream().filter(method -> method.getId().equals(oak.getId()))
            .findFirst().orElseThrow();
        MethodEvaluator.GroupCheck originalCheck = new MethodEvaluator().evaluate(oak, facts, NOW)
            .getPreparationAnyOf().get(0);
        MethodEvaluator.GroupCheck reorderedCheck = new MethodEvaluator().evaluate(reordered, facts, NOW)
            .getPreparationAnyOf().get(0);
        assertEquals(originalCheck.getResult(), reorderedCheck.getResult());
        assertEquals(originalCheck.getAlternatives().stream().collect(Collectors.toMap(
            value -> value.getAlternative().getId(), MethodEvaluator.AlternativeCheck::getResult)),
            reorderedCheck.getAlternatives().stream().collect(Collectors.toMap(
                value -> value.getAlternative().getId(), MethodEvaluator.AlternativeCheck::getResult)));
    }

    @Test
    public void malformedAlternativeGroupsAreRejectedStrictly() throws Exception
    {
        JsonObject duplicate = resource("woodcutting-v1.json");
        JsonArray alternatives = duplicate.getAsJsonArray("requirementGroups").get(0).getAsJsonObject()
            .getAsJsonArray("alternatives");
        alternatives.get(1).getAsJsonObject().addProperty("id", alternatives.get(0).getAsJsonObject().get("id").getAsString());
        assertThrows(IllegalArgumentException.class, () -> load(duplicate));

        JsonObject singleton = resource("woodcutting-v1.json");
        JsonArray one = new JsonArray();
        one.add(singleton.getAsJsonArray("requirementGroups").get(0).getAsJsonObject()
            .getAsJsonArray("alternatives").get(0));
        singleton.getAsJsonArray("requirementGroups").get(0).getAsJsonObject().add("alternatives", one);
        assertThrows(IllegalArgumentException.class, () -> load(singleton));

        JsonObject unresolved = resource("woodcutting-v1.json");
        unresolved.getAsJsonArray("methods").get(0).getAsJsonObject().getAsJsonArray("preparationAnyOf")
            .get(0).getAsJsonObject().addProperty("group", "requirement_group.missing");
        assertThrows(IllegalArgumentException.class, () -> load(unresolved));

        JsonObject safety = resource("woodcutting-v1.json");
        safety.getAsJsonArray("requirementGroups").get(0).getAsJsonObject().getAsJsonArray("alternatives")
            .get(0).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject()
            .addProperty("safetyRelevant", true);
        assertThrows(IllegalArgumentException.class, () -> load(safety));

        JsonObject orphan = resource("woodcutting-v1.json");
        orphan.getAsJsonArray("methods").forEach(method ->
            method.getAsJsonObject().add("preparationAnyOf", new JsonArray()));
        assertThrows(IllegalArgumentException.class, () -> load(orphan));
    }

    @Test
    public void completingOneSkillRemovesItsWholeFamilyAndMovesToAnother() throws Exception
    {
        List<MethodDefinition> methods = methods();
        GoalContext.Result both = context(RfdSkillCoverageTest.account(Map.of("COOKING", 20, "THIEVING", 25)), methods, Map.of());
        assertTrue(both.getMethods().stream().anyMatch(value -> value.getMethod().getActivity().equals("COOKING")
            && value.getGoalProgress() == 1));
        GoalContext.Result shifted = context(RfdSkillCoverageTest.account(Map.of("COOKING", 70, "THIEVING", 25)), methods, Map.of());
        assertTrue(shifted.getMethods().stream().filter(value -> value.getMethod().getActivity().equals("COOKING"))
            .allMatch(value -> value.getGoalProgress() == 0));
        assertEquals("THIEVING", shifted.getDecision().getBestActionable().orElseThrow().getMethod().getActivity());
    }

    @Test
    public void readyQuestAndReadyTrainingUseExistingDeterministicScores() throws Exception
    {
        AccountState base = RfdSkillCoverageTest.account(Map.of("COOKING", 31));
        Map<Integer, QuestStatus> quests = new HashMap<>(base.getQuests().getValue());
        quests.put(2310, QuestStatus.NOT_STARTED);
        AccountState state = base.toBuilder().quests(Observation.map(quests, "scenario quests", NOW).lastObserved()).build();
        List<MethodDefinition> methods = methods();
        Map<String, GoalContext.ExternalFactors> estimates = Map.of(
            "method.cooking.hosidius_mess_meat_pies", new GoalContext.ExternalFactors(0, 0, 1));
        GoalContext.Result context = context(state, methods, estimates);
        Map<String, Map<MethodScorer.Factor, Double>> quest = Map.of(PIRATE,
            Map.of(SETUP_COST, 0.0, TRANSITION_COST, 0.0, INVENTORY_DISRUPTION, 0.0,
                RISK, 0.0, UNCERTAINTY, 0.0));
        StrategicDecision.Result forward = new StrategicDecision().decide(context, quest);
        assertEquals(PIRATE, forward.getBestActionable().orElseThrow().getAction().getId());
        assertEquals(StrategicAction.Readiness.READY_TO_HANDOFF,
            forward.getBestActionable().orElseThrow().getAction().getReadiness());
        StrategicDecision.Candidate training = forward.getCandidates().stream()
            .filter(value -> value.getAction().getId().equals("method.cooking.hosidius_mess_meat_pies"))
            .findFirst().orElseThrow();
        assertEquals(StrategicAction.Readiness.READY, training.getAction().getReadiness());
        assertTrue(forward.getBestActionable().orElseThrow().getScore().orElseThrow().getTotal()
            > training.getScore().orElseThrow().getTotal());

        Collections.reverse(methods);
        StrategicDecision.Result reverse = new StrategicDecision().decide(context(state, methods, estimates), quest);
        assertEquals(forward.getCandidates().stream().map(value -> value.getAction().getId()).collect(Collectors.toList()),
            reverse.getCandidates().stream().map(value -> value.getAction().getId()).collect(Collectors.toList()));
    }

    private static Map<String, Observation<Double>> toolFacts(MethodDefinition method, int level)
    {
        Map<String, Observation<Double>> facts = new HashMap<>();
        facts.put("skill.woodcutting.level", known(level));
        facts.put("inventory.free_slots", known(1));
        method.getPreparationAnyOf().get(0).getAlternatives().forEach(alternative ->
            alternative.getRequirements().stream().filter(requirement -> requirement.getFact().startsWith("carried.item."))
                .forEach(requirement -> facts.put(requirement.getFact(), known(0))));
        return facts;
    }

    private static GoalContext.Result context(AccountState state, List<MethodDefinition> methods,
        Map<String, GoalContext.ExternalFactors> overrides) throws Exception
    {
        Map<String, GoalContext.ExternalFactors> factors = new HashMap<>();
        methods.forEach(method -> factors.put(method.getId(), new GoalContext.ExternalFactors(0, 0, 0)));
        factors.putAll(overrides);
        return new GoalContext().decide(ProductionGoalCatalogTest.load(), state, methods, factors, Map.of(), NOW);
    }

    private static GoalContext.MethodRelevance relevance(GoalContext.Result result, String id)
    {
        return result.getMethods().stream().filter(value -> value.getMethod().getId().equals(id)).findFirst().orElseThrow();
    }

    private static RecommendationDecision.CandidateResult method(GoalContext.Result result, String id)
    {
        return result.getDecision().getCandidates().stream().filter(value -> value.getMethod().getId().equals(id))
            .findFirst().orElseThrow();
    }

    private static MethodDefinition find(String id) throws Exception
    {
        return methods().stream().filter(method -> method.getId().equals(id)).findFirst().orElseThrow();
    }

    private static List<MethodDefinition> methods() throws Exception
    {
        return new ArrayList<>(ProductionSkillCoverageCatalogTest.load());
    }

    private static JsonObject resource(String file) throws Exception
    {
        try (InputStream input = RfdSkillCoverageV2Test.class.getResourceAsStream("/uimatlas/methods/" + file))
        {
            assertNotNull(input);
            return new JsonParser().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<MethodDefinition> load(JsonObject root) throws Exception
    {
        return new MethodDefinitionLoader().loadProduction(new StringReader(root.toString()),
            ProductionConstructionCatalogTest.canonicalItems());
    }

    private static Observation<Double> known(double value)
    {
        return Observation.verified(value, "scenario fact", NOW);
    }
}
