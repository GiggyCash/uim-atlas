package com.uimatlas.data;

import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.Requirement;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.uimatlas.data.DefinitionJson.require;

/** Separate, strict entry points for bundled production v2 and synthetic v1. The caller owns the reader. */
public final class MethodDefinitionLoader
{
    public List<MethodDefinition> loadSynthetic(Reader input)
    {
        return load(input, false, Set.of());
    }

    /** Item existence is checked against a caller-supplied canonical boundary, never fetched here. */
    public List<MethodDefinition> loadProduction(Reader input, Set<Integer> canonicalItemIds)
    {
        return load(input, true, Set.copyOf(canonicalItemIds));
    }

    private List<MethodDefinition> load(Reader input, boolean production, Set<Integer> canonicalItemIds)
    {
        try
        {
            DefinitionJson root = DefinitionJson.read(input).fields("schemaVersion", "dataKind", "facts", "methods");
            require(root.integer("schemaVersion", Integer.MAX_VALUE) == (production ? 2 : 1), "$.schemaVersion: unsupported version");
            String kind = production ? "PRODUCTION" : "SYNTHETIC_TEST_ONLY";
            require(root.text("dataKind").equals(kind), "$.dataKind: expected " + kind);
            if (production)
            {
                root.rejectMarkers();
            }
            Set<String> facts = new HashSet<>();
            root.list("facts", fact ->
            {
                fact.fields("id");
                String id = identifier(fact, "id");
                if (production)
                {
                    validateProductionFact(id, canonicalItemIds, fact.at("id"));
                }
                require(facts.add(id), fact.at("id") + ": duplicate fact ID");
                return id;
            });
            Set<String> ids = new HashSet<>();
            List<MethodDefinition> methods = root.list("methods", method -> parseMethod(method, facts, ids, production));
            require(!methods.isEmpty(), "$.methods: expected at least one method");
            return methods;
        }
        catch (IOException exception)
        {
            throw new IllegalArgumentException("Invalid method JSON: " + exception.getMessage(), exception);
        }
    }

    private MethodDefinition parseMethod(DefinitionJson method, Set<String> facts, Set<String> ids, boolean production)
    {
        List<String> fields = new ArrayList<>(Arrays.asList("id", "displayName", "category", "activity", "start", "hardRequirements", "preparation",
            "freeInventorySlots", "setupItems", "consumes", "produces", "stopConditions", "style", "xpRate", "costs", "danger", "reason"));
        if (production)
        {
            fields.addAll(List.of("optionalSetup", "sources"));
        }
        method.fields(fields.toArray(new String[0]));
        String id = identifier(method, "id");
        String prefix = production ? "method." : "synthetic.method.";
        require(id.startsWith(prefix), method.at("id") + ": expected " + prefix + " prefix");
        require(ids.add(id), method.at("id") + ": duplicate method ID");
        String name = method.text("displayName");
        require(production || name.startsWith("Synthetic "), method.at("displayName") + ": expected Synthetic label");
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
        List<Requirement> optional = production ? method.list("optionalSetup", value -> requirement(value, facts)) : List.of();
        Set<String> requiredFacts = new HashSet<>(prepFacts);
        hard.forEach(value -> requiredFacts.add(value.getFact()));
        for (Requirement value : optional)
        {
            require(!value.isSafetyRelevant() && requiredFacts.add(value.getFact()),
                method.at("optionalSetup") + ": optional facts must be distinct and cannot be safety gates");
        }
        List<MethodDefinition.Source> sources = production ? method.list("sources", this::source) : List.of();
        require(!production || !sources.isEmpty(), method.at("sources") + ": production requires source metadata");
        return new MethodDefinition(id, name, method.text("category"), method.text("activity"),
            new MethodDefinition.Start(start.text("location"), start.text("contact"), start.text("instruction")),
            hard, prep, slots, setup, consumes, method.list("produces", value -> resource(value, facts)), stops,
            new MethodDefinition.Style(style.number("attention", 0, 1), style.text("playStyle"), style.bool("tickManipulation")),
            new MethodDefinition.XpRate(minimumXp, xp.number("maximum", minimumXp, Double.MAX_VALUE), xp.text("assumptions")),
            new MethodDefinition.Costs(costs.number("storageUnlockValue", 0, 1), costs.number("setupMinutes", 0, Double.MAX_VALUE),
                costs.number("transitionMinutes", 0, Double.MAX_VALUE), costs.number("inventoryDisruption", 0, 1), costs.text("assumptions")),
            method.choice("danger", MethodDefinition.Danger.class), method.text("reason"),
            production ? MethodDefinition.DataKind.PRODUCTION : MethodDefinition.DataKind.SYNTHETIC_TEST_ONLY, optional, sources);
    }

    private Requirement requirement(DefinitionJson value, Set<String> facts)
    {
        value.fields("fact", "comparison", "target", "description", "allowLastObserved", "maxAgeSeconds", "safetyRelevant");
        String fact = reference(value, "fact", facts);
        boolean safety = value.bool("safetyRelevant");
        boolean allowLast = value.bool("allowLastObserved");
        require(!safety || !allowLast, value.at("allowLastObserved") + ": safety requires current verification");
        double target = value.number("target", 0, Double.MAX_VALUE);
        if (fact.matches("skill\\.[a-z][a-z0-9_]*\\.level"))
        {
            require(target >= 1 && target <= 99 && target == Math.rint(target), value.at("target") + ": invalid level range");
        }
        if (fact.equals("inventory.free_slots") || fact.equals("inventory.occupied_slots"))
        {
            require(target <= 28 && target == Math.rint(target), value.at("target") + ": invalid slot range");
        }
        if (fact.startsWith("capability."))
        {
            require(target == 0 || target == 1, value.at("target") + ": expected boolean capability");
        }
        return new Requirement(fact, value.choice("comparison", Requirement.Comparison.class), target,
            value.text("description"), allowLast, value.integer("maxAgeSeconds", Integer.MAX_VALUE), safety);
    }

    private void validateProductionFact(String id, Set<Integer> items, String path)
    {
        if (id.matches("(inventory|equipment|carried)\\.item\\.(0|[1-9][0-9]*)\\.quantity"))
        {
            String number = id.split("\\.")[2];
            require(number.length() <= 10 && Long.parseLong(number) <= Integer.MAX_VALUE,
                path + ": invalid item ID");
            require(items.contains(Integer.parseInt(number)), path + ": item ID outside canonical boundary");
            return;
        }
        require(id.matches("skill\\.[a-z][a-z0-9_]*\\.(level|xp)")
            || id.equals("inventory.free_slots") || id.equals("inventory.occupied_slots")
            || id.startsWith("capability."), path + ": unsupported or malformed production fact ID");
    }

    private MethodDefinition.Source source(DefinitionJson value)
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
            return new MethodDefinition.Source(url, LocalDate.parse(date), value.text("notes"));
        }
        catch (DateTimeParseException exception)
        {
            throw new IllegalArgumentException(value.at("reviewedAt") + ": invalid review date", exception);
        }
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
