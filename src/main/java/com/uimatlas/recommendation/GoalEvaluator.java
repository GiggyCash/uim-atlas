package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Evaluates a validated goal graph without interpreting any goal-specific IDs. */
public final class GoalEvaluator
{
    public GoalState evaluate(GoalDefinition goal, FactLookup facts, Instant now)
    {
        Map<String, Observation<Double>> snapshot = new TreeMap<>();
        FactLookup cached = fact -> snapshot.computeIfAbsent(fact, id ->
        {
            Observation<Double> value = facts.get(id);
            return value == null ? Observation.unknown() : value;
        });
        List<GoalState.Check> all = new ArrayList<>();
        GoalState.Check goalCompletion = check(goal.getId(), GoalState.CheckKind.COMPLETION,
            goal.getCompletion(), cached, now);
        all.add(goalCompletion);
        goal.getRequirements().forEach(requirement -> all.add(check(goal.getId(),
            GoalState.CheckKind.PREREQUISITE, requirement, cached, now)));

        Map<String, GoalState.MilestoneStatus> statuses = new HashMap<>();
        List<GoalState.MilestoneState> milestones = new ArrayList<>();
        for (GoalDefinition.Milestone milestone : goal.getMilestones())
        {
            List<GoalState.Check> checks = new ArrayList<>();
            GoalState.Check completion = check(milestone.getId(), GoalState.CheckKind.COMPLETION,
                milestone.getCompletion(), cached, now);
            checks.add(completion);
            milestone.getRequirements().forEach(requirement -> checks.add(check(milestone.getId(),
                GoalState.CheckKind.PREREQUISITE, requirement, cached, now)));
            boolean unknownDependency = milestone.getDependsOn().stream()
                .anyMatch(id -> statuses.get(id) == GoalState.MilestoneStatus.UNKNOWN);
            boolean completeDependencies = milestone.getDependsOn().stream()
                .allMatch(id -> statuses.get(id) == GoalState.MilestoneStatus.COMPLETE);
            GoalState.MilestoneStatus status;
            if (completion.getResult() == Requirement.Result.SATISFIED)
            {
                status = GoalState.MilestoneStatus.COMPLETE;
            }
            else if (completion.getResult() == Requirement.Result.UNKNOWN || unknownDependency
                || checks.stream().anyMatch(value -> value.getResult() == Requirement.Result.UNKNOWN))
            {
                status = GoalState.MilestoneStatus.UNKNOWN;
            }
            else if (!completeDependencies || checks.stream().anyMatch(value ->
                value.getKind() == GoalState.CheckKind.PREREQUISITE
                    && value.getResult() == Requirement.Result.MISSING))
            {
                status = GoalState.MilestoneStatus.BLOCKED;
            }
            else
            {
                status = GoalState.MilestoneStatus.AVAILABLE;
            }
            statuses.put(milestone.getId(), status);
            milestones.add(new GoalState.MilestoneState(milestone, status, checks));
            all.addAll(checks);
        }

        List<GoalState.Check> satisfied = filter(all, Requirement.Result.SATISFIED);
        List<GoalState.Check> missing = filter(all, Requirement.Result.MISSING);
        List<GoalState.Check> unknown = filter(all, Requirement.Result.UNKNOWN);
        GoalState.Status status = goalCompletion.getResult() == Requirement.Result.SATISFIED
            ? GoalState.Status.COMPLETE : unknown.isEmpty() ? GoalState.Status.IN_PROGRESS : GoalState.Status.UNKNOWN;
        List<GoalState.MilestoneState> current = status == GoalState.Status.COMPLETE ? List.of()
            : milestones.stream().filter(value -> value.getStatus() == GoalState.MilestoneStatus.AVAILABLE)
                .collect(java.util.stream.Collectors.toUnmodifiableList());
        int completed = (int) milestones.stream()
            .filter(value -> value.getStatus() == GoalState.MilestoneStatus.COMPLETE).count();
        return new GoalState(goal, status, satisfied, missing, unknown, milestones, current,
            completed, milestones.size());
    }

    private GoalState.Check check(String scope, GoalState.CheckKind kind, Requirement requirement,
        FactLookup facts, Instant now)
    {
        Observation<Double> observation = facts.get(requirement.getFact());
        if (observation == null)
        {
            observation = Observation.unknown();
        }
        return new GoalState.Check(scope, kind, requirement, observation,
            requirement.evaluate(observation, now));
    }

    private List<GoalState.Check> filter(List<GoalState.Check> checks, Requirement.Result result)
    {
        return checks.stream().filter(check -> check.getResult() == result)
            .collect(java.util.stream.Collectors.toUnmodifiableList());
    }
}
