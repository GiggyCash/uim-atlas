package com.uimatlas.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.MethodDefinition;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductionSkillCoverageCatalogTest
{
    static final Map<String, Integer> CATALOG_COUNTS = Map.ofEntries(
        Map.entry("agility-v1.json", 3), Map.entry("construction-v1.json", 3),
        Map.entry("cooking-v1.json", 3), Map.entry("crafting-v1.json", 3),
        Map.entry("firemaking-v1.json", 2), Map.entry("fishing-v1.json", 2),
        Map.entry("fletching-v1.json", 2), Map.entry("herblore-v1.json", 6),
        Map.entry("magic-v1.json", 3), Map.entry("mining-v1.json", 3),
        Map.entry("ranged-v1.json", 2), Map.entry("smithing-v1.json", 3),
        Map.entry("thieving-v1.json", 3), Map.entry("woodcutting-v1.json", 3));
    static final Set<String> FAMILIES = Set.of("AGILITY", "CRAFTING", "FISHING", "MINING");
    static final Set<String> V2_FAMILIES = Set.of("COOKING", "THIEVING", "MAGIC", "SMITHING",
        "FIREMAKING", "RANGED", "FLETCHING", "WOODCUTTING");
    private static final String PREFIX = "uimatlas/methods/";

    @Test
    public void exactReviewedPackLoadsThroughGenericProductionIndex() throws Exception
    {
        List<MethodDefinition> methods = load();
        assertEquals(41, methods.size());
        List<MethodDefinition> pack = methods.stream().filter(m -> FAMILIES.contains(m.getActivity()))
            .collect(Collectors.toList());
        assertEquals(11, pack.size());
        assertEquals(Set.of("method.agility.draynor_rooftop", "method.agility.varrock_rooftop",
            "method.agility.canifis_rooftop", "method.crafting.molten_glass", "method.crafting.oil_lamp",
            "method.crafting.vial", "method.fishing.fly_barbarian_village", "method.fishing.tempoross_mass",
            "method.mining.iron_mount_karuulm", "method.mining.calcified_cam_torum", "method.mining.shooting_star"),
            pack.stream().map(MethodDefinition::getId).collect(Collectors.toSet()));
        for (MethodDefinition method : methods)
        {
            assertEquals(MethodDefinition.DataKind.PRODUCTION, method.getDataKind());
            assertFalse(method.getSources().isEmpty());
            method.getSources().forEach(source ->
            {
                assertEquals(LocalDate.of(2026, 9, 14), source.getReviewedAt());
                assertFalse(source.getNotes().isBlank());
            });
        }
        for (String file : CATALOG_COUNTS.keySet())
        {
            assertEquals(CATALOG_COUNTS.get(file).intValue(), parse(json(file)).size());
        }
        assertThrows(UnsupportedOperationException.class, methods::clear);
        assertEquals(1, pack.stream().filter(m -> m.getId().equals("method.agility.draynor_rooftop"))
            .findFirst().orElseThrow().getHardRequirements().get(0).getTarget(), 0);
    }

    @Test
    public void eachNewCatalogRejectsBadFactsRangesSourcesAndProfiles() throws Exception
    {
        for (String family : Stream.concat(FAMILIES.stream(), V2_FAMILIES.stream()).collect(Collectors.toSet()))
        {
            String file = family.toLowerCase(java.util.Locale.ROOT) + "-v1.json";
            reject(file, root -> root.addProperty("unrecognized", true));
            reject(file, root -> root.getAsJsonArray("methods").add(first(root).deepCopy()));
            reject(file, root -> root.getAsJsonArray("facts").get(0).getAsJsonObject().addProperty("id", "bad fact"));
            reject(file, root -> first(root).addProperty("activity", "bad activity"));
            reject(file, root -> first(root).getAsJsonArray("hardRequirements").get(0)
                .getAsJsonObject().addProperty("target", 100));
            reject(file, root -> first(root).getAsJsonArray("hardRequirements").get(0)
                .getAsJsonObject().addProperty("target", 1.5));
            reject(file, root -> first(root).getAsJsonObject("freeInventorySlots").addProperty("target", 29));
            reject(file, root -> first(root).remove("sources"));
            reject(file, root -> first(root).getAsJsonArray("sources").get(0).getAsJsonObject().remove("reviewedAt"));
            reject(file, root -> first(root).getAsJsonArray("sources").get(0).getAsJsonObject()
                .addProperty("reviewedAt", "2026-02-30"));
            reject(file, root -> first(root).getAsJsonArray("efficiencyProfiles").get(0).getAsJsonObject()
                .getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("fact", "capability.missing.reference"));
            reject(file, root ->
            {
                JsonObject xp = new JsonObject();
                xp.addProperty("minimum", 20); xp.addProperty("maximum", 10); xp.addProperty("assumptions", "Invalid range");
                first(root).getAsJsonArray("efficiencyProfiles").get(0).getAsJsonObject().add("xpRate", xp);
            });
            reject(file, root ->
            {
                JsonObject fact = new JsonObject(); fact.addProperty("id", "inventory.item.2147483647.quantity");
                root.getAsJsonArray("facts").add(fact);
            });
        }
        reject("fishing-v1.json", root -> first(root).getAsJsonArray("consumes").get(0)
            .getAsJsonObject().addProperty("target", -1));
        reject("fishing-v1.json", root -> first(root).getAsJsonArray("setupItems").get(0)
            .getAsJsonObject().addProperty("target", 0.5));
        reject("crafting-v1.json", root -> first(root).getAsJsonArray("produces").get(0)
            .getAsJsonObject().addProperty("quantity", 29));
        reject("crafting-v1.json", root -> first(root).getAsJsonArray("consumes").get(0)
            .getAsJsonObject().addProperty("slotSemantics", "GUESS"));
        reject("crafting-v1.json", root -> first(root).getAsJsonArray("produces").get(0)
            .getAsJsonObject().addProperty("resourceId", "inventory.item.1783.quantity"));
    }

    @Test
    public void indexRejectsDuplicateIdsMissingFilesSyntheticDataAndTraversal() throws Exception
    {
        String data = json("fishing-v1.json");
        for (String index : List.of("", PREFIX + "a.json\n" + PREFIX + "a.json\n",
            "../test/synthetic-methods.json\n", PREFIX + "missing.json\n",
            PREFIX + "a.json\n" + PREFIX + "b.json\n"))
        {
            assertThrows(Exception.class, () -> new ProductionMethodCatalog().load(
                resources(index, Map.of(PREFIX + "a.json", data, PREFIX + "b.json", data)),
                ProductionConstructionCatalogTest.canonicalItems()));
        }
        String synthetic = Files.readString(Path.of("src/test/resources/uimatlas/methods/synthetic-methods.json"));
        assertThrows(IllegalArgumentException.class, () -> new ProductionMethodCatalog().load(
            resources(PREFIX + "a.json\n", Map.of(PREFIX + "a.json", synthetic)),
            ProductionConstructionCatalogTest.canonicalItems()));
        ClassLoader forward = resources(PREFIX + "a.json\n" + PREFIX + "b.json\n", Map.of(
            PREFIX + "a.json", data, PREFIX + "b.json", json("agility-v1.json")));
        ClassLoader reverse = resources(PREFIX + "b.json\n" + PREFIX + "a.json\n", Map.of(
            PREFIX + "a.json", data, PREFIX + "b.json", json("agility-v1.json")));
        assertEquals(new ProductionMethodCatalog().load(forward, ProductionConstructionCatalogTest.canonicalItems())
            .stream().map(MethodDefinition::getId).collect(Collectors.toList()),
            new ProductionMethodCatalog().load(reverse, ProductionConstructionCatalogTest.canonicalItems())
                .stream().map(MethodDefinition::getId).collect(Collectors.toList()));
    }

    @Test
    public void productionJavaContainsNoPackKnowledge() throws Exception
    {
        Set<String> families = Stream.concat(FAMILIES.stream(), V2_FAMILIES.stream()).collect(Collectors.toSet());
        List<MethodDefinition> methods = load().stream().filter(m -> families.contains(m.getActivity()))
            .collect(Collectors.toList());
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java")))
        {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()))
            {
                String text = Files.readString(path);
                for (MethodDefinition method : methods)
                {
                    assertFalse(path.toString(), text.contains(method.getId()));
                    assertFalse(path.toString(), text.contains(method.getDisplayName()));
                }
                for (String skill : families)
                {
                    assertFalse(path.toString(), text.contains("\"" + skill + "\""));
                    assertFalse(path.toString(), text.contains("skill." + skill.toLowerCase(java.util.Locale.ROOT) + "."));
                }
            }
        }
    }

    static List<MethodDefinition> load() throws Exception
    {
        return new ProductionMethodCatalog().load(ProductionSkillCoverageCatalogTest.class.getClassLoader(),
            ProductionConstructionCatalogTest.canonicalItems());
    }

    private static ClassLoader resources(String index, Map<String, String> files)
    {
        return new ClassLoader(null)
        {
            @Override public InputStream getResourceAsStream(String name)
            {
                String content = name.equals(PREFIX + "catalogs.txt") ? index : files.get(name);
                return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static String json(String file) throws Exception
    {
        try (InputStream input = ProductionSkillCoverageCatalogTest.class.getResourceAsStream("/" + PREFIX + file))
        {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<MethodDefinition> parse(String json) throws Exception
    {
        return new MethodDefinitionLoader().loadProduction(new StringReader(json), ProductionConstructionCatalogTest.canonicalItems());
    }

    private static JsonObject first(JsonObject root) { return root.getAsJsonArray("methods").get(0).getAsJsonObject(); }

    private static void reject(String file, Consumer<JsonObject> mutation) throws Exception
    {
        JsonObject root = new JsonParser().parse(json(file)).getAsJsonObject();
        mutation.accept(root);
        assertThrows(file, IllegalArgumentException.class, () -> parse(root.toString()));
    }
}
