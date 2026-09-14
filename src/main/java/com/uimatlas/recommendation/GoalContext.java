package com.uimatlas.recommendation;

import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import lombok.Value;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;

/** Derives goal relevance, selects covered candidates, then delegates to the existing decision pipeline. */
public final class GoalContext
{
    @Value
    public static class ExternalFactors
    {
        double storageUnlockValue;
        double risk;
        double uncertainty;

        public ExternalFactors(double storageUnlockValue, double risk, double uncertainty)
        {
            validate(storageUnlockValue, "storageUnlockValue");
            validate(risk, "risk");
            validate(uncertainty, "uncertainty");
            this.storageUnlockValue = storageUnlockValue;
            this.risk = risk;
            this.uncertainty = uncertainty;
        }

        private static void validate(double value, String name)
        {
            if (!Double.isFinite(value) || value < 0 || value > 1)
            {
                throw new IllegalArgumentException(name + ": expected normalized [0,1] value");
            }
        }
    }

    @Value
    public static class MethodRelevance
    {
        MethodDefinition method;
        double goalProgress;
        List<GoalState.Check> matchedRequirements;

        public MethodRelevance(MethodDefinition method, double goalProgress, List<GoalState.Check> matchedRequirements)
        {
            this.method = method;
            this.goalProgress = goalProgress;
            this.matchedRequirements = List.copyOf(matchedRequirements);
        }
    }

    @Value
    public static class CoverageGap
    {
        GoalState.Check check;
        String reason;
    }

    @Value
    public static class Result
    {
        GoalState goalState;
        List<MethodRelevance> methods;
        List<CoverageGap> coverageGaps;
        RecommendationDecision.Result decision;

        public Result(GoalState goalState, List<MethodRelevance> methods,
            List<CoverageGap> coverageGaps, RecommendationDecision.Result decision)
        {
            this.goalState = goalState;
            this.methods = List.copyOf(methods);
            this.coverageGaps = List.copyOf(coverageGaps);
            this.decision = decision;
        }
    }

    public Result decide(GoalDefinition goal, AccountState state, Collection<MethodDefinition> methods,
        Map<String, ExternalFactors> externalFactors,
        Map<String, Map<String, com.uimatlas.state.Observation<Boolean>>> preparationSupport, Instant now)
    {
        return decide(goal, new AccountStateFacts(state, now), methods,
            externalFactors, preparationSupport, now);
    }

    public Result decide(GoalDefinition goal, FactLookup facts, Collection<MethodDefinition> methods,
        Map<String, ExternalFactors> externalFactors,
        Map<String, Map<String, com.uimatlas.state.Observation<Boolean>>> preparationSupport, Instant now)
    {
        Objects.requireNonNull(goal);
        Objects.requireNonNull(facts);
        Objects.requireNonNull(now);
        GoalState state = new GoalEvaluator().evaluate(goal, facts, now);
        List<MethodDefinition> ordered = new ArrayList<>(methods);
        ordered.sort(Comparator.comparing(MethodDefinition::getId));
        Set<String> methodIds = new HashSet<>();
        List<MethodRelevance> relevance = new ArrayList<>();
        List<RecommendationDecision.Candidate> candidates = new ArrayList<>();
        for (MethodDefinition method : ordered)
        {
            if (!methodIds.add(method.getId()))
            {
                throw new IllegalArgumentException("Duplicate method ID: " + method.getId());
            }
            String skillFact = "skill." + method.getActivity().toLowerCase(Locale.ROOT) + ".level";
            List<GoalState.Check> matches = state.getStatus() == GoalState.Status.COMPLETE ? List.of()
                : state.getMissingRequirements().stream()
                    .filter(check -> check.getKind() == GoalState.CheckKind.PREREQUISITE
                        && check.getRequirement().getFact().equals(skillFact)
                        && check.getRequirement().getComparison() == Requirement.Comparison.AT_LEAST
                        && levelAllows(method, check, facts, now))
                    .collect(java.util.stream.Collectors.toUnmodifiableList());
            double progress = matches.isEmpty() ? 0 : 1;
            relevance.add(new MethodRelevance(method, progress, matches));
            if (progress > 0)
            {
                ExternalFactors external = externalFactors.get(method.getId());
                if (external == null)
                {
                    throw new IllegalArgumentException("Missing explicit non-goal factors for " + method.getId());
                }
                Map<MethodScorer.Factor, Double> inputs = new EnumMap<>(MethodScorer.Factor.class);
                inputs.put(GOAL_PROGRESS, progress);
                inputs.put(STORAGE_UNLOCK_VALUE, external.getStorageUnlockValue());
                inputs.put(RISK, external.getRisk());
                inputs.put(UNCERTAINTY, external.getUncertainty());
                candidates.add(new RecommendationDecision.Candidate(method, inputs,
                    preparationSupport.getOrDefault(method.getId(), Map.of())));
            }
        }
        Map<String, GoalState.Check> gaps = new TreeMap<>();
        List<GoalState.Check> unresolved = new ArrayList<>(state.getMissingRequirements());
        unresolved.addAll(state.getUnknownRequirements());
        for (GoalState.Check check : unresolved)
        {
            boolean covered = relevance.stream().anyMatch(value -> value.getGoalProgress() > 0
                && value.getMatchedRequirements().stream().anyMatch(match -> sameRequirement(match, check)));
            if (!covered)
            {
                String key = check.getRequirement().getFact() + ":" + check.getRequirement().getTarget()
                    + ":" + check.getKind();
                gaps.putIfAbsent(key, check);
            }
        }
        List<CoverageGap> coverage = gaps.values().stream().map(check -> new CoverageGap(check,
            check.getResult() == Requirement.Result.UNKNOWN
                ? "Required state is unknown and no currently supported method can resolve it"
                : "No currently supported method advances this requirement"))
            .collect(java.util.stream.Collectors.toUnmodifiableList());
        return new Result(state, relevance, coverage,
            new RecommendationDecision().decide(facts, candidates, now));
    }

    private boolean levelAllows(MethodDefinition method, GoalState.Check target, FactLookup facts, Instant now)
    {
        return method.getHardRequirements().stream()
            .filter(requirement -> requirement.getFact().equals(target.getRequirement().getFact()))
            .allMatch(requirement -> requirement.evaluate(facts.get(requirement.getFact()), now)
                == Requirement.Result.SATISFIED);
    }

    private boolean sameRequirement(GoalState.Check left, GoalState.Check right)
    {
        return left.getKind() == right.getKind()
            && left.getRequirement().getFact().equals(right.getRequirement().getFact())
            && left.getRequirement().getComparison() == right.getRequirement().getComparison()
            && left.getRequirement().getTarget() == right.getRequirement().getTarget();
    }
}
