package com.uimatlas.recommendation;

import com.uimatlas.recommendation.MethodRanker.Candidate;
import com.uimatlas.recommendation.MethodRanker.RankingResult;
import com.uimatlas.state.AccountMode;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import com.uimatlas.state.ItemContainerState;
import com.uimatlas.state.ItemStack;
import com.uimatlas.state.Observation;
import com.uimatlas.state.SkillState;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.uimatlas.recommendation.MethodEvaluator.Status.*;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;
import static com.uimatlas.recommendation.SyntheticMethods.*;
import static org.junit.Assert.*;

/** Pure-domain scenarios: all activities, skill keys and item uses are invented test data. */
public class AccountStateRankingTest
{
    @Test
    public void currentSetupWinsUntilAccountInventoryChanges()
    {
        MethodDefinition a = method("a", "inventory.item.1001.quantity");
        MethodDefinition b = method("b", "inventory.item.1002.quantity");
        AccountState initial = AccountState.builder().loggedIn(true)
            .accountMode(Observation.verified(AccountMode.ULTIMATE_IRONMAN, "synthetic mode", NOW))
            .skills(Observation.map(Map.of("SYNTHETIC", new SkillState(5, 5, 100)), "synthetic skills", NOW))
            .inventory(Observation.verified(new ItemContainerState(Map.of(0, new ItemStack(1002, 1))),
                "synthetic inventory", NOW)).build();
        FactLookup initialFacts = new AccountStateFacts(initial, NOW);
        List<Candidate> initialCandidates = candidates(a, b, initialFacts);
        MethodRanker ranker = new MethodRanker(new MethodEvaluator(), new MethodScorer());
        RankingResult before = ranker.rank(initialCandidates, initialFacts, NOW);
        assertSame(b, before.getBest().orElseThrow().getEvaluation().getMethod());
        assertEquals(AVAILABLE, before.getBest().orElseThrow().getEvaluation().getStatus());
        assertEquals(2.8, before.getBest().orElseThrow().getScore().orElseThrow().getTotal(), 0.000001);
        assertEquals(NEEDS_PREP, before.getAlternatives().get(0).getEvaluation().getStatus());
        assertEquals(a.getPreparation(), before.getAlternatives().get(0).getEvaluation().getMissingPreparation());
        assertEquals(-2.0, before.getAlternatives().get(0).getScore().orElseThrow().getContributions().get(TRANSITION_COST), 0);

        AccountState changed = initial.toBuilder().inventory(Observation.verified(new ItemContainerState(Map.of(
            0, new ItemStack(1002, 1), 1, new ItemStack(1001, 1))), "synthetic inventory", NOW)).build();
        FactLookup changedFacts = new AccountStateFacts(changed, NOW);
        RankingResult after = ranker.rank(candidates(a, b, changedFacts), changedFacts, NOW);
        assertSame(a, after.getBest().orElseThrow().getEvaluation().getMethod());
        assertEquals(AVAILABLE, after.getBest().orElseThrow().getEvaluation().getStatus());
        assertEquals(2.9, after.getBest().orElseThrow().getScore().orElseThrow().getTotal(), 0.000001);
        assertTrue(after.getBest().orElseThrow().getEvaluation().getMissingPreparation().isEmpty());
        assertEquals(0.0, after.getBest().orElseThrow().getScore().orElseThrow().getContributions().get(TRANSITION_COST), 0);
        assertEquals(before, ranker.rank(initialCandidates, initialFacts, NOW));

        // Old favorable scores cannot rescue newly unknown or stale required state.
        FactLookup missingInventory = new AccountStateFacts(changed.toBuilder().inventory(Observation.unknown()).build(), NOW);
        RankingResult unknown = ranker.rank(initialCandidates, missingInventory, NOW);
        assertFalse(unknown.getBest().isPresent());
        assertEquals(2, unknown.getExcludedCandidates().size());
        unknown.getExcludedCandidates().forEach(candidate ->
        {
            assertEquals(UNKNOWN, candidate.getEvaluation().getStatus());
            assertFalse(candidate.getScore().isPresent());
        });
        assertFalse(ranker.rank(initialCandidates, initialFacts, NOW.plusSeconds(61)).getBest().isPresent());
    }

    private MethodDefinition method(String id, String setupFact)
    {
        return candidateMethod(id, List.of(requirement("skill.synthetic.level", 5)),
            List.of(requirement(setupFact, 1)), MethodDefinition.Danger.LOW);
    }

    private List<Candidate> candidates(MethodDefinition a, MethodDefinition b, FactLookup facts)
    {
        return List.of(candidate(a, 0.9, facts), candidate(b, 0.8, facts));
    }

    private Candidate candidate(MethodDefinition method, double efficiency, FactLookup facts)
    {
        // Test-only model: one known setup item eliminates the invented transition cost.
        // This is not a production normalization policy or advice about either item ID.
        Requirement setup = method.getPreparation().get(0);
        Requirement.Result result = setup.evaluate(facts.get(setup.getFact()), NOW);
        assertNotEquals(Requirement.Result.UNKNOWN, result);
        boolean ready = result == Requirement.Result.SATISFIED;
        Map<MethodScorer.Factor, Double> inputs = scoreInputs(efficiency);
        inputs.put(CURRENT_INVENTORY_FIT, ready ? 1.0 : 0.0);
        inputs.put(TRANSITION_COST, ready ? 0.0 : 1.0);
        return new Candidate(method, inputs);
    }
}
