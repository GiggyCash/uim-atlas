package com.uimatlas.recommendation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static com.uimatlas.recommendation.SyntheticMethods.NOW;
import static org.junit.Assert.*;

public class MethodScorerTest
{
    private final MethodScorer scorer = new MethodScorer();
    private List<MethodDefinition> methods;

    @Before
    public void setUp() throws Exception
    {
        methods = SyntheticMethods.load();
    }

    @Test
    public void lowerTransitionCostBeatsMarginallyHigherEfficiency()
    {
        Map<MethodScorer.Factor, Double> steady = inputs(methods.get(0));
        Map<MethodScorer.Factor, Double> quick = inputs(methods.get(1));
        // Score against the same low-danger definition to isolate the transition input.
        assertTrue(score(methods.get(0), steady) > score(methods.get(0), quick));
        steady.put(TRANSITION_COST, quick.get(TRANSITION_COST));
        assertTrue(score(methods.get(0), steady) < score(methods.get(0), quick));
    }

    @Test
    public void permanentStorageValueOutweighsRawEfficiency()
    {
        Map<MethodScorer.Factor, Double> unlock = inputs(methods.get(2));
        Map<MethodScorer.Factor, Double> quick = inputs(methods.get(1));
        quick.put(TRANSITION_COST, unlock.get(TRANSITION_COST));
        assertTrue(score(methods.get(0), unlock) > score(methods.get(0), quick));
        unlock.put(STORAGE_UNLOCK_VALUE, 0.0);
        assertTrue(score(methods.get(0), unlock) < score(methods.get(0), quick));
    }

    @Test
    public void riskInputAndDefinitionFloorBothPenalizeOrdering()
    {
        Map<MethodScorer.Factor, Double> low = inputs(methods.get(0));
        Map<MethodScorer.Factor, Double> efficient = new EnumMap<>(low);
        efficient.put(METHOD_EFFICIENCY, 1.0);
        assertTrue(score(methods.get(0), efficient) > score(methods.get(0), low));
        efficient.put(RISK, 0.5);
        assertTrue(score(methods.get(0), efficient) < score(methods.get(0), low));
        efficient.put(RISK, 0.0);
        assertTrue(score(methods.get(1), efficient) < score(methods.get(0), low));
    }

    @Test
    public void unknownAndBlockedEvaluationsHaveNoScore()
    {
        MethodDefinition method = methods.get(0);
        Map<String, com.uimatlas.state.Observation<Double>> facts = SyntheticMethods.ready(method);
        facts.remove(method.getHardRequirements().get(1).getFact());
        MethodEvaluator evaluator = new MethodEvaluator();
        assertFalse(scorer.score(evaluator.evaluate(method, facts, NOW), inputs(method)).isPresent());
        facts.put(method.getHardRequirements().get(0).getFact(),
            com.uimatlas.state.Observation.verified(0.0, "synthetic", NOW));
        assertFalse(scorer.score(evaluator.evaluate(method, facts, NOW), inputs(method)).isPresent());
    }

    @Test
    public void weightsAreReplaceableAndBreakdownExplainsTotal()
    {
        MethodDefinition method = methods.get(0);
        Map<MethodScorer.Factor, Double> weights = new EnumMap<>(MethodScorer.defaultWeights());
        weights.put(METHOD_EFFICIENCY, 2.0);
        MethodScorer custom = new MethodScorer(weights);
        weights.clear();
        MethodScorer.Score result = custom.score(evaluation(method), inputs(method)).orElseThrow();
        assertEquals(2 * inputs(method).get(METHOD_EFFICIENCY), result.getContributions().get(METHOD_EFFICIENCY), 0.00001);
        assertEquals(result.getContributions().values().stream().mapToDouble(Double::doubleValue).sum(), result.getTotal(), 0.00001);
        assertThrows(UnsupportedOperationException.class, () -> result.getContributions().clear());
    }

    @Test
    public void allFactorsHaveTheExpectedDirection()
    {
        MethodDefinition method = methods.get(0);
        Map<MethodScorer.Factor, Double> baseline = new EnumMap<>(MethodScorer.Factor.class);
        for (MethodScorer.Factor factor : MethodScorer.Factor.values())
        {
            baseline.put(factor, 0.0);
        }
        for (MethodScorer.Factor factor : MethodScorer.Factor.values())
        {
            Map<MethodScorer.Factor, Double> changed = new EnumMap<>(baseline);
            changed.put(factor, 1.0);
            assertEquals(MethodScorer.defaultWeights().get(factor), score(method, changed), 0.00001);
        }
    }

    @Test
    public void missingOrInvalidScoreInputsAreRejected()
    {
        MethodDefinition method = methods.get(0);
        Map<MethodScorer.Factor, Double> values = inputs(method);
        for (double invalid : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
        {
            values.put(UNCERTAINTY, invalid);
            assertThrows(IllegalArgumentException.class, () -> score(method, values));
        }
        values.remove(UNCERTAINTY);
        assertThrows(IllegalArgumentException.class, () -> score(method, values));
        assertThrows(IllegalArgumentException.class, () -> new MethodScorer(Map.of()));
    }

    private MethodEvaluator.Evaluation evaluation(MethodDefinition method)
    {
        return new MethodEvaluator().evaluate(method, SyntheticMethods.ready(method), NOW);
    }

    private double score(MethodDefinition method, Map<MethodScorer.Factor, Double> inputs)
    {
        return scorer.score(evaluation(method), inputs).orElseThrow().getTotal();
    }

    private Map<MethodScorer.Factor, Double> inputs(MethodDefinition method)
    {
        Map<MethodScorer.Factor, Double> inputs = new EnumMap<>(MethodScorer.Factor.class);
        for (MethodScorer.Factor factor : MethodScorer.Factor.values())
        {
            inputs.put(factor, 0.0);
        }
        inputs.put(METHOD_EFFICIENCY, method.getXpRate().getMaximum() / 110.0);
        inputs.put(STORAGE_UNLOCK_VALUE, method.getCosts().getStorageUnlockValue());
        inputs.put(TRANSITION_COST, method.getCosts().getTransitionMinutes() / 20.0);
        return inputs;
    }
}
