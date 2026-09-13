package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodEvaluator.Status.*;
import static com.uimatlas.recommendation.SyntheticMethods.NOW;
import static org.junit.Assert.*;

public class MethodEvaluatorTest
{
    private final MethodEvaluator evaluator = new MethodEvaluator();
    private MethodDefinition method;
    private Map<String, Observation<Double>> facts;

    @Before
    public void setUp() throws Exception
    {
        method = SyntheticMethods.load().get(0);
        facts = SyntheticMethods.ready(method);
    }

    @Test
    public void satisfiedRequirementsDisappearFromPreparation()
    {
        MethodEvaluator.Evaluation result = evaluate();
        assertEquals(AVAILABLE, result.getStatus());
        assertTrue(result.getMissingPreparation().isEmpty());
        assertTrue(result.getBlockers().isEmpty());
        assertTrue(result.getUnknownRequirements().isEmpty());
    }

    @Test
    public void onlyActualMissingPreparationIsReturned()
    {
        Requirement prep = method.getPreparation().get(0);
        missing(prep);
        assertEquals(NEEDS_PREP, evaluate().getStatus());
        assertEquals(List.of(prep), evaluate().getMissingPreparation());
        assertFalse(evaluate().getMissingPreparation().contains(method.getHardRequirements().get(0)));
    }

    @Test
    public void setupAndConsumedInputsAndFreeSlotsAreEvaluated()
    {
        for (Requirement requirement : List.of(method.getSetupItems().get(0), method.getConsumes().get(0), method.getFreeInventorySlots()))
        {
            facts = SyntheticMethods.ready(method);
            missing(requirement);
            assertEquals(NEEDS_PREP, evaluate().getStatus());
            assertEquals(List.of(requirement), evaluate().getMissingPreparation());
            facts.remove(requirement.getFact());
            assertEquals(UNKNOWN, evaluate().getStatus());
            assertTrue(evaluate().getMissingPreparation().isEmpty());
        }
    }

    @Test
    public void knownMissingHardRequirementBlocksWithoutBecomingPrep()
    {
        Requirement hard = method.getHardRequirements().get(0);
        missing(hard);
        assertEquals(BLOCKED, evaluate().getStatus());
        assertEquals(List.of(hard), evaluate().getBlockers());
        assertTrue(evaluate().getMissingPreparation().isEmpty());
    }

    @Test
    public void absentAndExplicitlyUnknownHardFactsRemainUnknown()
    {
        Requirement hard = method.getHardRequirements().get(0);
        facts.remove(hard.getFact());
        assertEquals(UNKNOWN, evaluate().getStatus());
        assertEquals(List.of(hard), evaluate().getUnknownRequirements());
        facts.put(hard.getFact(), Observation.unknown());
        assertEquals(UNKNOWN, evaluate().getStatus());
    }

    @Test
    public void unknownSafetyIsNeverSatisfiedEvenWithARecordedTrueValue()
    {
        Requirement safety = method.getHardRequirements().get(1);
        List<Observation<Double>> untrusted = List.of(Observation.unknown(),
            Observation.verified(1.0, "synthetic", NOW).lastObserved(),
            Observation.verified(1.0, "synthetic", NOW.minusSeconds(61)),
            Observation.verified(1.0, "synthetic", NOW.plusSeconds(1)),
            Observation.verified(Double.NaN, "synthetic", NOW));
        for (Observation<Double> observation : untrusted)
        {
            facts.put(safety.getFact(), observation);
            assertEquals(UNKNOWN, evaluate().getStatus());
            assertEquals(List.of(safety), evaluate().getUnknownRequirements());
        }
        missing(safety);
        assertEquals(BLOCKED, evaluate().getStatus());
    }

    @Test
    public void precedencePreservesUnknownDiagnosticsAndKnownPrep()
    {
        missing(method.getPreparation().get(0));
        facts.remove(method.getHardRequirements().get(1).getFact());
        assertEquals(UNKNOWN, evaluate().getStatus());
        missing(method.getHardRequirements().get(0));
        assertEquals(BLOCKED, evaluate().getStatus());
        assertEquals(1, evaluate().getUnknownRequirements().size());
        assertEquals(1, evaluate().getMissingPreparation().size());
    }

    @Test
    public void confidenceAndAgeAreSeparatePolicies()
    {
        Requirement gate = new Requirement("synthetic.level", Requirement.Comparison.AT_LEAST, 10,
            "Synthetic gate", true, 60, false);
        Observation<Double> observed = Observation.verified(10.0, "synthetic", NOW.minusSeconds(60)).lastObserved();
        assertEquals(Requirement.Result.SATISFIED, gate.evaluate(observed, NOW));
        assertEquals(Requirement.Result.UNKNOWN, gate.evaluate(observed, NOW.plusSeconds(1)));
        assertEquals("synthetic", observed.getSource());
        assertThrows(IllegalArgumentException.class, () -> new Requirement("synthetic.safety",
            Requirement.Comparison.EQUAL, 1, "Synthetic safety", true, 60, true));
    }

    @Test
    public void numericPredicatesAndStopMetadataDoNotCreateExtraStartGates()
    {
        Requirement stop = method.getStopConditions().get(0);
        assertEquals(Requirement.Result.UNKNOWN, stop.evaluate(null, NOW));
        assertEquals(AVAILABLE, evaluate().getStatus());
        Requirement maximum = new Requirement("synthetic.count", Requirement.Comparison.AT_MOST, 2,
            "Synthetic maximum", false, 60, false);
        assertEquals(Requirement.Result.SATISFIED, maximum.evaluate(Observation.verified(2.0, "synthetic", NOW), NOW));
        assertEquals(Requirement.Result.MISSING, maximum.evaluate(Observation.verified(3.0, "synthetic", NOW), NOW));
    }

    @Test
    public void definitionAndEvaluationCollectionsAreImmutable()
    {
        assertThrows(UnsupportedOperationException.class, () -> method.getPreparation().clear());
        assertThrows(UnsupportedOperationException.class, () -> evaluate().getMissingPreparation().clear());
    }

    private void missing(Requirement requirement)
    {
        facts.put(requirement.getFact(), Observation.verified(0.0, "synthetic", NOW));
    }

    private MethodEvaluator.Evaluation evaluate()
    {
        return evaluator.evaluate(method, facts, NOW);
    }
}
