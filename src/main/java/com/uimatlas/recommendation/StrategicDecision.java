package com.uimatlas.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Value;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;

/** Stateless layer over one evaluated GoalContext; never re-evaluates quests or executes a handoff. */
public final class StrategicDecision
{
    private static final Pattern QUEST_COMPLETION = Pattern.compile("quest\\.([0-9]+)\\.complete");
    private static final Set<MethodScorer.Factor> SHARED = Set.of(
        GOAL_PROGRESS, SETUP_COST, TRANSITION_COST, INVENTORY_DISRUPTION, RISK, UNCERTAINTY);
    private static final Set<MethodScorer.Factor> QUEST_EXTERNAL = Set.of(
        SETUP_COST, TRANSITION_COST, INVENTORY_DISRUPTION, RISK, UNCERTAINTY);

    @Value
    public static class Candidate
    {
        StrategicAction action;
        double goalProgress;
        Optional<MethodScorer.Score> score;
        Set<MethodScorer.Factor> missingExternalFactors;

        private Candidate(StrategicAction action, double progress, Optional<MethodScorer.Score> score,
            Set<MethodScorer.Factor> missingExternalFactors)
        {
            this.action = action;
            this.goalProgress = progress;
            this.score = score;
            this.missingExternalFactors = Set.copyOf(missingExternalFactors);
        }
    }

    @Value
    public static class Result
    {
        /** Original method ranks, provenance, irrelevant-method relevance and coverage gaps are preserved. */
        GoalContext.Result context;
        List<Candidate> candidates;
        Optional<Candidate> bestActionable;

        private Result(GoalContext.Result context, List<Candidate> candidates)
        {
            this.context = context;
            this.candidates = List.copyOf(candidates);
            this.bestActionable = candidates.stream().filter(candidate -> candidate.getGoalProgress() > 0
                && candidate.getAction().isActionable() && candidate.getScore().isPresent()).findFirst();
        }
    }

    /** Quest costs/risks describe beginning a handoff, not completing a walkthrough. No missing defaults. */
    public Result decide(GoalContext.Result context,
        Map<String, Map<MethodScorer.Factor, Double>> questFactors)
    {
        List<Candidate> candidates = new ArrayList<>();
        if (context.getGoalState().getStatus() == GoalState.Status.COMPLETE)
        {
            return new Result(context, candidates);
        }
        Map<String, GoalContext.MethodRelevance> relevance = context.getMethods().stream()
            .collect(Collectors.toMap(value -> value.getMethod().getId(), value -> value));
        for (RecommendationDecision.CandidateResult method : context.getDecision().getCandidates())
        {
            GoalContext.MethodRelevance match = relevance.get(method.getMethod().getId());
            Optional<MethodScorer.Score> score = method.getScore().map(value -> value.selectFactors(SHARED));
            candidates.add(new Candidate(new StrategicAction.Method(method, match), match.getGoalProgress(),
                score, Set.of()));
        }
        Map<String, GoalState.MilestoneState> milestones = new TreeMap<>();
        context.getGoalState().getMilestones().forEach(value -> milestones.put(value.getMilestone().getId(), value));
        for (GoalState.MilestoneState milestone : milestones.values())
        {
            Matcher quest = QUEST_COMPLETION.matcher(milestone.getMilestone().getCompletion().getFact());
            if (milestone.getStatus() == GoalState.MilestoneStatus.COMPLETE || !quest.matches())
            {
                continue;
            }
            List<GoalState.MilestoneState> dependencies = dependencies(milestone, milestones);
            QuestAction action = new QuestAction(milestone, dependencies, Integer.parseInt(quest.group(1)));
            boolean incomplete = milestone.getChecks().stream().anyMatch(check ->
                check.getKind() == GoalState.CheckKind.COMPLETION && check.getResult() == Requirement.Result.MISSING);
            double progress = incomplete ? 1 : 0;
            Map<MethodScorer.Factor, Double> inputs = new EnumMap<>(MethodScorer.Factor.class);
            inputs.putAll(questFactors.getOrDefault(action.getId(), Map.of()));
            if (!QUEST_EXTERNAL.containsAll(inputs.keySet()))
            {
                throw new IllegalArgumentException("Quest inputs may contain only explicit strategic costs/risk/uncertainty");
            }
            // Validate even partial inputs; missing values leave the candidate unscored, never default to zero.
            MethodScorer.weightedScore(inputs, MethodScorer.defaultWeights());
            Set<MethodScorer.Factor> missing = EnumSet.copyOf(QUEST_EXTERNAL);
            missing.removeAll(inputs.keySet());
            inputs.put(GOAL_PROGRESS, progress);
            Optional<MethodScorer.Score> score = action.isActionable() && missing.isEmpty()
                ? Optional.of(MethodScorer.weightedScore(inputs, MethodScorer.defaultWeights())) : Optional.empty();
            candidates.add(new Candidate(action, progress, score, missing));
        }
        candidates.sort(Comparator.comparingDouble((Candidate value) ->
            value.getScore().map(MethodScorer.Score::getTotal).orElse(Double.NEGATIVE_INFINITY))
            .reversed().thenComparing(value -> value.getAction().getKind().name())
            .thenComparing(value -> value.getAction().getId()));
        return new Result(context, candidates);
    }

    /** Walk explanations, not eligibility: a completed dependency already proves its earlier prerequisites. */
    private List<GoalState.MilestoneState> dependencies(GoalState.MilestoneState milestone,
        Map<String, GoalState.MilestoneState> states)
    {
        Map<String, GoalState.MilestoneState> result = new TreeMap<>();
        TreeSet<String> pending = new TreeSet<>(milestone.getMilestone().getDependsOn());
        while (!pending.isEmpty())
        {
            String id = pending.pollFirst();
            GoalState.MilestoneState dependency = states.get(id);
            if (result.putIfAbsent(id, dependency) == null
                && dependency.getStatus() != GoalState.MilestoneStatus.COMPLETE)
            {
                pending.addAll(dependency.getMilestone().getDependsOn());
            }
        }
        return List.copyOf(result.values());
    }
}
