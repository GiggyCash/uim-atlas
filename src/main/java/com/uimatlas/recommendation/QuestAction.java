package com.uimatlas.recommendation;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;

/** Strategic preflight only. No item/combat preparation guarantee or executable integration. */
@Value
public class QuestAction implements StrategicAction
{
    @Value
    public static class Handoff
    {
        public enum Target { QUEST_HELPER }
        public enum Availability { MANUAL_ONLY }

        int questId;
        String displayName;
        Target target = Target.QUEST_HELPER;
        Availability availability = Availability.MANUAL_ONLY;
    }

    GoalState.MilestoneState state;
    /** Dependency explanations, following incomplete ancestors and stopping at verified completion. */
    List<GoalState.MilestoneState> dependencies;
    Handoff handoff;

    QuestAction(GoalState.MilestoneState state, List<GoalState.MilestoneState> dependencies, int questId)
    {
        this.state = state;
        this.dependencies = List.copyOf(dependencies);
        this.handoff = new Handoff(questId, state.getMilestone().getDisplayName());
    }

    @Override
    public String getId()
    {
        return state.getMilestone().getId();
    }

    @Override
    public Kind getKind()
    {
        return Kind.QUEST_MILESTONE;
    }

    @Override
    public Readiness getReadiness()
    {
        return state.getStatus() == GoalState.MilestoneStatus.AVAILABLE ? Readiness.READY_TO_HANDOFF
            : state.getStatus() == GoalState.MilestoneStatus.BLOCKED ? Readiness.BLOCKED : Readiness.UNRESOLVED;
    }

    public List<GoalState.Check> getBlockers()
    {
        return prerequisiteChecks().filter(check -> check.getResult() == Requirement.Result.MISSING)
            .collect(Collectors.toUnmodifiableList());
    }

    public List<GoalState.Check> getUnresolvedRequirements()
    {
        return Stream.concat(state.getChecks().stream(),
            dependencies.stream().filter(value -> value.getStatus() != GoalState.MilestoneStatus.COMPLETE)
                .flatMap(value -> value.getChecks().stream()))
            .filter(check -> check.getResult() == Requirement.Result.UNKNOWN)
            .collect(Collectors.toUnmodifiableList());
    }

    private Stream<GoalState.Check> prerequisiteChecks()
    {
        return Stream.concat(state.getChecks().stream()
            .filter(check -> check.getKind() == GoalState.CheckKind.PREREQUISITE), dependencyCompletions());
    }

    private Stream<GoalState.Check> dependencyCompletions()
    {
        return dependencies.stream().flatMap(value -> value.getChecks().stream())
            .filter(check -> check.getKind() == GoalState.CheckKind.COMPLETION);
    }
}
