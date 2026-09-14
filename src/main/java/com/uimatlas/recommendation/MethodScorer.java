package com.uimatlas.recommendation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

        /** Preserve signed contributions when comparing only factors shared by action families. */
        Score selectFactors(Set<Factor> factors)
        {
            Map<Factor, Double> selected = new EnumMap<>(Factor.class);
            factors.forEach(factor -> selected.put(factor, contributions.get(factor)));
            return new Score(selected);
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
        Score base = weightedScore(inputs, weights);
        Map<Factor, Double> contributions = new EnumMap<>(base.getContributions());
        contributions.put(Factor.RISK, weights.get(Factor.RISK)
            * Math.max(inputs.get(Factor.RISK), evaluation.getMethod().getDanger().getRiskFloor()));
        return Optional.of(new Score(contributions));
    }

    /** Shared arithmetic only: callers own applicability, required factors and risk policy. */
    static Score weightedScore(Map<Factor, Double> inputs, Map<Factor, Double> weights)
    {
        Map<Factor, Double> contributions = new EnumMap<>(Factor.class);
        inputs.forEach((factor, value) ->
        {
            if (value == null || !Double.isFinite(value) || value < 0 || value > 1)
            {
                throw new IllegalArgumentException("Expected normalized [0,1] input for " + factor);
            }
            contributions.put(factor, weights.get(factor) * value);
        });
        return new Score(contributions);
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
