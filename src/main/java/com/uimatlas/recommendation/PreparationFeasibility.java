package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Classifies whether known setup deltas have a trusted resolver; it never invents one. */
public final class PreparationFeasibility
{
    public enum Status { READY, FEASIBLE_PREP, UNRESOLVED_PREP, BLOCKED }

    @Value
    public static class Deficit
    {
        Requirement requirement;
        Observation<Double> observation;
        double shortfall;
    }

    @Value
    public static class Result
    {
        Status status;
        List<Deficit> deficits;
        List<Requirement> unresolvedRequirements;
        List<Requirement> blockers;
        Map<String, Observation<Boolean>> preparationSupport;

        private Result(Status status, List<Deficit> deficits, List<Requirement> unresolved,
            List<Requirement> blockers, Map<String, Observation<Boolean>> preparationSupport)
        {
            this.status = status;
            this.deficits = List.copyOf(deficits);
            this.unresolvedRequirements = List.copyOf(unresolved);
            this.blockers = List.copyOf(blockers);
            this.preparationSupport = Map.copyOf(preparationSupport);
        }
    }

    public Result assess(MethodEfficiency.Result efficiency, FactLookup facts, Instant now)
    {
        return assess(efficiency, facts, now, Map.of());
    }

    /**
     * support identifies deltas handled by an explicit preparation provider. An empty map
     * is the conservative milestone default: knowing a shortage does not prove how to resolve it.
     */
    public Result assess(MethodEfficiency.Result efficiency, FactLookup facts, Instant now,
        Map<String, Observation<Boolean>> support)
    {
        MethodEvaluator.Evaluation evaluation = efficiency.getEvaluation();
        if (evaluation.getStatus() == MethodEvaluator.Status.BLOCKED)
        {
            return new Result(Status.BLOCKED, List.of(), List.of(), evaluation.getBlockers(), support);
        }

        Map<String, Deficit> deficits = new LinkedHashMap<>();
        evaluation.getMissingPreparation().stream().sorted(Comparator.comparing(Requirement::getFact))
            .forEach(requirement -> addDeficit(deficits, requirement, facts.get(requirement.getFact())));
        efficiency.getWorkingCapacity().stream()
            .filter(check -> check.getResult() == Requirement.Result.MISSING)
            .forEach(check -> addDeficit(deficits, check.getRequirement(), check.getObservation()));

        Map<String, Requirement> unresolved = new LinkedHashMap<>();
        evaluation.getUnknownRequirements().stream().sorted(Comparator.comparing(Requirement::getFact))
            .forEach(requirement -> unresolved.putIfAbsent(requirement.getFact(), requirement));
        efficiency.getWorkingCapacity().stream()
            .filter(check -> check.getResult() == Requirement.Result.UNKNOWN)
            .forEach(check -> unresolved.putIfAbsent(check.getRequirement().getFact(), check.getRequirement()));

        List<Deficit> orderedDeficits = new ArrayList<>(deficits.values());
        orderedDeficits.sort(Comparator.comparing(deficit -> deficit.getRequirement().getFact()));
        List<Requirement> orderedUnresolved = new ArrayList<>(unresolved.values());
        orderedUnresolved.sort(Comparator.comparing(Requirement::getFact));
        boolean ready = evaluation.getStatus() == MethodEvaluator.Status.AVAILABLE
            && orderedDeficits.isEmpty() && orderedUnresolved.isEmpty();
        boolean feasible = evaluation.getStatus() == MethodEvaluator.Status.NEEDS_PREP
            && orderedUnresolved.isEmpty() && !orderedDeficits.isEmpty()
            && orderedDeficits.stream().allMatch(deficit -> trustedSupport(
                support.get(deficit.getRequirement().getFact()), deficit.getRequirement(), now));
        return new Result(ready ? Status.READY : feasible ? Status.FEASIBLE_PREP : Status.UNRESOLVED_PREP,
            orderedDeficits, orderedUnresolved, List.of(), support);
    }

    private static void addDeficit(Map<String, Deficit> deficits, Requirement requirement,
        Observation<Double> observation)
    {
        if (observation == null || !observation.isKnown())
        {
            return;
        }
        double actual = observation.getValue();
        double shortfall = requirement.getComparison() == Requirement.Comparison.AT_LEAST
            ? requirement.getTarget() - actual
            : requirement.getComparison() == Requirement.Comparison.AT_MOST
                ? actual - requirement.getTarget() : Math.abs(requirement.getTarget() - actual);
        deficits.putIfAbsent(requirement.getFact(), new Deficit(requirement, observation, Math.max(0, shortfall)));
    }

    private static boolean trustedSupport(Observation<Boolean> support, Requirement requirement, Instant now)
    {
        return support != null && support.isKnown() && support.getValue()
            && support.getConfidence() == Observation.Confidence.VERIFIED_NOW
            && !support.getObservedAt().isAfter(now)
            && Duration.between(support.getObservedAt(), now)
                .compareTo(Duration.ofSeconds(requirement.getMaxAgeSeconds())) <= 0;
    }
}
