package com.uimatlas.recommendation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.Value;

/** Weighted sum of explicit, normalized [0,1] inputs. Placeholder weights, not game balance. */
public final class MethodScorer
{
    public enum Factor
    {
        GOAL_PROGRESS(3), STORAGE_UNLOCK_VALUE(3), CURRENT_INVENTORY_FIT(2), METHOD_EFFICIENCY(1),
        SETUP_COST(-1), TRANSITION_COST(-2), INVENTORY_DISRUPTION(-2), RISK(-4), UNCERTAINTY(-3);

        private final double defaultWeight;

        Factor(double defaultWeight)
        {
            this.defaultWeight = defaultWeight;
        }
    }

    @Value
    public static class Score
    {
        double total;
        Map<Factor, Double> contributions;

        private Score(Map<Factor, Double> contributions)
        {
            this.contributions = Map.copyOf(contributions);
            this.total = contributions.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private final Map<Factor, Double> weights;

    public MethodScorer()
    {
        this(defaultWeights());
    }

    public MethodScorer(Map<Factor, Double> weights)
    {
        requireComplete(weights);
        weights.forEach((factor, weight) ->
        {
            if (!Double.isFinite(weight) || Math.abs(weight) > 100
                || weight * factor.defaultWeight < 0)
            {
                throw new IllegalArgumentException("Invalid scoring weight for " + factor);
            }
        });
        this.weights = Map.copyOf(weights);
    }

    public static Map<Factor, Double> defaultWeights()
    {
        Map<Factor, Double> weights = new EnumMap<>(Factor.class);
        for (Factor factor : Factor.values())
        {
            weights.put(factor, factor.defaultWeight);
        }
        return Map.copyOf(weights);
    }

    public Optional<Score> score(MethodEvaluator.Evaluation evaluation, Map<Factor, Double> inputs)
    {
        if (evaluation.getStatus() == MethodEvaluator.Status.BLOCKED
            || evaluation.getStatus() == MethodEvaluator.Status.UNKNOWN)
        {
            return Optional.empty();
        }
        requireComplete(inputs);
        Map<Factor, Double> contributions = new EnumMap<>(Factor.class);
        inputs.forEach((factor, value) ->
        {
            if (!Double.isFinite(value) || value < 0 || value > 1)
            {
                throw new IllegalArgumentException("Expected normalized [0,1] input for " + factor);
            }
            double effective = factor == Factor.RISK
                ? Math.max(value, evaluation.getMethod().getDanger().getRiskFloor()) : value;
            contributions.put(factor, weights.get(factor) * effective);
        });
        return Optional.of(new Score(contributions));
    }

    private static void requireComplete(Map<Factor, Double> values)
    {
        if (values == null || values.size() != Factor.values().length)
        {
            throw new IllegalArgumentException("Every scoring factor must be explicit");
        }
        for (Factor factor : Factor.values())
        {
            if (values.get(factor) == null)
            {
                throw new IllegalArgumentException("Missing scoring factor " + factor);
            }
        }
    }
}
