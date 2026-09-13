package com.uimatlas.recommendation;

import com.uimatlas.recommendation.MethodRanker.Candidate;
import com.uimatlas.recommendation.MethodRanker.RankedCandidate;
import com.uimatlas.recommendation.MethodRanker.RankingResult;
import com.uimatlas.state.Observation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodDefinition.Danger.*;
import static com.uimatlas.recommendation.MethodEvaluator.Status.*;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static com.uimatlas.recommendation.SyntheticMethods.*;
import static org.junit.Assert.*;

public class MethodRankerTest
{
    private final MethodRanker ranker = new MethodRanker();
    private final FactLookup facts = fact -> "inventory.free_slots".equals(fact)
        ? Observation.verified(28.0, "synthetic inventory", NOW) : Observation.unknown();

    @Test
    public void highestTrustworthyScoreWinsAndAlternativesExcludeWinner()
    {
        Candidate low = candidate("low", 0.2);
        Candidate high = candidate("high", 0.9);
        Candidate middle = candidate("middle", 0.5);
        RankingResult result = rank(low, high, middle);
        assertEquals(List.of(high, middle, low), candidates(result.getRankedCandidates()));
        assertEquals(high, result.getBest().orElseThrow().getCandidate());
        assertEquals(List.of(middle, low), candidates(result.getAlternatives()));
        assertTrue(result.getExcludedCandidates().isEmpty());
    }

    @Test
    public void blockedCandidateCannotWinDespiteMaximumBenefitsAndKeepsAllDiagnostics()
    {
        Requirement blocked = requirement("inventory.free_slots", 29);
        Requirement unknown = requirement("synthetic.unobserved", 1);
        Requirement prep = requirement("synthetic.prep", 1);
        Candidate invalid = new Candidate(candidateMethod("blocked", List.of(blocked, unknown), List.of(prep), LOW), attractive());
        FactLookup snapshot = fact -> "synthetic.prep".equals(fact)
            ? Observation.verified(0.0, "synthetic prep", NOW) : facts.get(fact);
        Candidate valid = candidate("valid", 0.1);
        RankingResult result = ranker.rank(List.of(invalid, valid), snapshot, NOW);
        assertEquals(valid, result.getBest().orElseThrow().getCandidate());
        RankedCandidate excluded = result.getExcludedCandidates().get(0);
        assertEquals(BLOCKED, excluded.getEvaluation().getStatus());
        assertEquals(List.of(blocked), excluded.getEvaluation().getBlockers());
        assertEquals(List.of(unknown), excluded.getEvaluation().getUnknownRequirements());
        assertEquals(List.of(prep), excluded.getEvaluation().getMissingPreparation());
        assertFalse(excluded.getScore().isPresent());
    }

    @Test
    public void unknownRequirementsAndUnknownDangerCannotBeOffsetByRewards()
    {
        Requirement unknown = requirement("synthetic.unobserved", 1);
        Candidate missing = new Candidate(candidateMethod("missing", List.of(unknown), List.of(), LOW), attractive());
        Candidate danger = new Candidate(candidateMethod("danger", List.of(), List.of(), MethodDefinition.Danger.UNKNOWN), attractive());
        Candidate valid = candidate("valid", 0.1);
        RankingResult result = rank(missing, valid, danger);
        assertEquals(valid, result.getBest().orElseThrow().getCandidate());
        assertEquals(List.of(danger, missing), candidates(result.getExcludedCandidates()));
        for (RankedCandidate excluded : result.getExcludedCandidates())
        {
            assertEquals(MethodEvaluator.Status.UNKNOWN, excluded.getEvaluation().getStatus());
            assertFalse(excluded.getScore().isPresent());
        }
        assertEquals(List.of(unknown), result.getExcludedCandidates().get(1).getEvaluation().getUnknownRequirements());
    }

    @Test
    public void knownPreparationCanWinWithoutBecomingAvailable()
    {
        Requirement prep = requirement("synthetic.prep", 1);
        Candidate preparable = new Candidate(candidateMethod("prep", List.of(), List.of(prep), LOW), scoreInputs(0.9));
        FactLookup snapshot = fact -> "synthetic.prep".equals(fact)
            ? Observation.verified(0.0, "synthetic prep", NOW) : facts.get(fact);
        RankingResult result = ranker.rank(List.of(candidate("ready", 0.5), preparable), snapshot, NOW);
        RankedCandidate winner = result.getBest().orElseThrow();
        assertEquals(preparable, winner.getCandidate());
        assertEquals(NEEDS_PREP, winner.getEvaluation().getStatus());
        assertEquals(List.of(prep), winner.getEvaluation().getMissingPreparation());
        assertTrue(winner.getEvaluation().getUnknownRequirements().isEmpty());
        assertEquals(0.9, winner.getScore().orElseThrow().getTotal(), 0);
    }

    @Test
    public void lowerTransitionCostBeatsSlightlyHigherEfficiency()
    {
        Candidate steady = candidate("steady", 0.8);
        Candidate quick = candidate("quick", 0.9);
        assertWinner(quick, steady, quick);
        assertWinner(steady, steady, withInput(quick, TRANSITION_COST, 0.2));
    }

    @Test
    public void storageUnlockValueChangesWinner()
    {
        Candidate efficient = candidate("efficient", 0.9);
        Candidate storage = candidate("storage", 0.4);
        assertWinner(efficient, storage, efficient);
        Candidate valuedStorage = withInput(storage, STORAGE_UNLOCK_VALUE, 0.3);
        assertWinner(valuedStorage, valuedStorage, efficient);
    }

    @Test
    public void riskInputAndDangerFloorCanChangeWinner()
    {
        Candidate steady = candidate("steady", 0.5);
        Candidate quick = candidate("quick", 0.9);
        assertWinner(quick, steady, quick);
        assertWinner(steady, steady, withInput(quick, RISK, 0.2));
        Candidate caution = new Candidate(candidateMethod("caution", List.of(), List.of(), CAUTION), scoreInputs(0.9));
        assertWinner(steady, steady, caution);
        assertEquals(-2.0, rank(caution).getBest().orElseThrow().getScore().orElseThrow().getContributions().get(RISK), 0);
    }

    @Test
    public void exactTiesUseMethodIdWithoutAnImplicitAvailableBonus()
    {
        Candidate ready = candidate("z_ready", 0.5);
        Candidate prep = new Candidate(candidateMethod("a_prep", List.of(),
            List.of(requirement("inventory.occupied_slots", 1)), LOW), scoreInputs(0.5));
        FactLookup snapshot = fact -> "inventory.occupied_slots".equals(fact)
            ? Observation.verified(0.0, "synthetic inventory", NOW) : facts.get(fact);
        RankingResult result = ranker.rank(List.of(ready, prep), snapshot, NOW);
        assertEquals(List.of(prep, ready), candidates(result.getRankedCandidates()));
        assertEquals(NEEDS_PREP, result.getBest().orElseThrow().getEvaluation().getStatus());
    }

    @Test
    public void everyInputPermutationProducesTheSameCompleteResult()
    {
        Candidate blocked = new Candidate(candidateMethod("blocked", List.of(requirement("inventory.free_slots", 29)), List.of(), LOW), attractive());
        Candidate unknown = new Candidate(candidateMethod("unknown", List.of(requirement("synthetic.missing", 1)), List.of(), LOW), attractive());
        List<Candidate> inputs = new ArrayList<>(List.of(candidate("b", 0.5), unknown, candidate("a", 0.5), blocked));
        assertPermutations(inputs, 0, ranker.rank(inputs, facts, NOW));
    }

    @Test
    public void noEligibleCandidatesMeansNoWinnerOrAlternativesIncludingEmptyInput()
    {
        Candidate blocked = new Candidate(candidateMethod("blocked", List.of(requirement("inventory.free_slots", 29)), List.of(), LOW), Map.of());
        Candidate unknown = new Candidate(candidateMethod("unknown", List.of(requirement("synthetic.missing", 1)), List.of(), LOW), Map.of());
        for (List<Candidate> inputs : List.of(List.<Candidate>of(), List.of(blocked), List.of(unknown), List.of(unknown, blocked)))
        {
            RankingResult result = ranker.rank(inputs, facts, NOW);
            assertFalse(result.getBest().isPresent());
            assertTrue(result.getAlternatives().isEmpty());
            assertTrue(result.getRankedCandidates().isEmpty());
            assertEquals(inputs.size(), result.getExcludedCandidates().size());
        }
    }

    @Test
    public void resultsRetainImmutableInputsEvaluationTimeAndSignedContributions()
    {
        Map<MethodScorer.Factor, Double> inputs = scoreInputs(0.8);
        inputs.put(STORAGE_UNLOCK_VALUE, 0.4);
        inputs.put(TRANSITION_COST, 0.2);
        Candidate candidate = new Candidate(candidateMethod("explained", List.of(), List.of(), LOW), inputs);
        List<Candidate> candidates = new ArrayList<>(List.of(candidate));
        RankingResult result = ranker.rank(candidates, facts, NOW);
        candidates.clear();
        inputs.clear();
        RankedCandidate winner = result.getBest().orElseThrow();
        assertEquals(NOW, result.getEvaluatedAt());
        assertSame(candidate.getMethod(), winner.getEvaluation().getMethod());
        assertEquals(AVAILABLE, winner.getEvaluation().getStatus());
        MethodScorer.Score score = winner.getScore().orElseThrow();
        assertEquals(1.6, score.getTotal(), 0.000001);
        assertEquals(1.2, score.getContributions().get(STORAGE_UNLOCK_VALUE), 0.000001);
        assertEquals(-0.4, score.getContributions().get(TRANSITION_COST), 0);
        assertEquals(MethodScorer.Factor.values().length, score.getContributions().size());
        assertThrows(UnsupportedOperationException.class, () -> winner.getCandidate().getInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> score.getContributions().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getRankedCandidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getAlternatives().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getExcludedCandidates().clear());
    }

    @Test
    public void duplicateIdsAndInvalidEligibleInputsFailRatherThanInventingAResult()
    {
        Candidate candidate = candidate("same", 0.5);
        assertThrows(IllegalArgumentException.class, () -> rank(candidate, candidate("same", 0.9)));
        assertThrows(IllegalArgumentException.class, () -> rank(new Candidate(candidate.getMethod(), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> rank(withInput(candidate, RISK, Double.NaN)));
    }

    @Test
    public void negativeScoreCanStillBeTheBestEligibleCandidate()
    {
        Candidate best = withInput(candidate("best", 0.5), TRANSITION_COST, 0.5);
        assertWinner(best, withInput(candidate("worse", 0.5), TRANSITION_COST, 1), best);
    }

    private void assertPermutations(List<Candidate> inputs, int index, RankingResult expected)
    {
        if (index == inputs.size())
        {
            assertEquals(expected, ranker.rank(inputs, facts, NOW));
            return;
        }
        for (int i = index; i < inputs.size(); i++)
        {
            Collections.swap(inputs, index, i);
            assertPermutations(inputs, index + 1, expected);
            Collections.swap(inputs, index, i);
        }
    }

    private Candidate candidate(String id, double efficiency)
    {
        return new Candidate(candidateMethod(id, List.of(), List.of(), LOW), scoreInputs(efficiency));
    }

    private Candidate withInput(Candidate candidate, MethodScorer.Factor factor, double value)
    {
        Map<MethodScorer.Factor, Double> inputs = new java.util.EnumMap<>(candidate.getInputs());
        inputs.put(factor, value);
        return new Candidate(candidate.getMethod(), inputs);
    }

    private Map<MethodScorer.Factor, Double> attractive()
    {
        Map<MethodScorer.Factor, Double> inputs = scoreInputs(1);
        inputs.put(GOAL_PROGRESS, 1.0);
        inputs.put(STORAGE_UNLOCK_VALUE, 1.0);
        inputs.put(CURRENT_INVENTORY_FIT, 1.0);
        return inputs;
    }

    private RankingResult rank(Candidate... candidates)
    {
        return ranker.rank(List.of(candidates), facts, NOW);
    }

    private void assertWinner(Candidate expected, Candidate... candidates)
    {
        assertEquals(expected, rank(candidates).getBest().orElseThrow().getCandidate());
    }

    private List<Candidate> candidates(List<RankedCandidate> ranked)
    {
        return ranked.stream().map(RankedCandidate::getCandidate).collect(Collectors.toList());
    }
}
