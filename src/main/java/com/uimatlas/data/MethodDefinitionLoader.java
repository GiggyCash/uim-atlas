package com.uimatlas.data;

import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.Requirement;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.uimatlas.data.DefinitionJson.require;

/** Version 1 deliberately accepts only synthetic catalogs. The caller owns the reader. */
public final class MethodDefinitionLoader
{
    public List<MethodDefinition> loadSynthetic(Reader input)
    {
        try
        {
            DefinitionJson root = DefinitionJson.read(input).fields("schemaVersion", "dataKind", "facts", "methods");
            require(root.integer("schemaVersion", Integer.MAX_VALUE) == 1, "$.schemaVersion: unsupported version");
            require(root.text("dataKind").equals("SYNTHETIC_TEST_ONLY"), "$.dataKind: only SYNTHETIC_TEST_ONLY is supported");
            Set<String> facts = new HashSet<>();
            root.list("facts", fact ->
            {
                fact.fields("id");
                String id = identifier(fact, "id");
                require(facts.add(id), fact.at("id") + ": duplicate fact ID");
                return id;
            });
            Set<String> ids = new HashSet<>();
            List<MethodDefinition> methods = root.list("methods", method -> parseMethod(method, facts, ids));
            require(!methods.isEmpty(), "$.methods: expected at least one method");
            return methods;
        }
        catch (IOException exception)
        {
            throw new IllegalArgumentException("Invalid method JSON: " + exception.getMessage(), exception);
        }
    }

    private MethodDefinition parseMethod(DefinitionJson method, Set<String> facts, Set<String> ids)
    {
        method.fields("id", "displayName", "category", "activity", "start", "hardRequirements", "preparation",
            "freeInventorySlots", "setupItems", "consumes", "produces", "stopConditions", "style", "xpRate", "costs", "danger", "reason");
        String id = identifier(method, "id");
        require(id.startsWith("synthetic.method."), method.at("id") + ": expected synthetic.method. prefix");
        require(ids.add(id), method.at("id") + ": duplicate method ID");
        String name = method.text("displayName");
        require(name.startsWith("Synthetic "), method.at("displayName") + ": expected Synthetic label");
        DefinitionJson start = method.child("start").fields("location", "contact", "instruction");
        DefinitionJson style = method.child("style").fields("attention", "playStyle", "tickManipulation");
        DefinitionJson xp = method.child("xpRate").fields("minimum", "maximum", "assumptions");
        double minimumXp = xp.number("minimum", 0, Double.MAX_VALUE);
        DefinitionJson costs = method.child("costs").fields("storageUnlockValue", "setupMinutes", "transitionMinutes",
            "inventoryDisruption", "assumptions");
        List<Requirement> hard = method.list("hardRequirements", value -> requirement(value, facts));
        List<Requirement> prep = method.list("preparation", value -> requirement(value, facts));
        List<Requirement> setup = method.list("setupItems", value -> quantityRequirement(value, facts));
        List<Requirement> consumes = method.list("consumes", value -> quantityRequirement(value, facts));
        Requirement slots = requirement(method.child("freeInventorySlots"), facts);
        require(slots.getFact().equals("inventory.free_slots") && slots.getComparison() == Requirement.Comparison.AT_LEAST
            && slots.getTarget() == Math.rint(slots.getTarget()), method.at("freeInventorySlots") + ": expected integer free-slot minimum");
        List<Requirement> preparation = new ArrayList<>(prep);
        preparation.add(slots);
        preparation.addAll(setup);
        preparation.addAll(consumes);
        Set<String> prepFacts = new HashSet<>();
        for (Requirement requirement : preparation)
        {
            require(!requirement.isSafetyRelevant(), method.at("preparation") + ": safety requirements must be hard gates");
            require(prepFacts.add(requirement.getFact()), method.at("preparation") + ": duplicate preparation fact " + requirement.getFact());
        }
        List<Requirement> stops = method.list("stopConditions", value -> requirement(value, facts));
        require(!stops.isEmpty(), method.at("stopConditions") + ": expected at least one stop condition");
        return new MethodDefinition(id, name, method.text("category"), method.text("activity"),
            new MethodDefinition.Start(start.text("location"), start.text("contact"), start.text("instruction")),
            hard, prep, slots, setup, consumes, method.list("produces", value -> resource(value, facts)), stops,
            new MethodDefinition.Style(style.number("attention", 0, 1), style.text("playStyle"), style.bool("tickManipulation")),
            new MethodDefinition.XpRate(minimumXp, xp.number("maximum", minimumXp, Double.MAX_VALUE), xp.text("assumptions")),
            new MethodDefinition.Costs(costs.number("storageUnlockValue", 0, 1), costs.number("setupMinutes", 0, Double.MAX_VALUE),
                costs.number("transitionMinutes", 0, Double.MAX_VALUE), costs.number("inventoryDisruption", 0, 1), costs.text("assumptions")),
            method.choice("danger", MethodDefinition.Danger.class), method.text("reason"));
    }

    private Requirement requirement(DefinitionJson value, Set<String> facts)
    {
        value.fields("fact", "comparison", "target", "description", "allowLastObserved", "maxAgeSeconds", "safetyRelevant");
        String fact = reference(value, "fact", facts);
        boolean safety = value.bool("safetyRelevant");
        boolean allowLast = value.bool("allowLastObserved");
        require(!safety || !allowLast, value.at("allowLastObserved") + ": safety requires current verification");
        return new Requirement(fact, value.choice("comparison", Requirement.Comparison.class), value.number("target", 0, Double.MAX_VALUE),
            value.text("description"), allowLast, value.integer("maxAgeSeconds", Integer.MAX_VALUE), safety);
    }

    private Requirement quantityRequirement(DefinitionJson value, Set<String> facts)
    {
        Requirement requirement = requirement(value, facts);
        require(requirement.getComparison() == Requirement.Comparison.AT_LEAST && requirement.getTarget() > 0
            && requirement.getTarget() == Math.rint(requirement.getTarget()), value.at("target") + ": expected positive integer AT_LEAST quantity");
        return requirement;
    }

    private MethodDefinition.ResourceAmount resource(DefinitionJson value, Set<String> facts)
    {
        value.fields("resourceId", "quantity", "basis");
        return new MethodDefinition.ResourceAmount(reference(value, "resourceId", facts),
            value.number("quantity", Double.MIN_VALUE, Double.MAX_VALUE), value.text("basis"));
    }

    private String reference(DefinitionJson value, String name, Set<String> facts)
    {
        String id = identifier(value, name);
        require(facts.contains(id), value.at(name) + ": unresolved fact ID " + id);
        return id;
    }

    private String identifier(DefinitionJson value, String name)
    {
        String id = value.text(name);
        require(id.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+"), value.at(name) + ": invalid stable ID");
        return id;
    }
}
