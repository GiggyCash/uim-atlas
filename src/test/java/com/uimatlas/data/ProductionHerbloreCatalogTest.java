package com.uimatlas.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.ResourceFlow;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductionHerbloreCatalogTest
{
    static final String CATALOG = "/uimatlas/methods/herblore-v1.json";

    @Test
    public void sixReviewedProductionMethodsLoadStrictly() throws Exception
    {
        List<MethodDefinition> methods = load();
        assertEquals(List.of(
            "method.herblore.clean_guam",
            "method.herblore.attack_potion",
            "method.herblore.energy_potion",
            "method.herblore.prayer_potion",
            "method.herblore.stamina_potion",
            "method.herblore.mixology.mammoth_might_order"),
            methods.stream().map(MethodDefinition::getId).collect(Collectors.toList()));
        for (MethodDefinition method : methods)
        {
            assertEquals(MethodDefinition.DataKind.PRODUCTION, method.getDataKind());
            assertEquals("HERBLORE", method.getActivity());
            assertNull(method.getXpRate());
            assertTrue(method.getResourceFlow().isPresent());
            assertFalse(method.getResourceFlow().orElseThrow().getInputs().isEmpty());
            assertFalse(method.getResourceFlow().orElseThrow().getOutputs().isEmpty());
            assertFalse(method.getEfficiencyProfiles().isEmpty());
            assertTrue(method.getEfficiencyProfiles().stream().allMatch(profile -> profile.getXpRate().isEmpty()));
            assertTrue(method.getSources().size() >= 3);
            method.getSources().forEach(source ->
            {
                assertEquals(LocalDate.of(2026, 9, 14), source.getReviewedAt());
                assertTrue(source.getUrl().startsWith("https://"));
                assertFalse(source.getNotes().isBlank());
            });
        }
        MethodDefinition stamina = methods.get(4);
        assertEquals(ResourceFlow.SlotSemantics.ONE_SHARED_STACK,
            stamina.getResourceFlow().orElseThrow().getInputs().get(1).getSlotSemantics());
        MethodDefinition mixology = methods.get(5);
        assertEquals(30, mixology.getResourceFlow().orElseThrow().getInputs().get(0).getQuantity());
        assertEquals(ResourceFlow.SlotSemantics.NO_INVENTORY_SLOT,
            mixology.getResourceFlow().orElseThrow().getInputs().get(0).getSlotSemantics());
    }

    @Test
    public void malformedOrAmbiguousFlowsAreRejected() throws Exception
    {
        mutate(root -> root.addProperty("schemaVersion", 3), "unknown fields");
        mutate(root -> consume(root).remove("slotSemantics"), "missing fields");
        mutate(root -> consume(root).addProperty("slotSemantics", "INFER_FROM_QUANTITY"), "unsupported value");
        mutate(root -> consume(root).addProperty("slotSemantics", "NO_INVENTORY_SLOT"),
            "slot semantics do not match the resource scope");
        mutate(root -> consume(root).addProperty("target", 0), "positive integer");
        mutate(root -> consume(root).addProperty("target", 1.5), "positive integer");
        mutate(root -> consume(root).addProperty("target", 29), "per-unit slot quantity exceeds inventory capacity");
        mutate(root -> output(root).addProperty("quantity", 0), "number out of range");
        mutate(root -> output(root).addProperty("quantity", 1.5), "positive integer");
        mutate(root -> first(root).add("consumes", new com.google.gson.JsonArray()),
            "outputs require at least one input");
        mutate(root -> output(root).addProperty("resourceId", consume(root).get("fact").getAsString()),
            "Overlapping or duplicate");
        mutate(root -> first(root).getAsJsonArray("consumes").add(consume(root).deepCopy()),
            "duplicate preparation fact");
        mutate(root ->
        {
            String occupied = consume(root).get("fact").getAsString().replace(".quantity", ".occupied_slots");
            root.getAsJsonArray("facts").remove(findFact(root, occupied));
        }, "missing declared occupied-slot fact");
        mutate(root -> root.getAsJsonArray("methods").add(first(root).deepCopy()), "duplicate method ID");
    }

    @Test
    public void allFlowItemsUseTheCanonicalRuneLiteBoundary() throws Exception
    {
        assertThrows(IllegalArgumentException.class,
            () -> new MethodDefinitionLoader().loadProduction(new StringReader(json()), Set.of()));
        Set<Integer> canonical = ProductionConstructionCatalogTest.canonicalItems();
        JsonObject root = new JsonParser().parse(json()).getAsJsonObject();
        root.getAsJsonArray("facts").forEach(element ->
        {
            String id = element.getAsJsonObject().get("id").getAsString();
            if (id.matches("inventory\\.item\\.[0-9]+\\.(quantity|occupied_slots)")
                || id.matches("container\\.[a-z0-9_]+\\.contents\\.[0-9]+\\.quantity"))
            {
                String[] parts = id.split("\\.");
                assertTrue(id, canonical.contains(Integer.parseInt(parts[id.startsWith("container.") ? 3 : 2])));
            }
        });
    }

    @Test
    public void herbloreKnowledgeDoesNotAppearInProductionJava() throws Exception
    {
        List<MethodDefinition> methods = load();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java")))
        {
            for (Path path : sources.filter(value -> value.toString().endsWith(".java")).collect(Collectors.toList()))
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

    static List<MethodDefinition> load() throws Exception
    {
        return new MethodDefinitionLoader().loadProduction(new StringReader(json()),
            ProductionConstructionCatalogTest.canonicalItems());
    }

    static String json() throws Exception
    {
        try (java.io.InputStream input = ProductionHerbloreCatalogTest.class.getResourceAsStream(CATALOG))
        {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void mutate(Consumer<JsonObject> mutation, String expected) throws Exception
    {
        JsonObject root = new JsonParser().parse(json()).getAsJsonObject();
        mutation.accept(root);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new MethodDefinitionLoader().loadProduction(new StringReader(root.toString()),
                ProductionConstructionCatalogTest.canonicalItems()));
        assertTrue(exception.getMessage(), exception.getMessage().contains(expected));
    }

    private static JsonObject first(JsonObject root)
    {
        return root.getAsJsonArray("methods").get(0).getAsJsonObject();
    }

    private static JsonObject consume(JsonObject root)
    {
        return first(root).getAsJsonArray("consumes").get(0).getAsJsonObject();
    }

    private static JsonObject output(JsonObject root)
    {
        return first(root).getAsJsonArray("produces").get(0).getAsJsonObject();
    }

    private static int findFact(JsonObject root, String id)
    {
        for (int index = 0; index < root.getAsJsonArray("facts").size(); index++)
        {
            if (root.getAsJsonArray("facts").get(index).getAsJsonObject().get("id").getAsString().equals(id))
            {
                return index;
            }
        }
        throw new AssertionError("Missing fixture fact " + id);
    }
}
