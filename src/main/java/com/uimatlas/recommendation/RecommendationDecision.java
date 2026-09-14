package com.uimatlas.recommendation;

import com.uimatlas.state.AccountState;
import com.uimatlas.state.AccountStateFacts;
import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/** Generic end-to-end domain composition; presentation and live selection remain outside it. */
public final class RecommendationDecision
{
    @Value
    public static class Candidate
    {
        MethodDefinition method;
        Map<MethodScorer.Factor, Double> explicitFactors;
        Map<String, Observation<Boolean>> preparationSupport;

        public Candidate(MethodDefinition method, Map<MethodScorer.Factor, Double> explicitFactors)
        {
            this(method, explicitFactors, Map.of());
        }

        public Candidate(MethodDefinition method, Map<MethodScorer.Factor, Double> explicitFactors,
            Map<String, Observation<Boolean>> preparationSupport)
        {
            this.method = Objects.requireNonNull(method);
            if (method.getId() == null || method.getId().isBlank())
            {
                throw new IllegalArgumentException("Candidate requires a stable method ID");
            }
            this.explicitFactors = Map.copyOf(explicitFactors);
            this.preparationSupport = Map.copyOf(preparationSupport);
        }
    }

    @Value
    public static class CandidateResult
    {
        MethodDefinition method;
        MethodEvaluator.Evaluation evaluation;
        SetupScoringInputs.Result setup;
        MethodEfficiency.Result efficiency;
        Optional<ResourceFlow.Analysis> resourceFlow;
        Optional<MethodScorer.Score> score;
        PreparationFeasibility.Result preparation;
        MethodActionability.Result actionability;
    }

    @Value
    public static class Result
    {
        Instant evaluatedAt;
        Optional<CandidateResult> bestActionable;
        /** Scored candidates first by score, then unscored diagnostics by method ID. */
        List<CandidateResult> candidates;
        MethodRanker.RankingResult ranking;

        private Result(Instant evaluatedAt, Optional<CandidateResult> bestActionable,
            List<CandidateResult> candidates, MethodRanker.RankingResult ranking)
        {
            this.evaluatedAt = evaluatedAt;
            this.bestActionable = bestActionable;
            this.candidates = List.copyOf(candidates);
            this.ranking = ranking;
        }
    }

    public Result decide(AccountState state, Collection<Candidate> candidates, Instant now)
    {
        return decide(new AccountStateFacts(state, now), candidates, now);
    }

    public Result decide(FactLookup facts, Collection<Candidate> candidates, Instant now)
    {
        Objects.requireNonNull(facts);
        Objects.requireNonNull(now);
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(candidate -> candidate.getMethod().getId()));
        Set<String> ids = new HashSet<>();
        Map<String, CandidateResult> results = new HashMap<>();
        List<MethodRanker.Candidate> scorable = new ArrayList<>();
        PreparationFeasibility preparation = new PreparationFeasibility();
        MethodActionability actionability = new MethodActionability();
        for (Candidate candidate : ordered)
        {
            String id = candidate.getMethod().getId();
            if (!ids.add(id))
            {
                throw new IllegalArgumentException("Duplicate candidate method ID: " + id);
            }
            MethodEfficiency.Result efficiency = new MethodEfficiency().derive(candidate.getMethod(), facts, now);
            SetupScoringInputs.Result setup = new SetupScoringInputs().derive(candidate.getMethod(), facts, now);
            Optional<ResourceFlow.Analysis> flow = candidate.getMethod().getResourceFlow()
                .map(resource -> resource.analyze(facts, now));
            PreparationFeasibility.Result prep = preparation.assess(efficiency, flow, facts, now,
                candidate.getPreparationSupport());
            MethodActionability.Result action = actionability.decide(efficiency.getEvaluation(), prep);
            Optional<Map<MethodScorer.Factor, Double>> inputs = efficiency
                .withExplicitFactors(candidate.getExplicitFactors()).flatMap(setup::withExplicitFactors);
            inputs.ifPresent(value -> scorable.add(new MethodRanker.Candidate(candidate.getMethod(), value)));
            results.put(id, new CandidateResult(candidate.getMethod(), efficiency.getEvaluation(), setup,
                efficiency, flow, Optional.empty(), prep, action));
        }

        MethodRanker.RankingResult ranking = new MethodRanker().rank(scorable, facts, now);
        ranking.getRankedCandidates().forEach(ranked ->
        {
            String id = ranked.getCandidate().getMethod().getId();
            CandidateResult prior = results.get(id);
            results.put(id, new CandidateResult(prior.getMethod(), ranked.getEvaluation(), prior.getSetup(),
                prior.getEfficiency(), prior.getResourceFlow(), ranked.getScore(), prior.getPreparation(),
                prior.getActionability()));
        });
        List<CandidateResult> all = new ArrayList<>(results.values());
        all.sort(Comparator.comparingDouble((CandidateResult result) ->
            result.getScore().map(MethodScorer.Score::getTotal).orElse(Double.NEGATIVE_INFINITY))
            .reversed().thenComparing(result -> result.getMethod().getId()));
        Optional<CandidateResult> best = all.stream()
            .filter(result -> result.getScore().isPresent() && result.getActionability().isActionable()).findFirst();
        return new Result(now, best, all, ranking);
    }
}
