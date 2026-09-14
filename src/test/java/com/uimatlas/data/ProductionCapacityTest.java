package com.uimatlas.data;

import com.google.gson.JsonObject;
import com.uimatlas.recommendation.*;
import com.uimatlas.state.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.uimatlas.data.ProductionConstructionCatalogTest.*;
import static com.uimatlas.recommendation.MethodEvaluator.Status.*;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static org.junit.Assert.*;

public class ProductionCapacityTest
{
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final Map<MethodScorer.Factor, Double> EXPLICIT = Map.of(
        GOAL_PROGRESS, 0.5, STORAGE_UNLOCK_VALUE, 0.0, RISK, 0.0, UNCERTAINTY, 0.0);

    @Test
    public void fiveFreeSlotsAndUnknownHelpersKeepMethodAvailableButEfficiencyUnresolved() throws Exception
    {
        for (MethodDefinition method : load().subList(0, 2))
        {
            Map<String, Observation<Double>> facts = constrained(method);
            MethodEfficiency.Result result = derive(method, facts);
            assertEquals(AVAILABLE, result.getEvaluation().getStatus());
            assertEquals(Requirement.Result.MISSING, result.getWorkingCapacity().get(0).getResult());
            assertTrue(result.getSelected().isEmpty());
            assertTrue(result.getTrustedXpRate().isEmpty());
            assertTrue(result.withExplicitFactors(EXPLICIT).isEmpty());
            assertTrue(new SetupScoringInputs().derive(method, facts::get, NOW).getInputs().isPresent());
            assertTrue(checks(result).stream().anyMatch(c -> c.getRequirement().getFact().startsWith("container.")
                && c.getResult() == Requirement.Result.UNKNOWN));
            assertTrue(checks(result).stream().anyMatch(c -> c.getRequirement().getFact().equals("inventory.item.24882.quantity")
                && c.getResult() == Requirement.Result.UNKNOWN));
        }
    }

    @Test
    public void verifiedMatchingPayloadImprovesBatchingAndReusesExistingScorer() throws Exception
    {
        for (MethodDefinition method : load().subList(0, 2))
        {
            Map<String, Observation<Double>> facts = constrained(method);
            MethodDefinition.EfficiencyProfile ordinary = profile(method, "profile.ordinary_batch");
            satisfy(ordinary, facts);
            facts.put(method.getWorkingCapacity().get(0).getFact(), known(19));
            MethodEfficiency.Result before = derive(method, facts);
            assertEquals(ordinary, before.getSelected().orElseThrow());
            double beforeScore = score(method, facts, before);
            Map<?, ?> setup = new SetupScoringInputs().derive(method, facts::get, NOW).getInputs().orElseThrow();
            MethodDefinition.EfficiencyProfile helper = profile(method, "profile.loaded_helper");
            satisfy(helper, facts);
            MethodEfficiency.Result after = derive(method, facts);
            assertEquals(helper, after.getSelected().orElseThrow());
            assertTrue(score(method, facts, after) > beforeScore);
            assertEquals(setup, new SetupScoringInputs().derive(method, facts::get, NOW).getInputs().orElseThrow());
            assertTrue(after.getTrustedXpRate().isEmpty());
            assertTrue(after.getProfiles().stream().filter(m -> m.getProfile().equals(helper)).findFirst().orElseThrow().isApplicable());
            MethodEfficiency.Check payload = checks(after).stream().filter(c -> c.getRequirement().getFact().startsWith("container.")
                && c.getResult() == Requirement.Result.SATISFIED).findFirst().orElseThrow();
            assertEquals("capacity scenario", payload.getObservation().getSource());
            assertEquals(NOW, payload.getObservation().getObservedAt());
            // Same five free slots, only one loose plank: the helper still establishes a better batch profile.
            facts.put(method.getConsumes().get(0).getFact(), known(1));
            facts.put(method.getWorkingCapacity().get(0).getFact(), known(6));
            assertEquals(helper, derive(method, facts).getSelected().orElseThrow());
        }
    }

    @Test
    public void optionalAbsenceAndUnknownNeverBecomeMethodGates() throws Exception
    {
        for (MethodDefinition method : load().subList(0, 2))
        {
            for (Observation<Double> value : List.of(known(0), Observation.<Double>unknown()))
            {
                Map<String, Observation<Double>> facts = constrained(method);
                method.getOptionalSetup().forEach(r -> facts.put(r.getFact(), value));
                MethodEfficiency.Result result = derive(method, facts);
                assertEquals(AVAILABLE, result.getEvaluation().getStatus());
                assertTrue(result.getSelected().isEmpty());
                assertTrue(result.getTrustedXpRate().isEmpty());
                assertTrue(checks(result).stream().anyMatch(c -> c.getResult() != Requirement.Result.SATISFIED));
            }
        }
    }

    @Test
    public void unknownContentsSpareSpaceWrongPlanksAndStaleObservationsCannotEarnHelperProfile() throws Exception
    {
        MethodDefinition method = load().get(0);
        MethodDefinition.EfficiencyProfile helper = profile(method, "profile.loaded_helper");
        String payload = helper.getRequirements().get(1).getFact();
        Map<String, Observation<Double>> facts = constrained(method);
        satisfy(helper, facts);
        facts.put("container.plank_sack.free_capacity", known(28));
        facts.put("container.plank_sack.contents.8780.quantity", known(28));
        for (Observation<Double> invalid : List.of(Observation.<Double>unknown(), known(0), known(27), known(28).lastObserved(),
            Observation.verified(28.0, "expired capacity", NOW.minusSeconds(301)),
            Observation.verified(28.0, "future capacity", NOW.plusSeconds(1)), known(28.5), known(Double.NaN), known(-1)))
        {
            facts.put(payload, invalid);
            MethodEfficiency.Result result = derive(method, facts);
            assertTrue(result.getSelected().isEmpty());
            assertEquals(AVAILABLE, result.getEvaluation().getStatus());
            assertTrue(checks(result).stream().anyMatch(c -> c.getRequirement().getFact().equals(payload)
                && c.getObservation().equals(invalid) && c.getResult() != Requirement.Result.SATISFIED));
        }
        facts.put(payload, Observation.verified(28.0, "boundary capacity", NOW.minusSeconds(300)));
        assertEquals(helper, derive(method, facts).getSelected().orElseThrow());
    }

    @Test
    public void publishedXpRequiresEveryProfileAssumptionAndReadyMethod() throws Exception
    {
        for (MethodDefinition method : load())
        {
            MethodDefinition.EfficiencyProfile benchmark = method.getEfficiencyProfiles().stream()
                .filter(p -> p.getXpRate().isPresent()).findFirst().orElseThrow();
            Map<String, Observation<Double>> facts = ready(method);
            satisfy(benchmark, facts);
            assertEquals(benchmark.getXpRate(), derive(method, facts).getTrustedXpRate());
            for (Requirement requirement : benchmark.getRequirements())
            {
                Observation<Double> previous = facts.remove(requirement.getFact());
                MethodEfficiency.Result unresolved = derive(method, facts);
                assertTrue(requirement.getFact(), unresolved.getTrustedXpRate().isEmpty());
                assertTrue(checks(unresolved).stream().anyMatch(c -> c.getRequirement().equals(requirement)
                    && c.getResult() == Requirement.Result.UNKNOWN));
                facts.put(requirement.getFact(), previous);
            }
            facts.put("capability.poh.owned", known(0));
            assertEquals(BLOCKED, derive(method, facts).getEvaluation().getStatus());
            assertTrue(derive(method, facts).getSelected().isEmpty());
        }
    }

    @Test
    public void limestoneFiveFreeSlotsWithTenBricksHasFivePositionPrepShortfall() throws Exception
    {
        MethodDefinition method = load().get(2);
        Map<String, Observation<Double>> facts = ready(method);
        Map<Integer, ItemStack> inventory = new HashMap<>();
        for (int slot = 0; slot < 23; slot++)
        {
            inventory.put(slot, new ItemStack(slot < 10 ? 3420 : 2347, 1));
        }
        AccountStateFacts observed = new AccountStateFacts(AccountState.builder().inventory(
            Observation.verified(new ItemContainerState(inventory), "ordinary inventory", NOW)).build(), NOW);
        facts.put("inventory.free_slots", observed.get("inventory.free_slots"));
        facts.put("inventory.item.3420.usable_slots", observed.get("inventory.item.3420.usable_slots"));
        MethodDefinition.EfficiencyProfile benchmark = method.getEfficiencyProfiles().get(0);
        // Give every optional benchmark predicate a favorable synthetic value: required capacity still dominates.
        satisfy(benchmark, facts);
        MethodEfficiency.Result result = derive(method, facts);
        assertEquals(5, observed.get("inventory.free_slots").getValue(), 0);
        assertEquals(NEEDS_PREP, result.getEvaluation().getStatus());
        Requirement shortfall = result.getEvaluation().getMissingPreparation().get(0);
        assertEquals("inventory.item.3420.usable_slots", shortfall.getFact());
        assertEquals(5, shortfall.getTarget() - facts.get(shortfall.getFact()).getValue(), 0);
        assertTrue(result.getProfiles().get(0).isApplicable());
        assertTrue(result.getSelected().isEmpty());
        assertTrue(result.getTrustedXpRate().isEmpty());
        assertTrue(new SetupScoringInputs().derive(method, facts::get, NOW).getInputs().orElseThrow().get(SETUP_COST) > 0);
        facts.put(shortfall.getFact(), known(20));
        facts.put("inventory.free_slots", known(0));
        assertEquals(AVAILABLE, derive(method, facts).getEvaluation().getStatus());
    }

    @Test
    public void factProfileAndPredicateOrderDoNotChangeSelectionOrDiagnostics() throws Exception
    {
        JsonObject root = new com.google.gson.JsonParser().parse(json()).getAsJsonObject();
        MethodDefinition method = load().get(0);
        Map<String, Observation<Double>> facts = ready(method);
        method.getEfficiencyProfiles().forEach(p -> satisfy(p, facts));
        MethodEfficiency.Result expected = derive(method, facts);
        List<String> keys = new ArrayList<>(facts.keySet());
        Collections.reverse(keys);
        Map<String, Observation<Double>> reversed = new LinkedHashMap<>();
        keys.forEach(k -> reversed.put(k, facts.get(k)));
        for (com.google.gson.JsonElement element : first(root).getAsJsonArray("efficiencyProfiles"))
        {
            reverse(element.getAsJsonObject(), "requirements");
        }
        reverse(first(root), "efficiencyProfiles");
        reverse(root, "facts");
        MethodDefinition reordered = new MethodDefinitionLoader().loadProduction(
            new java.io.StringReader(root.toString()), canonicalItems()).get(0);
        MethodEfficiency.Result actual = derive(reordered, reversed);
        assertEquals(expected.getSelected().orElseThrow().getId(), actual.getSelected().orElseThrow().getId());
        assertEquals(expected.getTrustedXpRate(), actual.getTrustedXpRate());
        for (int i = 0; i < expected.getProfiles().size(); i++)
        {
            assertEquals(expected.getProfiles().get(i).getChecks(), actual.getProfiles().get(i).getChecks());
        }
    }

    @Test
    public void strictProfileValidationRejectsAmbiguityAndFavorableDefaults() throws Exception
    {
        mutate(r -> r.addProperty("schemaVersion", 2), "unsupported version");
        mutate(r -> capacityFact(r).remove("capacitySemantics"), "required only");
        mutate(r -> capacityFact(r).addProperty("capacitySemantics", "ITEM_QUANTITY_IS_SLOTS"), "unsupported capacity semantics");
        mutate(r -> r.getAsJsonArray("facts").get(0).getAsJsonObject()
            .addProperty("capacitySemantics", "FREE_PLUS_OBSERVED_EXACT_ITEM_SLOTS"), "required only");
        mutate(r -> first(r).add("xpRate", profileJson(r).getAsJsonObject("xpRate")), "unknown fields");
        mutate(r -> profileJson(r).addProperty("priority", 10), "ambiguous");
        mutate(r -> profileJson(r).addProperty("id", "profile.ordinary_batch"), "duplicate profile ID");
        mutate(r -> profileJson(r).addProperty("priority", 0.5), "integer");
        mutate(r -> profileJson(r).addProperty("efficiency", 1.01), "range");
        mutate(r -> profileJson(r).remove("efficiency"), "missing fields");
        mutate(r -> profileJson(r).addProperty("unexpected", true), "unknown fields");
        mutate(r -> profileJson(r).add("requirements", new com.google.gson.JsonArray()), "verified requirements");
        mutate(r -> profileJson(r).getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("allowLastObserved", true), "current verification");
        mutate(r -> profileJson(r).getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("safetyRelevant", true), "safety gates");
        mutate(r -> profileJson(r).getAsJsonArray("requirements").add(profileJson(r).getAsJsonArray("requirements").get(0).deepCopy()), "duplicate diagnostic fact");
        mutate(r -> first(r).getAsJsonArray("workingCapacity").get(0).getAsJsonObject().addProperty("target", 29), "slot range");
        mutate(r -> profileJson(r).getAsJsonArray("requirements").get(1).getAsJsonObject().addProperty("target", 0.5), "integer container");
        mutate(r -> profileJson(r).getAsJsonArray("requirements").get(1).getAsJsonObject().addProperty("fact", "container.plank_sack.contents.999999.quantity"), "unresolved fact");
    }

    @Test
    public void compositionRejectsExplicitEfficiencyAndResultsAreImmutable() throws Exception
    {
        MethodDefinition method = load().get(0);
        Map<String, Observation<Double>> facts = constrained(method);
        satisfy(profile(method, "profile.loaded_helper"), facts);
        MethodEfficiency.Result result = derive(method, facts);
        Map<MethodScorer.Factor, Double> inputs = new HashMap<>(EXPLICIT);
        inputs.put(METHOD_EFFICIENCY, 1.0);
        assertThrows(IllegalArgumentException.class, () -> result.withExplicitFactors(inputs));
        assertThrows(UnsupportedOperationException.class, result.getProfiles()::clear);
        assertThrows(UnsupportedOperationException.class, result.getWorkingCapacity()::clear);
        assertThrows(UnsupportedOperationException.class, result.getProfiles().get(0).getChecks()::clear);
        assertThrows(UnsupportedOperationException.class, method.getEfficiencyProfiles()::clear);
        assertThrows(UnsupportedOperationException.class, result.getSelected().orElseThrow().getRequirements()::clear);
        assertThrows(UnsupportedOperationException.class, result.withExplicitFactors(EXPLICIT).orElseThrow()::clear);
    }

    @Test
    public void hardCapacityPredicateCanBlockIndependentlyOfWorkingAndEfficientCapacity() throws Exception
    {
        JsonObject root = new com.google.gson.JsonParser().parse(json()).getAsJsonObject();
        JsonObject hardCapacity = first(root).getAsJsonObject("freeInventorySlots").deepCopy();
        hardCapacity.addProperty("target", 6);
        first(root).getAsJsonArray("hardRequirements").add(hardCapacity);
        MethodDefinition method = new MethodDefinitionLoader().loadProduction(
            new java.io.StringReader(root.toString()), canonicalItems()).get(0);
        Map<String, Observation<Double>> facts = constrained(method);
        satisfy(profile(method, "profile.loaded_helper"), facts);
        MethodEfficiency.Result result = derive(method, facts);
        assertEquals(BLOCKED, result.getEvaluation().getStatus());
        assertEquals("inventory.free_slots", result.getEvaluation().getBlockers().get(0).getFact());
        assertTrue(result.getSelected().isEmpty());
        facts.put("inventory.free_slots", known(6));
        assertEquals(AVAILABLE, derive(method, facts).getEvaluation().getStatus());
    }

    @Test
    public void containerAndCapacityFactDeclarationsAreStrictAndCanonical() throws Exception
    {
        for (String invalid : List.of("container.example.contents.08778.quantity", "container.example.contents.2147483648.quantity",
            "container.example.contents.2147483647.quantity", "container.example.contents.8778.count",
            "container.example.total", "inventory.item.08778.usable_slots", "equipment.item.8778.usable_slots"))
        {
            mutate(r -> r.getAsJsonArray("facts").get(0).getAsJsonObject().addProperty("id", invalid), "ID");
        }
        // A content-only item reference still uses the caller's canonical item boundary.
        JsonObject root = new com.google.gson.JsonParser().parse(json()).getAsJsonObject();
        JsonObject fact = new JsonObject();
        fact.addProperty("id", "container.example.contents.0.quantity");
        root.getAsJsonArray("facts").add(fact);
        java.util.Set<Integer> boundary = new java.util.HashSet<>(canonicalItems());
        boundary.remove(0);
        assertThrows(IllegalArgumentException.class, () -> new MethodDefinitionLoader().loadProduction(
            new java.io.StringReader(root.toString()), boundary));
        fact.addProperty("id", "container.example.owned");
        assertEquals(3, new MethodDefinitionLoader().loadProduction(new java.io.StringReader(root.toString()), boundary).size());
        fact.addProperty("id", "container.example.free_capacity");
        assertEquals(3, new MethodDefinitionLoader().loadProduction(new java.io.StringReader(root.toString()), boundary).size());
    }

    private static double score(MethodDefinition method, Map<String, Observation<Double>> facts, MethodEfficiency.Result efficiency)
    {
        Map<MethodScorer.Factor, Double> inputs = new SetupScoringInputs().derive(method, facts::get, NOW)
            .withExplicitFactors(efficiency.withExplicitFactors(EXPLICIT).orElseThrow()).orElseThrow();
        return new MethodScorer().score(efficiency.getEvaluation(), inputs).orElseThrow().getTotal();
    }

    private static Map<String, Observation<Double>> constrained(MethodDefinition method)
    {
        Map<String, Observation<Double>> facts = ready(method);
        facts.put("inventory.free_slots", known(5));
        facts.put(method.getWorkingCapacity().get(0).getFact(), known(6));
        return facts;
    }

    private static Observation<Double> known(double value)
    {
        return Observation.verified(value, "capacity scenario", NOW);
    }

    private static MethodEfficiency.Result derive(MethodDefinition method, Map<String, Observation<Double>> facts)
    {
        return new MethodEfficiency().derive(method, facts::get, NOW);
    }

    private static MethodDefinition.EfficiencyProfile profile(MethodDefinition method, String id)
    {
        return method.getEfficiencyProfiles().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    private static void satisfy(MethodDefinition.EfficiencyProfile profile, Map<String, Observation<Double>> facts)
    {
        profile.getRequirements().forEach(r -> facts.put(r.getFact(), known(r.getTarget())));
    }

    private static List<MethodEfficiency.Check> checks(MethodEfficiency.Result result)
    {
        return result.getProfiles().stream().flatMap(p -> p.getChecks().stream()).collect(java.util.stream.Collectors.toList());
    }

    private static JsonObject profileJson(JsonObject root)
    {
        return first(root).getAsJsonArray("efficiencyProfiles").get(2).getAsJsonObject();
    }

    private static JsonObject capacityFact(JsonObject root)
    {
        for (com.google.gson.JsonElement value : root.getAsJsonArray("facts"))
        {
            JsonObject fact = value.getAsJsonObject();
            if (fact.get("id").getAsString().equals("inventory.item.8778.usable_slots"))
            {
                return fact;
            }
        }
        throw new AssertionError("capacity fact missing");
    }

    private static void reverse(JsonObject object, String key)
    {
        List<com.google.gson.JsonElement> elements = new ArrayList<>();
        object.getAsJsonArray(key).forEach(elements::add);
        Collections.reverse(elements);
        com.google.gson.JsonArray reversed = new com.google.gson.JsonArray();
        elements.forEach(reversed::add);
        object.add(key, reversed);
    }
}
