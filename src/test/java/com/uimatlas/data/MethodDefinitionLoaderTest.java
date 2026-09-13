package com.uimatlas.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.MethodScorer;
import com.uimatlas.recommendation.SyntheticMethods;
import java.io.StringReader;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class MethodDefinitionLoaderTest
{
    @Test
    public void syntheticCatalogLoadsUsefulMetadataAndImmutableCollections() throws Exception
    {
        List<MethodDefinition> methods = SyntheticMethods.load();
        assertEquals(3, methods.size());
        MethodDefinition method = methods.get(0);
        assertTrue(method.getStart().getInstruction().contains("synthetic"));
        assertFalse(method.getStopConditions().isEmpty());
        assertFalse(method.getReason().isBlank());
        assertEquals(1, method.getProduces().get(0).getQuantity(), 0);
        assertThrows(UnsupportedOperationException.class, methods::clear);
    }

    @Test
    public void malformedSyntaxAndDuplicateKeysFailClearly() throws Exception
    {
        reject("{", "JSON");
        reject(SyntheticMethods.json().replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"), "duplicate field");
        reject(SyntheticMethods.json() + " {}", "JSON");
        reject(SyntheticMethods.json().replace("\"schemaVersion\": 1", "\"schemaVersion\": null"), "non-null");
        reject(SyntheticMethods.json().replace("\"schemaVersion\": 1", "schemaVersion: 1"), "JSON");
    }

    @Test
    public void unsupportedVersionsKindsAndDuplicateIdsAreRejected() throws Exception
    {
        mutate(root -> root.addProperty("schemaVersion", 2), "schemaVersion");
        mutate(root -> root.addProperty("dataKind", "PRODUCTION"), "dataKind");
        mutate(root -> root.getAsJsonArray("methods").add(first(root).deepCopy()), "duplicate method ID");
        mutate(root -> root.getAsJsonArray("facts").add(root.getAsJsonArray("facts").get(0).deepCopy()), "duplicate fact ID");
        mutate(root -> first(root).addProperty("id", "method.real"), "synthetic.method.");
        mutate(root -> first(root).addProperty("displayName", "Unlabelled fixture"), "Synthetic label");
    }

    @Test
    public void missingUnknownAndMistypedFieldsAreRejected() throws Exception
    {
        mutate(root -> first(root).remove("stopConditions"), "missing fields");
        mutate(root -> first(root).addProperty("typo", true), "unknown fields");
        mutate(root -> first(root).getAsJsonObject("xpRate").addProperty("minimum", "100"), "expected number");
        mutate(root -> first(root).getAsJsonObject("style").addProperty("tickManipulation", "false"), "expected boolean");
        mutate(root -> first(root).addProperty("preparation", "invalid"), "expected array");
        mutate(root -> first(root).addProperty("reason", " "), "nonblank");
    }

    @Test
    public void invalidRangesPoliciesAndReferencesAreRejected() throws Exception
    {
        mutate(root -> first(root).getAsJsonObject("xpRate").addProperty("maximum", 1), "maximum");
        mutate(root -> first(root).getAsJsonObject("costs").addProperty("setupMinutes", -1), "setupMinutes");
        mutate(root -> first(root).getAsJsonObject("style").addProperty("attention", 2), "attention");
        mutate(root -> first(root).addProperty("danger", "SAFE_ENOUGH"), "danger");
        mutate(root -> gate(root, 0).addProperty("fact", "synthetic.undeclared"), "unresolved fact");
        mutate(root -> gate(root, 0).addProperty("comparison", "GUESS"), "comparison");
        mutate(root -> gate(root, 0).addProperty("maxAgeSeconds", 1.5), "integer");
        mutate(root -> gate(root, 1).addProperty("allowLastObserved", true), "current verification");
        mutate(root -> first(root).getAsJsonArray("preparation").get(0).getAsJsonObject().addProperty("safetyRelevant", true), "hard gates");
        mutate(root -> first(root).getAsJsonArray("preparation").add(first(root).getAsJsonObject("freeInventorySlots").deepCopy()), "duplicate preparation fact");
        mutate(root -> first(root).getAsJsonArray("stopConditions").remove(0), "stop condition");
        mutate(root -> first(root).getAsJsonArray("setupItems").get(0).getAsJsonObject().addProperty("target", 0), "positive integer");
        mutate(root -> first(root).getAsJsonArray("produces").get(0).getAsJsonObject().addProperty("quantity", 0), "quantity");
    }

    @Test
    public void unknownDangerCannotReceiveAScore() throws Exception
    {
        JsonObject root = new JsonParser().parse(SyntheticMethods.json()).getAsJsonObject();
        first(root).addProperty("danger", "UNKNOWN");
        MethodDefinition method = new MethodDefinitionLoader().loadSynthetic(new StringReader(root.toString())).get(0);
        MethodEvaluator.Evaluation evaluation = new MethodEvaluator().evaluate(method, SyntheticMethods.ready(method), SyntheticMethods.NOW);
        assertEquals(MethodEvaluator.Status.UNKNOWN, evaluation.getStatus());
        assertFalse(new MethodScorer().score(evaluation, java.util.Map.of()).isPresent());
    }

    private void mutate(Consumer<JsonObject> change, String expected) throws Exception
    {
        JsonObject root = new JsonParser().parse(SyntheticMethods.json()).getAsJsonObject();
        change.accept(root);
        reject(root.toString(), expected);
    }

    private void reject(String json, String expected)
    {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new MethodDefinitionLoader().loadSynthetic(new StringReader(json)));
        assertTrue(exception.getMessage(), exception.getMessage().contains(expected));
    }

    private static JsonObject first(JsonObject root)
    {
        return root.getAsJsonArray("methods").get(0).getAsJsonObject();
    }

    private static JsonObject gate(JsonObject root, int index)
    {
        return first(root).getAsJsonArray("hardRequirements").get(index).getAsJsonObject();
    }
}
