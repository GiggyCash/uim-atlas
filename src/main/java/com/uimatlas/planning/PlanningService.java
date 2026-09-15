package com.uimatlas.planning;

import com.uimatlas.data.ProductionGoalCatalog;
import com.uimatlas.data.ProductionMethodCatalog;
import com.uimatlas.recommendation.FactLookup;
import com.uimatlas.recommendation.GoalContext;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.recommendation.GoalState;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.MethodScorer;
import com.uimatlas.recommendation.QuestAction;
import com.uimatlas.recommendation.RecommendationDecision;
import com.uimatlas.recommendation.Requirement;
import com.uimatlas.recommendation.StrategicAction;
import com.uimatlas.recommendation.StrategicDecision;
import com.uimatlas.state.AccountMode;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import com.uimatlas.state.Observation;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.Value;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;

/** Loads immutable production knowledge and composes the existing planner for one account snapshot. */
public final class PlanningService
{
    public enum Status { SELECT_GOAL, WAITING_FOR_ACCOUNT, UNSUPPORTED_ACCOUNT, GOAL_COMPLETE, READY, NEEDS_INFO }
    public enum ReasonKind { NONE, ACCOUNT_UNKNOWN, ACCOUNT_NOT_UIM, COMPLETE, REOBSERVATION_REQUIRED,
        UNKNOWN_REQUIREMENT, MISSING_PREPARATION, BLOCKED, SCORING_UNAVAILABLE, NO_COVERED_ACTION,
        LOWER_PRIORITY, SCORE_DISADVANTAGE, STABLE_TIE }

    @Value
    public static class Reason
    {
        ReasonKind kind;
        String actionId;
        String description;
        Requirement requirement;
        Observation<Double> observation;
    }

    @Value
    public static class Alternative
    {
        StrategicDecision.Candidate candidate;
        Reason reason;
    }

    @Value
    public static class Result
    {
        Status status;
        List<GoalDefinition> goals;
        GoalDefinition selectedGoal;
        StrategicDecision.Result strategic;
        StrategicDecision.Candidate primary;
        List<Alternative> alternatives;
        Reason reason;
        Instant evaluatedAt;
        Instant refreshAt;
        Set<String> refreshFacts;
        int methodCount;
        int relevantMethodCount;
        int actionableCount;
    }

    private final List<GoalDefinition> goals;
    private final List<MethodDefinition> methods;

    public PlanningService(Collection<GoalDefinition> goals, Collection<MethodDefinition> methods)
    {
        this.goals = orderedUnique(goals, GoalDefinition::getId, "goal");
        this.methods = orderedUnique(methods, MethodDefinition::getId, "method");
        if (this.goals.isEmpty() || this.methods.isEmpty())
        {
            throw new IllegalArgumentException("Planning requires production goals and methods");
        }
    }

    public static PlanningService loadProduction(ClassLoader resources, Set<Integer> questIds,
        int maximumQuestPoints)
    {
        try
        {
            return new PlanningService(new ProductionGoalCatalog().load(resources, questIds, maximumQuestPoints),
                new ProductionMethodCatalog().loadBundled(resources));
        }
        catch (IOException | IllegalArgumentException exception)
        {
            throw new IllegalStateException("Unable to load production planning data", exception);
        }
    }

    public List<GoalDefinition> getGoals()
    {
        return goals;
    }

    public int getMethodCount()
    {
        return methods.size();
    }

    public Result plan(AccountState account, String goalId, Instant now)
    {
        if (goalId == null)
        {
            return basic(Status.SELECT_GOAL, null, reason(ReasonKind.NONE, null, "Select a goal to begin."), now);
        }
        GoalDefinition goal = goals.stream().filter(value -> value.getId().equals(goalId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown production goal ID: " + goalId));
        if (!account.isLoggedIn())
        {
            return basic(Status.WAITING_FOR_ACCOUNT, goal,
                reason(ReasonKind.ACCOUNT_UNKNOWN, null, "Waiting for a logged-in account snapshot."), now);
        }
        if (!account.getAccountMode().isKnown())
        {
            return basic(Status.NEEDS_INFO, goal,
                reason(ReasonKind.ACCOUNT_UNKNOWN, null, "Account mode is unavailable."), now);
        }
        if (account.getAccountMode().getValue() != AccountMode.ULTIMATE_IRONMAN)
        {
            return basic(Status.UNSUPPORTED_ACCOUNT, goal,
                reason(ReasonKind.ACCOUNT_NOT_UIM, null, "Live planning currently requires an Ultimate Ironman."), now);
        }

        FactLookup facts = new AccountStateFacts(account, now);
        Map<String, GoalContext.ExternalFactors> methodFactors = new LinkedHashMap<>();
        for (MethodDefinition method : methods)
        {
            // No account-specific storage provider exists, so benefit is conservatively disabled. Required
            // uncertainty remains a gate; zero adds no separate optional-estimate claim. Danger supplies risk.
            methodFactors.put(method.getId(), new GoalContext.ExternalFactors(
                0, method.getDanger().getRiskFloor(), 0));
        }
        GoalContext.Result context = new GoalContext().decide(goal, facts, methods, methodFactors, Map.of(), now);
        StrategicDecision.Result strategic = new StrategicDecision().decide(context, questFactors(goal));
        if (context.getGoalState().getStatus() == GoalState.Status.COMPLETE)
        {
            return detailed(Status.GOAL_COMPLETE, goal, strategic, null, List.of(),
                reason(ReasonKind.COMPLETE, goal.getId(), goal.getDisplayName() + " is complete."), facts, now);
        }

        StrategicDecision.Candidate primary = strategic.getCandidates().stream()
            .filter(this::liveEligible).findFirst().orElse(null);
        List<Alternative> alternatives = strategic.getCandidates().stream()
            .filter(value -> value.getGoalProgress() > 0 && value != primary).limit(5)
            .map(value -> new Alternative(value, alternativeReason(value, primary, facts, now)))
            .collect(java.util.stream.Collectors.toUnmodifiableList());
        Reason reason = primary == null ? noRecommendationReason(strategic, facts, now)
            : reason(ReasonKind.NONE, primary.getAction().getId(), "Actionable from the current trusted snapshot.");
        return detailed(primary == null ? Status.NEEDS_INFO : Status.READY, goal, strategic,
            primary, alternatives, reason, facts, now);
    }

    private boolean liveEligible(StrategicDecision.Candidate value)
    {
        StrategicAction.Readiness readiness = value.getAction().getReadiness();
        return value.getGoalProgress() > 0 && value.getScore().isPresent()
            && (readiness == StrategicAction.Readiness.READY
                || readiness == StrategicAction.Readiness.READY_TO_HANDOFF);
    }

    private Reason noRecommendationReason(StrategicDecision.Result strategic, FactLookup facts, Instant now)
    {
        List<Reason> reasons = strategic.getCandidates().stream().filter(value -> value.getGoalProgress() > 0)
            .map(value -> candidateReason(value, facts, now)).collect(java.util.stream.Collectors.toList());
        for (ReasonKind kind : List.of(ReasonKind.REOBSERVATION_REQUIRED, ReasonKind.UNKNOWN_REQUIREMENT,
            ReasonKind.MISSING_PREPARATION, ReasonKind.BLOCKED, ReasonKind.SCORING_UNAVAILABLE))
        {
            Optional<Reason> match = reasons.stream().filter(value -> value.getKind() == kind)
                .sorted(Comparator.comparing(value -> value.getActionId() == null ? "" : value.getActionId()))
                .findFirst();
            if (match.isPresent())
            {
                return match.get();
            }
        }
        Optional<GoalState.Check> gap = strategic.getContext().getCoverageGaps().stream()
            .map(GoalContext.CoverageGap::getCheck).sorted(Comparator.comparing(value -> value.getRequirement().getFact()))
            .findFirst();
        return gap.map(value -> checkReason(value.getScopeId(), value.getRequirement(), value.getObservation(), now,
                value.getResult() == Requirement.Result.MISSING ? ReasonKind.BLOCKED : null))
            .orElseGet(() -> reason(ReasonKind.NO_COVERED_ACTION, null,
                "No currently covered candidate is actionable."));
    }

    private Reason candidateReason(StrategicDecision.Candidate candidate, FactLookup facts, Instant now)
    {
        StrategicAction action = candidate.getAction();
        if (action instanceof StrategicAction.Method)
        {
            RecommendationDecision.CandidateResult method = ((StrategicAction.Method) action).getResult();
            List<Requirement> unresolved = new ArrayList<>(method.getEvaluation().getUnknownRequirements());
            unresolved.addAll(method.getPreparation().getUnresolvedRequirements());
            method.getEvaluation().getPreparationAnyOf().stream()
                .filter(value -> value.getResult() == MethodEvaluator.GroupResult.UNKNOWN)
                .flatMap(value -> value.getAlternatives().stream())
                .flatMap(value -> value.getUnknownRequirements().stream()).forEach(unresolved::add);
            Optional<Requirement> unknown = unresolved.stream().sorted(Comparator.comparing(Requirement::getFact)).findFirst();
            if (unknown.isPresent())
            {
                return checkReason(action.getId(), unknown.get(), facts.get(unknown.get().getFact()), now, null);
            }
            if (!method.getPreparation().getDeficits().isEmpty())
            {
                Requirement requirement = method.getPreparation().getDeficits().get(0).getRequirement();
                return new Reason(ReasonKind.MISSING_PREPARATION, action.getId(), requirement.getDescription(),
                    requirement, method.getPreparation().getDeficits().get(0).getObservation());
            }
            if (!method.getPreparation().getUnresolvedGroups().isEmpty())
            {
                return reason(ReasonKind.MISSING_PREPARATION, action.getId(),
                    method.getPreparation().getUnresolvedGroups().get(0).getGroup().getDescription());
            }
            if (!method.getEvaluation().getBlockers().isEmpty())
            {
                Requirement requirement = method.getEvaluation().getBlockers().get(0);
                return new Reason(ReasonKind.BLOCKED, action.getId(), requirement.getDescription(), requirement,
                    facts.get(requirement.getFact()));
            }
            if (candidate.getScore().isEmpty())
            {
                return reason(ReasonKind.SCORING_UNAVAILABLE, action.getId(),
                    "No verified efficiency profile is available for this setup.");
            }
        }
        else
        {
            QuestAction quest = (QuestAction) action;
            if (!quest.getUnresolvedRequirements().isEmpty())
            {
                GoalState.Check check = quest.getUnresolvedRequirements().get(0);
                return checkReason(action.getId(), check.getRequirement(), check.getObservation(), now, null);
            }
            if (!quest.getBlockers().isEmpty())
            {
                GoalState.Check check = quest.getBlockers().get(0);
                return checkReason(action.getId(), check.getRequirement(), check.getObservation(), now,
                    ReasonKind.BLOCKED);
            }
            if (candidate.getScore().isEmpty())
            {
                return reason(ReasonKind.SCORING_UNAVAILABLE, action.getId(),
                    "Strategic handoff scoring inputs are unavailable.");
            }
        }
        return reason(ReasonKind.NO_COVERED_ACTION, action.getId(), "This candidate is not live-actionable.");
    }

    private Reason alternativeReason(StrategicDecision.Candidate candidate,
        StrategicDecision.Candidate primary, FactLookup facts, Instant now)
    {
        if (!liveEligible(candidate))
        {
            return candidateReason(candidate, facts, now);
        }
        if (primary != null && candidate.getPriority().compareTo(primary.getPriority()) > 0)
        {
            String description = primary.getPriority() == StrategicDecision.Priority.FRONTIER_HANDOFF
                ? "A directly available goal milestone has higher strategic priority."
                : "A method that directly unblocks the active goal frontier has higher strategic priority.";
            return reason(ReasonKind.LOWER_PRIORITY, candidate.getAction().getId(), description);
        }
        if (primary == null || primary.getScore().isEmpty() || candidate.getScore().isEmpty())
        {
            return candidateReason(candidate, facts, now);
        }
        Map<MethodScorer.Factor, Double> winner = primary.getScore().orElseThrow().getContributions();
        Map<MethodScorer.Factor, Double> alternative = candidate.getScore().orElseThrow().getContributions();
        List<MethodScorer.Factor> disadvantages = winner.keySet().stream()
            .filter(alternative::containsKey)
            .filter(factor -> winner.get(factor) - alternative.get(factor) > 0.0000001)
            .sorted(Comparator.comparingDouble((MethodScorer.Factor factor) ->
                winner.get(factor) - alternative.get(factor)).reversed().thenComparing(Enum::name))
            .limit(2).collect(java.util.stream.Collectors.toList());
        if (disadvantages.isEmpty())
        {
            return reason(ReasonKind.STABLE_TIE, candidate.getAction().getId(),
                "Same strategic priority and score; stable ordering placed the selected action first.");
        }
        String description = disadvantages.stream().map(this::factorDisadvantage)
            .collect(java.util.stream.Collectors.joining(" "));
        return reason(ReasonKind.SCORE_DISADVANTAGE, candidate.getAction().getId(), description);
    }

    private String factorDisadvantage(MethodScorer.Factor factor)
    {
        switch (factor)
        {
            case GOAL_PROGRESS: return "Less verified goal progress.";
            case SETUP_COST: return "Higher setup cost.";
            case TRANSITION_COST: return "Higher transition cost.";
            case INVENTORY_DISRUPTION: return "More inventory disruption.";
            case RISK: return "Higher risk.";
            case UNCERTAINTY: return "Higher uncertainty.";
            default: throw new IllegalArgumentException("Not a shared strategic factor: " + factor);
        }
    }

    private Reason checkReason(String actionId, Requirement requirement, Observation<Double> observation,
        Instant now, ReasonKind fallback)
    {
        ReasonKind kind = fallback;
        if (kind == null)
        {
            kind = observation != null && observation.isKnown()
                && (observation.getObservedAt().isAfter(now)
                    || Duration.between(observation.getObservedAt(), now)
                        .compareTo(Duration.ofSeconds(requirement.getMaxAgeSeconds())) > 0)
                ? ReasonKind.REOBSERVATION_REQUIRED : ReasonKind.UNKNOWN_REQUIREMENT;
        }
        return new Reason(kind, actionId, requirement.getDescription(), requirement, observation);
    }

    private Result detailed(Status status, GoalDefinition goal, StrategicDecision.Result strategic,
        StrategicDecision.Candidate primary, List<Alternative> alternatives, Reason reason,
        FactLookup facts, Instant now)
    {
        Refresh refresh = refresh(strategic.getContext(), facts, now);
        int relevant = (int) strategic.getContext().getMethods().stream()
            .filter(value -> value.getGoalProgress() > 0).count();
        int actionable = (int) strategic.getCandidates().stream().filter(this::liveEligible).count();
        return new Result(status, goals, goal, strategic, primary, alternatives, reason, now,
            refresh.at, refresh.facts, methods.size(), relevant, actionable);
    }

    private Result basic(Status status, GoalDefinition goal, Reason reason, Instant now)
    {
        return new Result(status, goals, goal, null, null, List.of(), reason, now,
            null, Set.of(), methods.size(), 0, 0);
    }

    private Refresh refresh(GoalContext.Result context, FactLookup facts, Instant now)
    {
        List<Requirement> requirements = new ArrayList<>();
        GoalDefinition goal = context.getGoalState().getGoal();
        requirements.add(goal.getCompletion());
        requirements.addAll(goal.getRequirements());
        goal.getMilestones().forEach(value ->
        {
            requirements.add(value.getCompletion());
            requirements.addAll(value.getRequirements());
        });
        context.getMethods().stream().filter(value -> value.getGoalProgress() > 0)
            .map(GoalContext.MethodRelevance::getMethod).forEach(method ->
            {
                requirements.addAll(method.getHardRequirements());
                requirements.addAll(method.getPreparation());
                requirements.add(method.getFreeInventorySlots());
                requirements.addAll(method.getSetupItems());
                requirements.addAll(method.getConsumes());
                requirements.addAll(method.getWorkingCapacity());
                method.getEfficiencyProfiles().forEach(value -> requirements.addAll(value.getRequirements()));
                method.getPreparationAnyOf().forEach(group -> group.getAlternatives()
                    .forEach(value -> requirements.addAll(value.getRequirements())));
            });
        Instant earliest = null;
        Set<String> refreshFacts = new TreeSet<>();
        for (Requirement requirement : requirements)
        {
            Observation<Double> observation = facts.get(requirement.getFact());
            if (observation == null || !observation.isKnown() || observation.getObservedAt().isAfter(now))
            {
                continue;
            }
            Instant expires = observation.getObservedAt().plusSeconds(requirement.getMaxAgeSeconds()).plusNanos(1);
            if (!expires.isAfter(now))
            {
                continue;
            }
            if (earliest == null || expires.isBefore(earliest))
            {
                earliest = expires;
                refreshFacts.clear();
            }
            if (expires.equals(earliest))
            {
                refreshFacts.add(requirement.getFact());
            }
        }
        return new Refresh(earliest, Set.copyOf(refreshFacts));
    }

    /** Zeroes describe opening a manual handoff; they make no claim about executing the quest. */
    private Map<String, Map<MethodScorer.Factor, Double>> questFactors(GoalDefinition goal)
    {
        Map<MethodScorer.Factor, Double> factors = new EnumMap<>(MethodScorer.Factor.class);
        factors.put(SETUP_COST, 0.0);
        factors.put(TRANSITION_COST, 0.0);
        factors.put(INVENTORY_DISRUPTION, 0.0);
        factors.put(RISK, 0.0);
        factors.put(UNCERTAINTY, 0.0);
        Map<String, Map<MethodScorer.Factor, Double>> result = new LinkedHashMap<>();
        goal.getMilestones().forEach(value -> result.put(value.getId(), Map.copyOf(factors)));
        return Map.copyOf(result);
    }

    private static Reason reason(ReasonKind kind, String actionId, String description)
    {
        return new Reason(kind, actionId, description, null, null);
    }

    private static <T> List<T> orderedUnique(Collection<T> values,
        java.util.function.Function<T, String> id, String type)
    {
        List<T> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparing(id));
        Set<String> seen = new HashSet<>();
        ordered.forEach(value ->
        {
            if (!seen.add(id.apply(value)))
            {
                throw new IllegalArgumentException("Duplicate production " + type + " ID: " + id.apply(value));
            }
        });
        return List.copyOf(ordered);
    }

    @Value
    private static class Refresh
    {
        Instant at;
        Set<String> facts;
    }
}
