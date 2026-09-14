package com.uimatlas.data;

import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.recommendation.Requirement;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static com.uimatlas.data.DefinitionJson.require;

/** Strict loader for the separate production goal resource family. */
public final class GoalDefinitionLoader
{
    private final Set<Integer> canonicalQuestIds;
    private final int maximumQuestPoints;

    public GoalDefinitionLoader(Set<Integer> canonicalQuestIds, int maximumQuestPoints)
    {
        this.canonicalQuestIds = Set.copyOf(canonicalQuestIds);
        require(maximumQuestPoints >= 0, "maximumQuestPoints: expected nonnegative boundary");
        this.maximumQuestPoints = maximumQuestPoints;
    }

    public List<GoalDefinition> loadProduction(Reader input)
    {
        try
        {
            DefinitionJson root = DefinitionJson.read(input)
                .fields("schemaVersion", "dataKind", "facts", "goals");
            require(root.integer("schemaVersion", Integer.MAX_VALUE) == 1,
                "$.schemaVersion: unsupported version");
            require(root.text("dataKind").equals("PRODUCTION"), "$.dataKind: expected PRODUCTION");
            root.rejectMarkers();
            Set<String> facts = new HashSet<>();
            root.list("facts", value ->
            {
                value.fields("id");
                String id = identifier(value, "id");
                validateFact(id, value.at("id"));
                require(facts.add(id), value.at("id") + ": duplicate fact ID");
                return id;
            });
            Set<String> goalIds = new HashSet<>();
            Set<String> milestoneIds = new HashSet<>();
            List<GoalDefinition> goals = root.list("goals",
                value -> goal(value, facts, goalIds, milestoneIds));
            require(!goals.isEmpty(), "$.goals: expected at least one goal");
            return goals;
        }
        catch (IOException exception)
        {
            throw new IllegalArgumentException("Invalid goal JSON: " + exception.getMessage(), exception);
        }
    }

    private GoalDefinition goal(DefinitionJson value, Set<String> facts, Set<String> goalIds,
        Set<String> milestoneIds)
    {
        value.fields("id", "displayName", "completion", "requirements", "milestones", "rewards", "sources");
        String id = identifier(value, "id");
        require(id.startsWith("goal."), value.at("id") + ": expected goal. prefix");
        require(goalIds.add(id), value.at("id") + ": duplicate goal ID");
        List<GoalDefinition.Milestone> milestones = value.list("milestones",
            milestone -> milestone(milestone, facts, milestoneIds));
        require(!milestones.isEmpty(), value.at("milestones") + ": expected at least one milestone");
        milestones = topological(milestones, value.at("milestones"));
        List<GoalDefinition.Source> sources = value.list("sources", this::source);
        require(!sources.isEmpty(), value.at("sources") + ": production goal requires source metadata");
        return new GoalDefinition(id, value.text("displayName"), completion(value.child("completion"), facts),
            requirements(value, "requirements", facts), milestones,
            value.list("rewards", reward ->
            {
                reward.fields("id", "description");
                return new GoalDefinition.Reward(identifier(reward, "id"), reward.text("description"));
            }), sources);
    }

    private GoalDefinition.Milestone milestone(DefinitionJson value, Set<String> facts, Set<String> ids)
    {
        value.fields("id", "displayName", "completion", "dependsOn", "requirements", "sources");
        String id = identifier(value, "id");
        require(id.startsWith("milestone."), value.at("id") + ": expected milestone. prefix");
        require(ids.add(id), value.at("id") + ": duplicate milestone ID");
        Set<String> dependencies = new HashSet<>();
        List<String> dependsOn = value.list("dependsOn", dependency ->
        {
            dependency.fields("id");
            String target = identifier(dependency, "id");
            require(dependencies.add(target), dependency.at("id") + ": duplicate dependency");
            return target;
        });
        List<GoalDefinition.Source> sources = value.list("sources", this::source);
        require(!sources.isEmpty(), value.at("sources") + ": milestone requires source metadata");
        return new GoalDefinition.Milestone(id, value.text("displayName"),
            completion(value.child("completion"), facts), dependsOn,
            requirements(value, "requirements", facts), sources);
    }

    private List<GoalDefinition.Milestone> topological(List<GoalDefinition.Milestone> milestones, String path)
    {
        Map<String, GoalDefinition.Milestone> byId = new HashMap<>();
        Map<String, Integer> remaining = new HashMap<>();
        Map<String, Set<String>> dependants = new HashMap<>();
        milestones.forEach(milestone -> byId.put(milestone.getId(), milestone));
        for (GoalDefinition.Milestone milestone : milestones)
        {
            remaining.put(milestone.getId(), milestone.getDependsOn().size());
            for (String dependency : milestone.getDependsOn())
            {
                require(byId.containsKey(dependency), path + ": unresolved child reference " + dependency);
                require(!dependency.equals(milestone.getId()), path + ": cycle at " + dependency);
                dependants.computeIfAbsent(dependency, ignored -> new TreeSet<>()).add(milestone.getId());
            }
        }
        TreeSet<String> ready = new TreeSet<>();
        remaining.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<GoalDefinition.Milestone> ordered = new ArrayList<>();
        while (!ready.isEmpty())
        {
            String id = ready.pollFirst();
            ordered.add(byId.get(id));
            for (String dependant : dependants.getOrDefault(id, Set.of()))
            {
                int count = remaining.computeIfPresent(dependant, (ignored, prior) -> prior - 1);
                if (count == 0)
                {
                    ready.add(dependant);
                }
            }
        }
        require(ordered.size() == milestones.size(), path + ": milestone dependency cycle");
        return List.copyOf(ordered);
    }

    private List<Requirement> requirements(DefinitionJson value, String field, Set<String> facts)
    {
        Set<String> seen = new HashSet<>();
        return value.list(field, requirement ->
        {
            Requirement parsed = requirement(requirement, facts);
            require(seen.add(parsed.getFact()), requirement.at("fact") + ": duplicate requirement fact");
            return parsed;
        });
    }

    private Requirement completion(DefinitionJson value, Set<String> facts)
    {
        Requirement completion = requirement(value, facts);
        require(completion.getComparison() == Requirement.Comparison.EQUAL && completion.getTarget() == 1
            && (completion.getFact().matches("quest\\.(0|[1-9][0-9]*)\\.complete")
                || completion.getFact().startsWith("capability.")),
            value.at("fact") + ": invalid completion definition");
        return completion;
    }

    private Requirement requirement(DefinitionJson value, Set<String> facts)
    {
        value.fields("fact", "comparison", "target", "description", "allowLastObserved",
            "maxAgeSeconds", "safetyRelevant");
        String fact = identifier(value, "fact");
        require(facts.contains(fact), value.at("fact") + ": unresolved fact ID " + fact);
        double target = value.number("target", 0, Double.MAX_VALUE);
        if (fact.matches("skill\\.[a-z][a-z0-9_]*\\.level"))
        {
            require(target >= 1 && target <= 99 && target == Math.rint(target),
                value.at("target") + ": invalid skill level");
        }
        if (fact.matches("quest\\.(0|[1-9][0-9]*)\\.(complete|started)") || fact.startsWith("capability."))
        {
            require(target == 0 || target == 1, value.at("target") + ": expected boolean target");
        }
        if (fact.equals("account.quest_points"))
        {
            require(target == Math.rint(target) && target <= maximumQuestPoints,
                value.at("target") + ": invalid quest-point range");
        }
        return new Requirement(fact, value.choice("comparison", Requirement.Comparison.class), target,
            value.text("description"), value.bool("allowLastObserved"),
            value.integer("maxAgeSeconds", Integer.MAX_VALUE), value.bool("safetyRelevant"));
    }

    private void validateFact(String id, String path)
    {
        if (id.matches("quest\\.(0|[1-9][0-9]*)\\.(complete|started)"))
        {
            String rawId = id.split("\\.")[1];
            require(rawId.length() <= 10 && Long.parseLong(rawId) <= Integer.MAX_VALUE,
                path + ": invalid quest ID");
            require(canonicalQuestIds.contains(Integer.parseInt(rawId)),
                path + ": quest ID outside canonical boundary");
            return;
        }
        require(id.matches("skill\\.[a-z][a-z0-9_]*\\.level") || id.equals("account.quest_points")
            || id.matches("capability\\.[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+"),
            path + ": unsupported or malformed goal fact ID");
    }

    private GoalDefinition.Source source(DefinitionJson value)
    {
        value.fields("url", "reviewedAt", "notes");
        String url = value.text("url");
        URI uri = URI.create(url);
        require("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null,
            value.at("url") + ": expected absolute HTTPS source URL");
        String date = value.text("reviewedAt");
        try
        {
            require(date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), value.at("reviewedAt") + ": expected ISO date");
            return new GoalDefinition.Source(url, LocalDate.parse(date), value.text("notes"));
        }
        catch (DateTimeParseException exception)
        {
            throw new IllegalArgumentException(value.at("reviewedAt") + ": invalid review date", exception);
        }
    }

    private String identifier(DefinitionJson value, String name)
    {
        String id = value.text(name);
        require(id.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+"), value.at(name) + ": invalid stable ID");
        return id;
    }
}
