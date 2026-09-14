package com.uimatlas.data;

import com.uimatlas.recommendation.FactLookup;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.recommendation.GoalEvaluator;
import com.uimatlas.recommendation.GoalState;
import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoalEvaluatorTest
{
    static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    public void knownIncompleteAndUnknownRemainDistinct() throws Exception
    {
        GoalDefinition goal = ProductionGoalCatalogTest.load();
        Map<String, Observation<Double>> values = facts(goal, 3, false);
        GoalState inProgress = new GoalEvaluator().evaluate(goal, values::get, NOW);
        assertEquals(GoalState.Status.IN_PROGRESS, inProgress.getStatus());
        assertTrue(inProgress.getMissingRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("skill.herblore.level")));
        assertTrue(inProgress.getUnknownRequirements().isEmpty());
        assertEquals(9, inProgress.getCompletedMilestones());
        assertEquals(10, inProgress.getTotalMilestones());
        assertEquals("milestone.rfd.culinaromancer", inProgress.getCurrentMilestones().get(0).getMilestone().getId());

        values.remove("quest.74.complete");
        GoalState unknown = new GoalEvaluator().evaluate(goal, values::get, NOW);
        assertEquals(GoalState.Status.UNKNOWN, unknown.getStatus());
        assertTrue(unknown.getUnknownRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.74.complete")));
        assertFalse(unknown.getMissingRequirements().stream().anyMatch(check ->
            check.getRequirement().getFact().equals("quest.74.complete")));
    }

    @Test
    public void verifiedFinalCompletionCompletesTheGoalWithoutInventedPercentage() throws Exception
    {
        GoalDefinition goal = ProductionGoalCatalogTest.load();
        GoalState state = new GoalEvaluator().evaluate(goal, facts(goal, 99, true)::get, NOW);
        assertEquals(GoalState.Status.COMPLETE, state.getStatus());
        assertEquals(10, state.getCompletedMilestones());
        assertEquals(10, state.getTotalMilestones());
        assertTrue(state.getCurrentMilestones().isEmpty());
    }

    @Test
    public void questFreshnessAndFutureDatesRemainUnknown() throws Exception
    {
        GoalDefinition goal = ProductionGoalCatalogTest.load();
        for (Instant observed : new Instant[]{NOW.minusSeconds(86401), NOW.plusSeconds(1)})
        {
            Map<String, Observation<Double>> values = facts(goal, 99, false);
            values.put("quest.74.complete", Observation.verified(1.0, "RuneLite: Quest.getState", observed).lastObserved());
            GoalState state = new GoalEvaluator().evaluate(goal, values::get, NOW);
            assertEquals(GoalState.Status.UNKNOWN, state.getStatus());
            assertTrue(state.getUnknownRequirements().stream().anyMatch(check ->
                check.getRequirement().getFact().equals("quest.74.complete")));
        }
    }

    static Map<String, Observation<Double>> facts(GoalDefinition goal, int herblore, boolean complete)
    {
        Map<String, Observation<Double>> values = new HashMap<>();
        java.util.stream.Stream.concat(
            java.util.stream.Stream.concat(goal.getRequirements().stream(), java.util.stream.Stream.of(goal.getCompletion())),
            goal.getMilestones().stream().flatMap(milestone -> java.util.stream.Stream.concat(
                milestone.getRequirements().stream(), java.util.stream.Stream.of(milestone.getCompletion()))))
            .forEach(requirement ->
            {
                String fact = requirement.getFact();
                double value = fact.equals("skill.herblore.level") ? herblore
                    : fact.equals("quest.2316.complete") ? (complete ? 1 : 0)
                    : fact.startsWith("skill.") ? 99 : fact.equals("account.quest_points") ? 333 : 1;
                Observation<Double> observation = Observation.verified(value, fact.startsWith("quest.")
                    ? "RuneLite: Quest.getState" : "verified goal fact", NOW);
                values.put(fact, fact.startsWith("quest.") || fact.equals("account.quest_points")
                    ? observation.lastObserved() : observation);
            });
        return values;
    }
}
