package com.uimatlas.recommendation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/** Stateless selection over a coherent fact snapshot and explicit account-specific scoring inputs. */
public final class MethodRanker
{
    @Value
    public static class Candidate
    {
        MethodDefinition method;
        Map<MethodScorer.Factor, Double> inputs;

        public Candidate(MethodDefinition method, Map<MethodScorer.Factor, Double> inputs)
        {
            this.method = Objects.requireNonNull(method);
            if (method.getId() == null || method.getId().isBlank())
            {
                throw new IllegalArgumentException("Candidate requires a stable method ID");
            }
            this.inputs = Map.copyOf(inputs);
        }
    }

    /** Excluded candidates retain the evaluation but have no score. */
    @Value
    public static class RankedCandidate
    {
        Candidate candidate;
        MethodEvaluator.Evaluation evaluation;
        Optional<MethodScorer.Score> score;

        private RankedCandidate(Candidate candidate, MethodEvaluator.Evaluation evaluation,
            Optional<MethodScorer.Score> score)
        {
            this.candidate = candidate;
            this.evaluation = evaluation;
            this.score = score;
        }
    }

    @Value
    public static class RankingResult
    {
        Instant evaluatedAt;
        /** Eligible candidates only, best first. */
        List<RankedCandidate> rankedCandidates;
        /** Diagnostics only, ordered by method ID; these are never alternatives. */
        List<RankedCandidate> excludedCandidates;

        private RankingResult(Instant evaluatedAt, List<RankedCandidate> ranked, List<RankedCandidate> excluded)
        {
            this.evaluatedAt = evaluatedAt;
            this.rankedCandidates = List.copyOf(ranked);
            this.excludedCandidates = List.copyOf(excluded);
        }

        public Optional<RankedCandidate> getBest()
        {
            return rankedCandidates.stream().findFirst();
        }

        public List<RankedCandidate> getAlternatives()
        {
            return rankedCandidates.subList(Math.min(1, rankedCandidates.size()), rankedCandidates.size());
        }
    }

    private final MethodEvaluator evaluator;
    private final MethodScorer scorer;

    public MethodRanker()
    {
        this(new MethodEvaluator(), new MethodScorer());
    }

    public MethodRanker(MethodEvaluator evaluator, MethodScorer scorer)
    {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.scorer = Objects.requireNonNull(scorer);
    }

    /**
     * AVAILABLE and NEEDS_PREP compete by descending score, then ascending String method ID.
     * No status bonus, epsilon ties or minimum score: even negative eligible scores can win.
     * Duplicate IDs are rejected rather than resolved using input order. All lookups and inputs
     * must describe the same snapshot; now is explicit and must be at or after its cutoff.
     */
    public RankingResult rank(Collection<Candidate> candidates, FactLookup facts, Instant now)
    {
        Objects.requireNonNull(facts);
        Objects.requireNonNull(now);
        List<Candidate> ordered = new ArrayList<>(candidates);
        Set<String> ids = new HashSet<>();
        for (Candidate candidate : ordered)
        {
            if (!ids.add(candidate.getMethod().getId()))
            {
                throw new IllegalArgumentException("Duplicate candidate method ID: " + candidate.getMethod().getId());
            }
        }
        ordered.sort(Comparator.comparing(candidate -> candidate.getMethod().getId()));
        List<RankedCandidate> ranked = new ArrayList<>();
        List<RankedCandidate> excluded = new ArrayList<>();
        for (Candidate candidate : ordered)
        {
            MethodEvaluator.Evaluation evaluation = evaluator.evaluate(candidate.getMethod(), facts, now);
            // The scorer owns eligibility and does not calculate scores for BLOCKED or UNKNOWN.
            Optional<MethodScorer.Score> score = scorer.score(evaluation, candidate.getInputs());
            RankedCandidate result = new RankedCandidate(candidate, evaluation, score);
            (score.isPresent() ? ranked : excluded).add(result);
        }
        ranked.sort(Comparator.comparingDouble((RankedCandidate candidate) -> candidate.getScore().orElseThrow().getTotal())
            .reversed().thenComparing(candidate -> candidate.getCandidate().getMethod().getId()));
        return new RankingResult(now, ranked, excluded);
    }
}
