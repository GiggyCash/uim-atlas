package com.uimatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.GoalDefinition;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Quest;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductionGoalCatalogTest
{
    static final String CATALOG = "/uimatlas/goals/recipe-for-disaster-v1.json";

    @Test
    public void rfdProductionGoalLoadsStrictlyWithTenDeterministicMilestones() throws Exception
    {
        GoalDefinition goal = load();
        assertEquals("goal.recipe_for_disaster", goal.getId());
        assertEquals("Recipe for Disaster", goal.getDisplayName());
        assertEquals(10, goal.getMilestones().size());
        assertEquals("milestone.rfd.introduction", goal.getMilestones().get(0).getId());
        assertEquals("milestone.rfd.culinaromancer", goal.getMilestones().get(9).getId());
        assertEquals("quest.2316.complete", goal.getCompletion().getFact());
        assertTrue(goal.getRequirements().stream().anyMatch(requirement ->
            requirement.getFact().equals("skill.herblore.level") && requirement.getTarget() == 25));
        assertTrue(goal.getRequirements().stream().anyMatch(requirement ->
            requirement.getFact().equals("account.quest_points") && requirement.getTarget() == 175));
        assertEquals(2, goal.getRewards().size());
        assertTrue(goal.getSources().size() >= 4);
        goal.getSources().forEach(source ->
        {
            assertEquals(LocalDate.of(2026, 9, 14), source.getReviewedAt());
            assertTrue(source.getUrl().startsWith("https://"));
        });
        assertThrows(UnsupportedOperationException.class, goal.getMilestones()::clear);
    }

    @Test
    public void milestoneOrderDoesNotDependOnJsonOrder() throws Exception
    {
        JsonObject root = parse();
        JsonArray milestones = goal(root).getAsJsonArray("milestones");
        List<com.google.gson.JsonElement> reversed = new java.util.ArrayList<>();
        milestones.forEach(reversed::add);
        Collections.reverse(reversed);
        JsonArray replacement = new JsonArray();
        reversed.forEach(replacement::add);
        goal(root).add("milestones", replacement);
        assertEquals(load().getMilestones().stream().map(GoalDefinition.Milestone::getId).collect(Collectors.toList()),
            loader().loadProduction(new StringReader(root.toString())).get(0).getMilestones().stream()
                .map(GoalDefinition.Milestone::getId).collect(Collectors.toList()));
    }

    @Test
    public void duplicateIdsReferencesAndCyclesAreRejected() throws Exception
    {
        mutate(root -> root.getAsJsonArray("goals").add(goal(root).deepCopy()), "duplicate goal ID");
        mutate(root -> goal(root).getAsJsonArray("milestones").add(milestone(root, 0).deepCopy()),
            "duplicate milestone ID");
        mutate(root -> milestone(root, 0).getAsJsonArray("dependsOn").add(reference("milestone.rfd.missing")),
            "unresolved child reference");
        mutate(root -> milestone(root, 0).getAsJsonArray("dependsOn").add(reference("milestone.rfd.culinaromancer")),
            "dependency cycle");
    }

    @Test
    public void malformedFactsRangesCompletionAndMetadataAreRejected() throws Exception
    {
        mutate(root -> root.getAsJsonArray("facts").add(root.getAsJsonArray("facts").get(0).deepCopy()),
            "duplicate fact ID");
        mutate(root -> root.getAsJsonArray("facts").get(15).getAsJsonObject()
            .addProperty("id", "quest.999999.complete"), "canonical boundary");
        mutate(root -> root.getAsJsonArray("facts").get(0).getAsJsonObject()
            .addProperty("id", "bad"), "stable ID");
        mutate(root -> goal(root).getAsJsonArray("requirements").get(1).getAsJsonObject()
            .addProperty("target", 100), "invalid skill level");
        mutate(root -> goal(root).getAsJsonArray("requirements").get(0).getAsJsonObject()
            .addProperty("target", 334), "quest-point range");
        mutate(root -> goal(root).getAsJsonObject("completion").addProperty("comparison", "AT_LEAST"),
            "invalid completion definition");
        mutate(root -> milestone(root, 0).add("sources", new JsonArray()), "source metadata");
        mutate(root -> goal(root).addProperty("typo", true), "unknown fields");
    }

    @Test
    public void productionGoalIsInJarAndNoGoalKnowledgeIsEmbeddedInJava() throws Exception
    {
        String jar = System.getProperty("pluginJar");
        assertNotNull(jar);
        try (JarFile file = new JarFile(jar))
        {
            assertNotNull(file.getEntry("uimatlas/goals/recipe-for-disaster-v1.json"));
            assertTrue(file.stream().noneMatch(entry -> entry.getName().contains("synthetic")
                || entry.getName().contains("fixture") || entry.getName().startsWith("com/uimatlas/data/ProductionGoal")));
        }
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java")))
        {
            for (Path path : sources.filter(value -> value.toString().endsWith(".java")).collect(Collectors.toList()))
            {
                String source = Files.readString(path);
                assertFalse(path.toString(), source.contains("goal.recipe_for_disaster"));
                assertFalse(path.toString(), source.contains("Recipe for Disaster"));
            }
        }
    }

    static GoalDefinition load() throws Exception
    {
        return loader().loadProduction(new StringReader(json())).get(0);
    }

    static GoalDefinitionLoader loader()
    {
        return new GoalDefinitionLoader(Arrays.stream(Quest.values()).map(Quest::getId).collect(Collectors.toSet()), 333);
    }

    static String json() throws Exception
    {
        try (java.io.InputStream input = ProductionGoalCatalogTest.class.getResourceAsStream(CATALOG))
        {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void mutate(Consumer<JsonObject> mutation, String expected) throws Exception
    {
        JsonObject root = parse();
        mutation.accept(root);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> loader().loadProduction(new StringReader(root.toString())));
        assertTrue(exception.getMessage(), exception.getMessage().contains(expected));
    }

    private static JsonObject parse() throws Exception
    {
        return new JsonParser().parse(json()).getAsJsonObject();
    }

    private static JsonObject goal(JsonObject root)
    {
        return root.getAsJsonArray("goals").get(0).getAsJsonObject();
    }

    private static JsonObject milestone(JsonObject root, int index)
    {
        return goal(root).getAsJsonArray("milestones").get(index).getAsJsonObject();
    }

    private static JsonObject reference(String id)
    {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        return value;
    }
}
