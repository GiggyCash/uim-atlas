package com.uimatlas.data;

import com.uimatlas.recommendation.GoalDefinition;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bundled goal index with cross-file identity validation and deterministic ordering. */
public final class ProductionGoalCatalog
{
    private static final String INDEX = "uimatlas/goals/catalogs.txt";

    public List<GoalDefinition> load(ClassLoader resources, Set<Integer> canonicalQuestIds,
        int maximumQuestPoints) throws IOException
    {
        Set<String> paths = new HashSet<>();
        Set<String> goalIds = new HashSet<>();
        Set<String> milestoneIds = new HashSet<>();
        List<GoalDefinition> goals = new ArrayList<>();
        GoalDefinitionLoader loader = new GoalDefinitionLoader(canonicalQuestIds, maximumQuestPoints);
        try (BufferedReader index = reader(resources, INDEX))
        {
            String path;
            while ((path = index.readLine()) != null)
            {
                if (!path.matches("uimatlas/goals/[a-z][a-z0-9-]*\\.json") || !paths.add(path))
                {
                    throw new IllegalArgumentException("Invalid or duplicate production goal path: " + path);
                }
                try (BufferedReader input = reader(resources, path))
                {
                    for (GoalDefinition goal : loader.loadProduction(input))
                    {
                        if (!goalIds.add(goal.getId()))
                        {
                            throw new IllegalArgumentException("Duplicate production goal ID: " + goal.getId());
                        }
                        for (GoalDefinition.Milestone milestone : goal.getMilestones())
                        {
                            if (!milestoneIds.add(milestone.getId()))
                            {
                                throw new IllegalArgumentException(
                                    "Duplicate production milestone ID: " + milestone.getId());
                            }
                        }
                        goals.add(goal);
                    }
                }
            }
        }
        if (goals.isEmpty())
        {
            throw new IllegalArgumentException("Empty production goal catalog index");
        }
        goals.sort(Comparator.comparing(GoalDefinition::getId));
        return List.copyOf(goals);
    }

    private BufferedReader reader(ClassLoader resources, String path) throws IOException
    {
        InputStream input = resources.getResourceAsStream(path);
        if (input == null)
        {
            throw new IOException("Missing production goal resource: " + path);
        }
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
