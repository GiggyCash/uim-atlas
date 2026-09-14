package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.Value;
import static com.uimatlas.recommendation.MethodScorer.Factor.*;

/** Pure snapshot derivation of four setup factors. See DATA_SCHEMA.md for units and limits. */
public final class SetupScoringInputs
{
    private static final double INVENTORY_CAPACITY = 28;
    private static final double FULL_BURDEN = 4;
    private static final double FULL_COST_MINUTES = 30;

    @Value
    public static class Result
    {
        /** All four factors, or absent when any necessary observation cannot be used. */
        Optional<Map<MethodScorer.Factor, Double>> inputs;
        List<Requirement> unresolvedRequirements;
        /** Original observations, including rejected stale/invalid observations, for diagnosis. */
        Map<String, Observation<Double>> observations;

        private Result(Map<MethodScorer.Factor, Double> inputs, List<Requirement> unresolved,
            Map<String, Observation<Double>> observations)
        {
            this.inputs = inputs == null ? Optional.empty() : Optional.of(Map.copyOf(inputs));
            this.unresolvedRequirements = List.copyOf(unresolved);
            this.observations = Map.copyOf(observations);
        }

        /** Supply exactly the other five factors; uncertainty keeps its optional-estimate meaning. */
        public Optional<Map<MethodScorer.Factor, Double>> withExplicitFactors(
            Map<MethodScorer.Factor, Double> explicit)
        {
            if (!explicit.keySet().equals(java.util.Set.of(GOAL_PROGRESS, STORAGE_UNLOCK_VALUE,
                METHOD_EFFICIENCY, RISK, UNCERTAINTY)))
            {
                throw new IllegalArgumentException("Supply exactly the five non-setup scoring factors");
            }
            explicit.forEach((factor, value) ->
            {
                if (value == null || !Double.isFinite(value) || value < 0 || value > 1)
                {
                    throw new IllegalArgumentException("Expected normalized [0,1] input for " + factor);
                }
            });
            return inputs.map(derived ->
            {
                Map<MethodScorer.Factor, Double> complete = new EnumMap<>(MethodScorer.Factor.class);
                complete.putAll(explicit);
                complete.putAll(derived);
                return Map.copyOf(complete);
            });
        }
    }

    /**
     * facts supplies the current carried setup in the definition's declared scopes. It must be
     * the same immutable snapshot used for evaluation/ranking at now. No client or clock reads.
     * Unknown required setup is not an optional estimate penalty and produces no score inputs.
     */
    public Result derive(MethodDefinition method, FactLookup facts, Instant now)
    {
        List<Requirement> items = new ArrayList<>(method.getSetupItems());
        items.addAll(method.getConsumes());
        items.sort(Comparator.comparing(Requirement::getFact));
        List<Requirement> requirements = new ArrayList<>(items);
        requirements.addAll(method.getPreparation());
        requirements.add(method.getFreeInventorySlots());
        requirements.sort(Comparator.comparing(Requirement::getFact));
        Map<String, Observation<Double>> observations = new TreeMap<>();
        List<Requirement> unresolved = new ArrayList<>();
        for (Requirement requirement : requirements)
        {
            Observation<Double> observation = observations.computeIfAbsent(requirement.getFact(), fact ->
            {
                Observation<Double> value = facts.get(fact);
                return value == null ? Observation.unknown() : value;
            });
            // Reuse the predicate's confidence, age and numeric checks, including known absence.
            if (requirement.evaluate(observation, now) == Requirement.Result.UNKNOWN)
            {
                unresolved.add(requirement);
            }
        }
        Requirement slots = method.getFreeInventorySlots();
        Observation<Double> free = observations.get(slots.getFact());
        if (slots.getComparison() != Requirement.Comparison.AT_LEAST
            || !slots.getFact().equals("inventory.free_slots")
            || slots.getTarget() > INVENTORY_CAPACITY || slots.getTarget() != Math.rint(slots.getTarget()))
        {
            throw new IllegalArgumentException("Expected an inventory free-slot minimum in [0,28]");
        }
        if (free.isKnown() && (free.getValue() > INVENTORY_CAPACITY || free.getValue() != Math.rint(free.getValue()))
            && !unresolved.contains(slots))
        {
            unresolved.add(slots);
            unresolved.sort(Comparator.comparing(Requirement::getFact));
        }
        for (Requirement item : items)
        {
            if (item.getComparison() != Requirement.Comparison.AT_LEAST || item.getTarget() <= 0
                || item.getTarget() != Math.rint(item.getTarget()))
            {
                throw new IllegalArgumentException("Expected positive integer setup/batch quantities");
            }
        }
        if (!unresolved.isEmpty())
        {
            return new Result(null, unresolved, observations);
        }

        double deficits = 0;
        for (Requirement item : items)
        {
            deficits += Math.max(0, 1 - observations.get(item.getFact()).getValue() / item.getTarget());
        }
        long missingPrep = method.getPreparation().stream()
            .filter(requirement -> requirement.evaluate(observations.get(requirement.getFact()), now)
                == Requirement.Result.MISSING).count();
        double burden = deficits + missingPrep;
        double slotShortfall = Math.max(0, slots.getTarget() - free.getValue()) / INVENTORY_CAPACITY;
        double occupancy = (INVENTORY_CAPACITY - free.getValue()) / INVENTORY_CAPACITY;
        MethodDefinition.Costs costs = method.getCosts();
        // Minutes describe a setup change, so do not charge them again when no change is needed.
        double setup = clamp(burden / FULL_BURDEN
            + (burden > 0 ? costs.getSetupMinutes() / FULL_COST_MINUTES : 0));
        double disruption = clamp(slotShortfall + costs.getInventoryDisruption() * occupancy);
        double transition = clamp(setup + disruption
            + (burden > 0 || slotShortfall > 0 ? costs.getTransitionMinutes() / FULL_COST_MINUTES : 0));
        double coverage = items.isEmpty() ? 1 : 1 - deficits / items.size();
        Map<MethodScorer.Factor, Double> inputs = new EnumMap<>(MethodScorer.Factor.class);
        inputs.put(CURRENT_INVENTORY_FIT, clamp(coverage * (1 - slotShortfall)));
        inputs.put(SETUP_COST, setup);
        inputs.put(TRANSITION_COST, transition);
        inputs.put(INVENTORY_DISRUPTION, disruption);
        return new Result(inputs, List.of(), observations);
    }

    private static double clamp(double value)
    {
        return Math.min(1, Math.max(0, value));
    }
}
