package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.Value;

/** A numeric fact predicate. Boolean facts use 0/1; absent facts never mean zero. */
@Value
public class Requirement
{
    public enum Comparison { AT_LEAST, EQUAL, AT_MOST }
    public enum Result { SATISFIED, MISSING, UNKNOWN }

    String fact;
    Comparison comparison;
    double target;
    String description;
    boolean allowLastObserved;
    long maxAgeSeconds;
    boolean safetyRelevant;

    public Requirement(String fact, Comparison comparison, double target, String description,
        boolean allowLastObserved, long maxAgeSeconds, boolean safetyRelevant)
    {
        if (fact == null || !fact.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+")
            || !Double.isFinite(target) || target < 0 || description == null || description.isBlank()
            || maxAgeSeconds < 0 || (safetyRelevant && allowLastObserved))
        {
            throw new IllegalArgumentException("Invalid requirement fact, target, description or freshness policy");
        }
        this.fact = fact;
        this.comparison = Objects.requireNonNull(comparison);
        this.target = target;
        this.description = description;
        this.allowLastObserved = allowLastObserved;
        this.maxAgeSeconds = maxAgeSeconds;
        this.safetyRelevant = safetyRelevant;
    }

    public Result evaluate(Observation<Double> observation, Instant now)
    {
        if (observation == null || !observation.isKnown()
            || (!allowLastObserved && observation.getConfidence() != Observation.Confidence.VERIFIED_NOW)
            || observation.getObservedAt().isAfter(now)
            || Duration.between(observation.getObservedAt(), now).compareTo(Duration.ofSeconds(maxAgeSeconds)) > 0
            || !Double.isFinite(observation.getValue()) || observation.getValue() < 0)
        {
            return Result.UNKNOWN;
        }
        double actual = observation.getValue();
        boolean satisfied = comparison == Comparison.AT_LEAST ? actual >= target
            : comparison == Comparison.AT_MOST ? actual <= target : actual == target;
        return satisfied ? Result.SATISFIED : Result.MISSING;
    }
}
