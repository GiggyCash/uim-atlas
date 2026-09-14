package com.uimatlas.recommendation;

import java.time.LocalDate;
import java.util.List;
import lombok.Value;

/** Immutable, data-loaded strategic goal graph. */
@Value
public class GoalDefinition
{
    String id;
    String displayName;
    Requirement completion;
    List<Requirement> requirements;
    /** Deterministic topological order, with stable IDs breaking independent-stage ties. */
    List<Milestone> milestones;
    List<Reward> rewards;
    List<Source> sources;

    public GoalDefinition(String id, String displayName, Requirement completion,
        List<Requirement> requirements, List<Milestone> milestones, List<Reward> rewards, List<Source> sources)
    {
        this.id = id;
        this.displayName = displayName;
        this.completion = completion;
        this.requirements = List.copyOf(requirements);
        this.milestones = List.copyOf(milestones);
        this.rewards = List.copyOf(rewards);
        this.sources = List.copyOf(sources);
    }

    @Value
    public static class Milestone
    {
        String id;
        String displayName;
        Requirement completion;
        List<String> dependsOn;
        List<Requirement> requirements;
        List<Source> sources;

        public Milestone(String id, String displayName, Requirement completion, List<String> dependsOn,
            List<Requirement> requirements, List<Source> sources)
        {
            this.id = id;
            this.displayName = displayName;
            this.completion = completion;
            this.dependsOn = List.copyOf(dependsOn);
            this.requirements = List.copyOf(requirements);
            this.sources = List.copyOf(sources);
        }
    }

    @Value
    public static class Reward
    {
        String id;
        String description;
    }

    @Value
    public static class Source
    {
        String url;
        LocalDate reviewedAt;
        String notes;
    }
}
