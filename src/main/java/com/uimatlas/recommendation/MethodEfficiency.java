package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.Value;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;

/** Capacity diagnostics and conditional efficiency inputs; never changes method eligibility. */
public final class MethodEfficiency
{
    @Value
    public static class Check
    {
        Requirement requirement;
        Observation<Double> observation;
        Requirement.Result result;
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProfileMatch
    {
        MethodDefinition.EfficiencyProfile profile;
        List<Check> checks;

        public boolean isApplicable()
        {
            return checks.stream().allMatch(c -> c.getResult() == Requirement.Result.SATISFIED);
        }
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Result
    {
        MethodEvaluator.Evaluation evaluation;
        List<Check> workingCapacity;
        List<ProfileMatch> profiles;
        /** Absent means unresolved efficiency, never a neutral or best-case default. */
        Optional<MethodDefinition.EfficiencyProfile> selected;

        public Optional<MethodDefinition.XpRate> getTrustedXpRate()
        {
            return selected.flatMap(MethodDefinition.EfficiencyProfile::getXpRate);
        }

        /** Explicit opt-in derivation; an existing METHOD_EFFICIENCY value cannot be overwritten. */
        public Optional<Map<MethodScorer.Factor, Double>> withExplicitFactors(Map<MethodScorer.Factor, Double> explicit)
        {
            if (!explicit.keySet().equals(Set.of(GOAL_PROGRESS, STORAGE_UNLOCK_VALUE, RISK, UNCERTAINTY)))
            {
                throw new IllegalArgumentException("Supply exactly the four non-setup, non-efficiency factors");
            }
            explicit.forEach((factor, value) ->
            {
                if (value == null || !Double.isFinite(value) || value < 0 || value > 1)
                {
                    throw new IllegalArgumentException("Expected normalized [0,1] input for " + factor);
                }
            });
            return selected.map(profile ->
            {
                Map<MethodScorer.Factor, Double> inputs = new EnumMap<>(MethodScorer.Factor.class);
                inputs.putAll(explicit);
                inputs.put(METHOD_EFFICIENCY, profile.getEfficiency());
                return Map.copyOf(inputs);
            });
        }
    }

    public Result derive(MethodDefinition method, FactLookup facts, Instant now)
    {
        // One observation per fact for eligibility, working diagnostics and every profile.
        Map<String, Observation<Double>> snapshot = new TreeMap<>();
        FactLookup cached = fact -> snapshot.computeIfAbsent(fact, id ->
        {
            Observation<Double> value = facts.get(id);
            return value == null ? Observation.unknown() : value;
        });
        MethodEvaluator.Evaluation evaluation = new MethodEvaluator().evaluate(method, cached, now);
        List<Check> working = check(method.getWorkingCapacity(), cached, now);
        List<MethodDefinition.EfficiencyProfile> ordered = new ArrayList<>(method.getEfficiencyProfiles());
        ordered.sort(Comparator.comparingInt(MethodDefinition.EfficiencyProfile::getPriority).reversed());
        List<ProfileMatch> matches = new ArrayList<>();
        for (MethodDefinition.EfficiencyProfile profile : ordered)
        {
            matches.add(new ProfileMatch(profile, check(profile.getRequirements(), cached, now)));
        }
        Optional<MethodDefinition.EfficiencyProfile> selected = evaluation.getStatus() == MethodEvaluator.Status.AVAILABLE
            ? matches.stream().filter(ProfileMatch::isApplicable).map(ProfileMatch::getProfile).findFirst()
            : Optional.empty();
        return new Result(evaluation, working, List.copyOf(matches), selected);
    }

    private List<Check> check(List<Requirement> requirements, FactLookup facts, Instant now)
    {
        List<Check> checks = new ArrayList<>();
        requirements.stream().sorted(Comparator.comparing(Requirement::getFact)).forEach(requirement ->
        {
            Observation<Double> observation = facts.get(requirement.getFact());
            checks.add(new Check(requirement, observation, requirement.evaluate(observation, now)));
        });
        return List.copyOf(checks);
    }
}
