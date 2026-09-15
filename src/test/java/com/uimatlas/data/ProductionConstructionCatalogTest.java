package com.uimatlas.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.Requirement;
import com.uimatlas.recommendation.SetupScoringInputs;
import com.uimatlas.recommendation.SyntheticMethods;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import com.uimatlas.state.ItemContainerState;
import com.uimatlas.state.ItemStack;
import com.uimatlas.state.Observation;
import com.uimatlas.state.SkillState;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.jar.JarFile;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodEvaluator.Status.*;
import static org.junit.Assert.*;

public class ProductionConstructionCatalogTest
{
    private static final String CATALOG = "/uimatlas/methods/construction-v1.json";
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    public void threeReviewedMethodsLoadWithRangesAndNeutralStorageValue() throws Exception
    {
        List<MethodDefinition> methods = load();
        assertEquals(Set.of("method.construction.mahogany_homes.novice", "method.construction.mahogany_homes.adept",
            "method.construction.limestone_attack_stones"), methods.stream().map(MethodDefinition::getId).collect(Collectors.toSet()));
        assertEquals(3, methods.size());
        assertEquals(List.of(20.0, 50.0, 59.0), methods.stream()
            .map(m -> m.getHardRequirements().get(0).getTarget()).collect(Collectors.toList()));
        for (MethodDefinition method : methods)
        {
            assertEquals(MethodDefinition.DataKind.PRODUCTION, method.getDataKind());
            assertEquals("CONSTRUCTION", method.getActivity());
            assertTrue(benchmark(method).getMinimum() < benchmark(method).getMaximum());
            assertFalse(benchmark(method).getAssumptions().isBlank());
            assertEquals(0, method.getCosts().getStorageUnlockValue(), 0);
            assertTrue(method.getSources().size() >= 2);
            for (MethodDefinition.Source source : method.getSources())
            {
                assertTrue(source.getUrl().startsWith("https://"));
                assertEquals(LocalDate.of(2026, 9, 14), source.getReviewedAt());
                assertFalse(source.getNotes().isBlank());
            }
            assertThrows(UnsupportedOperationException.class, method.getSources()::clear);
            assertThrows(UnsupportedOperationException.class, method.getOptionalSetup()::clear);
        }
        assertEquals(60000, benchmark(methods.get(0)).getMinimum(), 0);
        assertEquals(72000, benchmark(methods.get(0)).getMaximum(), 0);
        assertEquals(75000, benchmark(methods.get(1)).getMinimum(), 0);
        assertEquals(85000, benchmark(methods.get(1)).getMaximum(), 0);
        assertEquals(70000, benchmark(methods.get(2)).getMinimum(), 0);
        assertEquals(80000, benchmark(methods.get(2)).getMaximum(), 0);
    }

    @Test
    public void realAccountLevelsGateEveryProductionMethodAndUnobservedHouseStaysUnknown() throws Exception
    {
        for (MethodDefinition method : load())
        {
            Requirement level = method.getHardRequirements().get(0);
            int minimum = (int) level.getTarget();
            for (int real : List.of(minimum - 1, minimum, 99))
            {
                AccountState state = AccountState.builder().skills(Observation.map(
                    Map.of("CONSTRUCTION", new SkillState(real, 99, 0)), "observed skills", NOW)).build();
                AccountStateFacts facts = new AccountStateFacts(state, NOW);
                assertEquals(real < minimum ? Requirement.Result.MISSING : Requirement.Result.SATISFIED,
                    level.evaluate(facts.get(level.getFact()), NOW));
                MethodEvaluator.Evaluation evaluation = new MethodEvaluator().evaluate(method, facts, NOW);
                assertEquals(real < minimum ? BLOCKED : UNKNOWN, evaluation.getStatus());
                assertTrue(evaluation.getUnknownRequirements().stream().anyMatch(r -> r.getFact().equals("capability.poh.owned")));
            }
        }
    }

    @Test
    public void verifiedPohCapabilityFlowsFromAccountStateIntoProductionEvaluation() throws Exception
    {
        MethodDefinition novice = load().get(0);
        AccountState state = AccountState.builder().loggedIn(true)
            .skills(Observation.map(Map.of("CONSTRUCTION", new SkillState(20, 20, 4470)),
                "observed skills", NOW))
            .capabilities(Observation.map(Map.of("capability.poh.owned", true),
                "RuneLite: server varbit POH_HOUSE_LOCATION", NOW))
            .inventory(Observation.verified(new ItemContainerState(Map.of(
                0, new ItemStack(2347, 1),
                1, new ItemStack(8794, 1),
                2, new ItemStack(2353, 1),
                3, new ItemStack(8778, 1))), "observed inventory", NOW))
            .build();
        AccountStateFacts facts = new AccountStateFacts(state, NOW);
        Observation<Double> poh = facts.get("capability.poh.owned");
        assertEquals(1.0, poh.getValue(), 0);
        assertEquals("RuneLite: server varbit POH_HOUSE_LOCATION", poh.getSource());
        assertEquals(Observation.Confidence.VERIFIED_NOW, poh.getConfidence());
        assertEquals(24.0, facts.get("inventory.free_slots").getValue(), 0);
        assertEquals(1.0, facts.get("inventory.item.8778.quantity").getValue(), 0);
        assertEquals(25.0, facts.get("inventory.item.8778.usable_slots").getValue(), 0);
        assertEquals(AVAILABLE, new MethodEvaluator().evaluate(novice, facts, NOW).getStatus());
        assertEquals(UNKNOWN, new MethodEvaluator().evaluate(novice, facts, NOW.plusSeconds(301)).getStatus());

        for (MethodDefinition method : load())
        {
            Requirement requirement = method.getHardRequirements().stream()
                .filter(r -> r.getFact().equals("capability.poh.owned")).findFirst().orElseThrow();
            assertEquals(Requirement.Result.SATISFIED, requirement.evaluate(poh, NOW));
        }
    }

    @Test
    public void knownMissingHardGateBlocksAndOrdinaryToolsArePreparation() throws Exception
    {
        for (MethodDefinition method : load())
        {
            Map<String, Observation<Double>> facts = ready(method);
            facts.put("capability.poh.owned", known(0));
            assertEquals(BLOCKED, evaluate(method, facts).getStatus());
            facts = ready(method);
            Requirement tool = method.getSetupItems().get(0);
            facts.put(tool.getFact(), known(0));
            MethodEvaluator.Evaluation evaluation = evaluate(method, facts);
            assertEquals(NEEDS_PREP, evaluation.getStatus());
            assertEquals(List.of(tool), evaluation.getMissingPreparation());
            facts.remove("capability.poh.owned");
            assertEquals(UNKNOWN, evaluate(method, facts).getStatus());
        }
    }

    @Test
    public void optionalOptimizationAbsenceOrUnknownNeverBlocksOrAddsPreparation() throws Exception
    {
        for (MethodDefinition method : load())
        {
            Map<String, Observation<Double>> facts = ready(method);
            Map<?, ?> setupBefore = new SetupScoringInputs().derive(method, facts::get, NOW).getInputs().orElseThrow();
            for (Observation<Double> optional : List.of(known(0), Observation.<Double>unknown()))
            {
                method.getOptionalSetup().forEach(r -> facts.put(r.getFact(), optional));
                MethodEvaluator.Evaluation evaluation = evaluate(method, facts);
                assertEquals(AVAILABLE, evaluation.getStatus());
                assertTrue(evaluation.getMissingPreparation().isEmpty());
                assertTrue(evaluation.getUnknownRequirements().isEmpty());
                assertEquals(setupBefore, new SetupScoringInputs().derive(method, facts::get, NOW).getInputs().orElseThrow());
            }
        }
    }

    @Test
    public void bagAbsenceAndStalenessCannotBecomeRoutinePreparation() throws Exception
    {
        MethodDefinition method = load().get(2);
        Requirement bag = method.getHardRequirements().stream().filter(Requirement::isSafetyRelevant).findFirst().get();
        Map<String, Observation<Double>> facts = ready(method);
        facts.put(bag.getFact(), known(0));
        assertEquals(BLOCKED, evaluate(method, facts).getStatus());
        assertFalse(evaluate(method, facts).getMissingPreparation().contains(bag));
        facts.put(bag.getFact(), known(1).lastObserved());
        assertEquals(UNKNOWN, evaluate(method, facts).getStatus());
        facts.put(bag.getFact(), Observation.verified(1.0, "old inventory", NOW.minusSeconds(301)));
        assertEquals(UNKNOWN, evaluate(method, facts).getStatus());
    }

    @Test
    public void aDifferentExactItemDoesNotSatisfyTheRealMaterialRequirement() throws Exception
    {
        List<MethodDefinition> methods = load();
        Requirement oak = methods.get(0).getConsumes().get(0);
        Requirement teak = methods.get(1).getConsumes().get(0);
        int teakId = Integer.parseInt(teak.getFact().split("\\.")[2]);
        AccountState state = AccountState.builder().inventory(Observation.verified(
            new ItemContainerState(Map.of(0, new ItemStack(teakId, 1))), "observed inventory", NOW)).build();
        AccountStateFacts facts = new AccountStateFacts(state, NOW);
        assertEquals(Requirement.Result.MISSING, oak.evaluate(facts.get(oak.getFact()), NOW));
        assertEquals(Requirement.Result.SATISFIED, teak.evaluate(facts.get(teak.getFact()), NOW));
        assertEquals(UNKNOWN, new MethodEvaluator().evaluate(methods.get(1), facts, NOW).getStatus());
        AccountState noted = state.toBuilder().inventory(Observation.verified(new ItemContainerState(
            Map.of(0, new ItemStack(ItemID.Cert.PLANK_OAK, 100))), "observed noted supplies", NOW)).build();
        AccountStateFacts notedFacts = new AccountStateFacts(noted, NOW);
        assertEquals(Requirement.Result.MISSING, oak.evaluate(notedFacts.get(oak.getFact()), NOW));
    }

    @Test
    public void limestoneCapacityIsASeparateUnknownCheckNotTwentyExtraEmptySlots() throws Exception
    {
        MethodDefinition method = load().get(2);
        Map<String, Observation<Double>> facts = ready(method);
        facts.put("inventory.free_slots", known(0));
        assertEquals(AVAILABLE, evaluate(method, facts).getStatus());
        facts.remove("inventory.item.3420.usable_slots");
        assertEquals(UNKNOWN, evaluate(method, facts).getStatus());
        facts.put("inventory.item.3420.usable_slots", known(0));
        assertEquals(NEEDS_PREP, evaluate(method, facts).getStatus());
    }

    @Test
    public void methodKnowledgeIsNotEmbeddedInProductionJava() throws Exception
    {
        List<MethodDefinition> methods = load();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java")))
        {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()))
            {
                String source = Files.readString(path);
                for (MethodDefinition method : methods)
                {
                    assertFalse(path.toString(), source.contains(method.getId()));
                    assertFalse(path.toString(), source.contains(method.getDisplayName()));
                }
            }
        }
    }

    @Test
    public void productionAndSyntheticCannotLoadThroughEachOthersEntryPoint() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> new MethodDefinitionLoader().loadSynthetic(new StringReader(json())));
        assertThrows(IllegalArgumentException.class, () -> new MethodDefinitionLoader().loadProduction(
            new StringReader(SyntheticMethods.json()), canonicalItems()));
        assertTrue(SyntheticMethods.load().stream().allMatch(m -> m.getDataKind() == MethodDefinition.DataKind.SYNTHETIC_TEST_ONLY));
        mutate(root -> root.addProperty("dataKind", "SYNTHETIC_TEST_ONLY"), "dataKind");
        mutate(root -> first(root).addProperty("reason", "Synthetic fixture"), "test marker");
        mutate(root -> first(root).getAsJsonArray("sources").get(0).getAsJsonObject().addProperty("notes", "test only"), "test marker");
    }

    @Test
    public void invalidProductionMetadataReferencesAndRangesAreRejected() throws Exception
    {
        mutate(root -> root.getAsJsonArray("methods").add(first(root).deepCopy()), "duplicate method ID");
        mutate(root -> first(root).addProperty("surprise", true), "unknown fields");
        mutate(root -> first(root).remove("sources"), "missing fields");
        mutate(root -> first(root).add("sources", new com.google.gson.JsonArray()), "source metadata");
        mutate(root -> source(root).addProperty("reviewedAt", "2026-02-30"), "review date");
        mutate(root -> source(root).addProperty("url", "relative/page"), "HTTPS");
        mutate(root -> source(root).addProperty("notes", " "), "nonblank");
        mutate(root -> first(root).getAsJsonArray("hardRequirements").get(0).getAsJsonObject().addProperty("fact", "capability.missing"), "unresolved fact");
        for (double invalid : List.of(0.0, 100.0, 20.5))
        {
            mutate(root -> first(root).getAsJsonArray("hardRequirements").get(0).getAsJsonObject().addProperty("target", invalid), "level range");
        }
        mutate(root -> benchmarkJson(root).getAsJsonObject("xpRate").addProperty("minimum", -1), "minimum");
        mutate(root -> benchmarkJson(root).getAsJsonObject("xpRate").addProperty("maximum", 1), "maximum");
        mutate(root -> benchmarkJson(root).getAsJsonObject("xpRate").addProperty("maximum", "Infinity"), "number");
        mutate(root -> first(root).getAsJsonObject("freeInventorySlots").addProperty("target", 29), "slot range");
        mutate(root -> first(root).getAsJsonObject("costs").addProperty("transitionMinutes", -1), "transitionMinutes");
        mutate(root -> first(root).getAsJsonArray("optionalSetup").get(0).getAsJsonObject().addProperty("safetyRelevant", true), "safety gates");
        mutate(root -> first(root).getAsJsonArray("optionalSetup").add(first(root).getAsJsonArray("setupItems").get(0).deepCopy()), "distinct");
    }

    @Test
    public void allReferencedItemsMustExistInTheCanonicalBoundary() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> new MethodDefinitionLoader().loadProduction(new StringReader(json()), Set.of()));
        for (String invalid : List.of("inventory.item.08778.quantity", "inventory.item.2147483648.quantity",
            "inventory.item.99999999999999999999.quantity", "inventory.item.2147483647.quantity", "inventory.item.1", "skill.CONSTRUCTION.level", "bad-id"))
        {
            mutate(root -> root.getAsJsonArray("facts").get(0).getAsJsonObject().addProperty("id", invalid), "ID");
        }
        Set<Integer> boundary = canonicalItems();
        JsonObject root = new JsonParser().parse(json()).getAsJsonObject();
        for (com.google.gson.JsonElement fact : root.getAsJsonArray("facts"))
        {
            String id = fact.getAsJsonObject().get("id").getAsString();
            if (id.contains(".item."))
            {
                int itemId = Integer.parseInt(id.split("\\.")[2]);
                assertTrue(boundary.contains(itemId));
                Set<Integer> missingOne = new HashSet<>(boundary);
                missingOne.remove(itemId);
                assertThrows(IllegalArgumentException.class,
                    () -> new MethodDefinitionLoader().loadProduction(new StringReader(root.toString()), missingOne));
            }
        }
    }

    @Test
    public void builtJarContainsOnlyProductionMethodsAndNoTestClasses() throws Exception
    {
        try (JarFile jar = new JarFile(System.getProperty("pluginJar"));
            Stream<Path> classes = Files.walk(Path.of(System.getProperty("testClassesDirectory"))))
        {
            Set<String> entries = jar.stream().map(e -> e.getName()).collect(Collectors.toSet());
            assertTrue(entries.contains(CATALOG.substring(1)));
            assertFalse(entries.stream().anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("synthetic")));
            Path base = Path.of(System.getProperty("testClassesDirectory"));
            classes.filter(Files::isRegularFile).forEach(path -> assertFalse(entries.contains(base.relativize(path).toString().replace('\\', '/'))));
            List<String> catalogs = entries.stream().filter(name -> name.startsWith("uimatlas/methods/") && name.endsWith(".json"))
                .sorted().collect(Collectors.toList());
            assertEquals(ProductionSkillCoverageCatalogTest.CATALOG_COUNTS.keySet().stream()
                .map(name -> "uimatlas/methods/" + name).sorted().collect(Collectors.toList()), catalogs);
            assertTrue(entries.contains("uimatlas/methods/catalogs.txt"));
            assertTrue(entries.contains("uimatlas/goals/catalogs.txt"));
            assertTrue(entries.contains("uimatlas/items/catalog-item-ids-1.12.38.txt"));
            try (java.io.BufferedReader index = new java.io.BufferedReader(new InputStreamReader(
                jar.getInputStream(jar.getJarEntry("uimatlas/methods/catalogs.txt")), StandardCharsets.UTF_8)))
            {
                assertEquals(catalogs, index.lines().sorted().collect(Collectors.toList()));
            }
            for (String path : catalogs)
            {
                try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(jar.getJarEntry(path)), StandardCharsets.UTF_8))
                {
                    int expected = ProductionSkillCoverageCatalogTest.CATALOG_COUNTS.get(path.substring("uimatlas/methods/".length()));
                    assertEquals(expected, new MethodDefinitionLoader().loadProduction(reader, canonicalItems()).size());
                }
            }
        }
    }

    // Only public compile-time API constants are inspected in tests; no runtime reflection or network.
    static Set<Integer> canonicalItems() throws Exception
    {
        Set<Integer> ids = new HashSet<>();
        for (Field field : ItemID.class.getFields())
        {
            if (field.getType() == int.class && Modifier.isStatic(field.getModifiers()))
            {
                ids.add(field.getInt(null));
            }
        }
        return ids;
    }

    static String json() throws Exception
    {
        try (java.io.InputStream input = ProductionConstructionCatalogTest.class.getResourceAsStream(CATALOG))
        {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<MethodDefinition> load() throws Exception
    {
        return new MethodDefinitionLoader().loadProduction(new StringReader(json()), canonicalItems());
    }

    static Map<String, Observation<Double>> ready(MethodDefinition method)
    {
        Map<String, Observation<Double>> facts = new HashMap<>();
        Stream.of(method.getHardRequirements(), method.getPreparation(), method.getSetupItems(), method.getConsumes())
            .flatMap(List::stream).forEach(r -> facts.put(r.getFact(), known(r.getTarget())));
        facts.put("inventory.free_slots", known(0));
        return facts;
    }

    private static Observation<Double> known(double value)
    {
        return Observation.verified(value, "scenario observation", NOW);
    }

    private static MethodEvaluator.Evaluation evaluate(MethodDefinition method, Map<String, Observation<Double>> facts)
    {
        return new MethodEvaluator().evaluate(method, facts, NOW);
    }

    static void mutate(Consumer<JsonObject> mutation, String expected) throws Exception
    {
        JsonObject root = new JsonParser().parse(json()).getAsJsonObject();
        mutation.accept(root);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new MethodDefinitionLoader().loadProduction(new StringReader(root.toString()), canonicalItems()));
        assertTrue(exception.getMessage(), exception.getMessage().contains(expected));
    }

    static JsonObject first(JsonObject root)
    {
        return root.getAsJsonArray("methods").get(0).getAsJsonObject();
    }

    private static MethodDefinition.XpRate benchmark(MethodDefinition method)
    {
        assertNull(method.getXpRate());
        return method.getEfficiencyProfiles().stream().flatMap(p -> p.getXpRate().stream()).findFirst().orElseThrow();
    }

    private static JsonObject benchmarkJson(JsonObject root)
    {
        return first(root).getAsJsonArray("efficiencyProfiles").get(2).getAsJsonObject();
    }

    private static JsonObject source(JsonObject root)
    {
        return first(root).getAsJsonArray("sources").get(0).getAsJsonObject();
    }
}
