package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.util.List;
import lombok.Value;

/** Immutable, provenance-retaining evaluation of one selected goal. */
@Value
public class GoalState
{
    public enum Status { COMPLETE, IN_PROGRESS, UNKNOWN }
    public enum CheckKind { COMPLETION, PREREQUISITE }
    public enum MilestoneStatus { COMPLETE, AVAILABLE, BLOCKED, UNKNOWN }

    @Value
    public static class Check
    {
        String scopeId;
        CheckKind kind;
        Requirement requirement;
        Observation<Double> observation;
        Requirement.Result result;
    }

    @Value
    public static class MilestoneState
    {
        GoalDefinition.Milestone milestone;
        MilestoneStatus status;
        List<Check> checks;

        public MilestoneState(GoalDefinition.Milestone milestone, MilestoneStatus status, List<Check> checks)
        {
            this.milestone = milestone;
            this.status = status;
            this.checks = List.copyOf(checks);
        }
    }

    GoalDefinition goal;
    Status status;
    List<Check> satisfiedRequirements;
    List<Check> missingRequirements;
    List<Check> unknownRequirements;
    List<MilestoneState> milestones;
    List<MilestoneState> currentMilestones;
    int completedMilestones;
    int totalMilestones;

    public GoalState(GoalDefinition goal, Status status, List<Check> satisfiedRequirements,
        List<Check> missingRequirements, List<Check> unknownRequirements,
        List<MilestoneState> milestones, List<MilestoneState> currentMilestones,
        int completedMilestones, int totalMilestones)
    {
        this.goal = goal;
        this.status = status;
        this.satisfiedRequirements = List.copyOf(satisfiedRequirements);
        this.missingRequirements = List.copyOf(missingRequirements);
        this.unknownRequirements = List.copyOf(unknownRequirements);
        this.milestones = List.copyOf(milestones);
        this.currentMilestones = List.copyOf(currentMilestones);
        this.completedMilestones = completedMilestones;
        this.totalMilestones = totalMilestones;
    }
}
