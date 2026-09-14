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
        assertEquals(a.getSetupItems(), before.getAlternatives().get(0).getEvaluation().getMissingPreparation());
        assertEquals(-0.5, before.getAlternatives().get(0).getScore().orElseThrow().getContributions().get(TRANSITION_COST), 0);

        AccountState changed = initial.toBuilder().inventory(Observation.verified(new ItemContainerState(Map.of(
            0, new ItemStack(1001, 1))), "synthetic inventory", NOW)).build();
        FactLookup changedFacts = new AccountStateFacts(changed, NOW);
        RankingResult after = ranker.rank(candidates(a, b, changedFacts), changedFacts, NOW);
        assertSame(a, after.getBest().orElseThrow().getEvaluation().getMethod());
        assertEquals(AVAILABLE, after.getBest().orElseThrow().getEvaluation().getStatus());
        assertEquals(2.9, after.getBest().orElseThrow().getScore().orElseThrow().getTotal(), 0.000001);
        assertTrue(after.getBest().orElseThrow().getEvaluation().getMissingPreparation().isEmpty());
        assertEquals(NEEDS_PREP, after.getAlternatives().get(0).getEvaluation().getStatus());
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
        MethodDefinition base = candidateMethod(id, List.of(requirement("skill.synthetic.level", 5)),
            List.of(), MethodDefinition.Danger.LOW);
        return new MethodDefinition(base.getId(), base.getDisplayName(), base.getCategory(), base.getActivity(),
            base.getStart(), base.getHardRequirements(), base.getPreparation(), base.getFreeInventorySlots(),
            List.of(requirement(setupFact, 1)), base.getConsumes(), base.getProduces(), base.getStopConditions(),
            base.getStyle(), new MethodDefinition.XpRate(id.equals("a") ? 900 : 800, id.equals("a") ? 900 : 800,
                "Synthetic efficiency comparison only"), base.getCosts(), base.getDanger(), base.getReason());
    }

    private List<Candidate> candidates(MethodDefinition a, MethodDefinition b, FactLookup facts)
    {
        return List.of(candidate(a, facts), candidate(b, facts));
    }

    private Candidate candidate(MethodDefinition method, FactLookup facts)
    {
        // Efficiency normalization stays explicitly test-supplied; setup factors are derived.
        Map<MethodScorer.Factor, Double> inputs = new SetupScoringInputs().derive(method, facts, NOW)
            .withExplicitFactors(Map.of(GOAL_PROGRESS, 0.0, STORAGE_UNLOCK_VALUE, 0.0,
                METHOD_EFFICIENCY, method.getXpRate().getMaximum() / 1000, RISK, 0.0, UNCERTAINTY, 0.0))
            .orElseThrow();
        return new Candidate(method, inputs);
    }
}
