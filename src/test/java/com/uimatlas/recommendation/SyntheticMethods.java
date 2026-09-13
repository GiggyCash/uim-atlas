package com.uimatlas.recommendation;

import com.uimatlas.data.MethodDefinitionLoader;
import com.uimatlas.state.Observation;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared test data access, deliberately absent from the plugin artifact. */
public final class SyntheticMethods
{
    public static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");

    private SyntheticMethods()
    {
    }

    public static String json() throws IOException
    {
        try (InputStream input = SyntheticMethods.class.getResourceAsStream("/uimatlas/methods/synthetic-methods.json"))
        {
            if (input == null)
            {
                throw new IOException("Missing synthetic methods fixture");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static List<MethodDefinition> load() throws IOException
    {
        return new MethodDefinitionLoader().loadSynthetic(new StringReader(json()));
    }

    public static Map<String, Observation<Double>> ready(MethodDefinition method)
    {
        Map<String, Observation<Double>> facts = new HashMap<>();
        method.getHardRequirements().forEach(requirement -> satisfy(facts, requirement));
        method.getPreparation().forEach(requirement -> satisfy(facts, requirement));
        method.getSetupItems().forEach(requirement -> satisfy(facts, requirement));
        method.getConsumes().forEach(requirement -> satisfy(facts, requirement));
        satisfy(facts, method.getFreeInventorySlots());
        return facts;
    }

    private static void satisfy(Map<String, Observation<Double>> facts, Requirement requirement)
    {
        facts.put(requirement.getFact(), Observation.verified(requirement.getTarget(), "synthetic observation", NOW));
    }
}
