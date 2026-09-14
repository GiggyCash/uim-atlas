package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/** A bounded, consume-then-produce inventory transformation for one declared batch. */
@Value
public class ResourceFlow
{
    public enum SlotSemantics { ONE_SLOT_PER_UNIT, ONE_SHARED_STACK, NO_INVENTORY_SLOT }
    public enum Status { READY, MISSING_INPUTS, INSUFFICIENT_CAPACITY, UNKNOWN }

    @Value
    public static class Entry
    {
        String quantityFact;
        int quantity;
        SlotSemantics slotSemantics;
        long maxAgeSeconds;

        public Entry(String quantityFact, int quantity, SlotSemantics slotSemantics, long maxAgeSeconds)
        {
            boolean inventory = quantityFact != null
                && quantityFact.matches("inventory\\.item\\.(0|[1-9][0-9]*)\\.quantity");
            boolean external = quantityFact != null
                && quantityFact.matches("container\\.[a-z][a-z0-9_]*\\.contents\\.(0|[1-9][0-9]*)\\.quantity");
            if ((!inventory && !external) || inventory == (slotSemantics == SlotSemantics.NO_INVENTORY_SLOT)
                || quantity <= 0 || maxAgeSeconds < 0)
            {
                throw new IllegalArgumentException("Invalid inventory resource-flow entry");
            }
            this.quantityFact = quantityFact;
            this.quantity = quantity;
            this.slotSemantics = java.util.Objects.requireNonNull(slotSemantics);
            this.maxAgeSeconds = maxAgeSeconds;
        }

        public String occupiedSlotsFact()
        {
            if (slotSemantics == SlotSemantics.NO_INVENTORY_SLOT)
            {
                throw new IllegalStateException("External resource has no ordinary inventory position");
            }
            return quantityFact.substring(0, quantityFact.length() - ".quantity".length()) + ".occupied_slots";
        }
    }

    @Value
    public static class Check
    {
        String fact;
        Observation<Double> observation;
    }

    @Value
    public static class Analysis
    {
        Status status;
        List<Check> checks;
        List<Requirement> unresolvedRequirements;
        Optional<Integer> inputSlotsReleased;
        Optional<Integer> outputSlotsRequired;
        Optional<Integer> resultingFreeSlots;
        int additionalFreeSlotsNeeded;

        private Analysis(Status status, Map<String, Observation<Double>> observations,
            List<Requirement> unresolved, Integer released, Integer output, Integer resulting, int additional)
        {
            this.status = status;
            List<Check> ordered = new ArrayList<>();
            observations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.add(new Check(entry.getKey(), entry.getValue())));
            this.checks = List.copyOf(ordered);
            List<Requirement> orderedUnresolved = new ArrayList<>(unresolved);
            orderedUnresolved.sort(Comparator.comparing(Requirement::getFact));
            this.unresolvedRequirements = List.copyOf(orderedUnresolved);
            this.inputSlotsReleased = Optional.ofNullable(released);
            this.outputSlotsRequired = Optional.ofNullable(output);
            this.resultingFreeSlots = Optional.ofNullable(resulting);
            this.additionalFreeSlotsNeeded = additional;
        }
    }

    List<Entry> inputs;
    List<Entry> outputs;
    Requirement freeSlots;

    public ResourceFlow(List<Entry> inputs, List<Entry> outputs, Requirement freeSlots)
    {
        if (inputs.isEmpty() || outputs.isEmpty() || !freeSlots.getFact().equals("inventory.free_slots"))
        {
            throw new IllegalArgumentException("Resource flow requires inputs, outputs and inventory.free_slots");
        }
        Set<String> facts = new HashSet<>();
        for (Entry entry : inputs)
        {
            if (!facts.add(entry.getQuantityFact()))
            {
                throw new IllegalArgumentException("Duplicate resource-flow input: " + entry.getQuantityFact());
            }
        }
        for (Entry entry : outputs)
        {
            if (!facts.add(entry.getQuantityFact()))
            {
                throw new IllegalArgumentException("Overlapping or duplicate resource-flow item: " + entry.getQuantityFact());
            }
        }
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.freeSlots = freeSlots;
    }

    public Analysis analyze(FactLookup facts, Instant now)
    {
        Map<String, Observation<Double>> observed = new HashMap<>();
        List<Requirement> unresolved = new ArrayList<>();
        Map<Entry, Long> quantities = new HashMap<>();
        Map<Entry, Integer> positions = new HashMap<>();
        boolean missing = false;

        Observation<Double> freeObservation = observe(facts, observed, freeSlots.getFact());
        Long free = count(freeSlots, freeObservation, now, 28, unresolved);
        for (Entry entry : inputs)
        {
            Requirement quantity = current(entry.getQuantityFact(), entry.getQuantity(), entry.getMaxAgeSeconds(),
                "Current carried input quantity for the declared resource-flow batch.");
            Observation<Double> quantityObservation = observe(facts, observed, entry.getQuantityFact());
            Requirement.Result inputResult = quantity.evaluate(quantityObservation, now);
            if (inputResult == Requirement.Result.UNKNOWN)
            {
                unresolved.add(quantity);
            }
            else if (inputResult == Requirement.Result.MISSING)
            {
                missing = true;
            }
            Long actual = exactCount(quantity, quantityObservation, now, Integer.MAX_VALUE, unresolved);
            Integer occupied = occupied(entry, facts, now, observed, unresolved, actual);
            if (actual != null)
            {
                quantities.put(entry, actual);
            }
            if (occupied != null)
            {
                positions.put(entry, occupied);
            }
        }
        for (Entry entry : outputs)
        {
            Requirement quantity = current(entry.getQuantityFact(), 0, entry.getMaxAgeSeconds(),
                "Current output quantity used only to determine compatible occupied-slot behavior.");
            Observation<Double> quantityObservation = observe(facts, observed, entry.getQuantityFact());
            Long actual = exactCount(quantity, quantityObservation, now, Integer.MAX_VALUE, unresolved);
            Integer occupied = occupied(entry, facts, now, observed, unresolved, actual);
            if (actual != null)
            {
                quantities.put(entry, actual);
            }
            if (occupied != null)
            {
                positions.put(entry, occupied);
            }
        }
        if (!unresolved.isEmpty() || free == null)
        {
            return new Analysis(Status.UNKNOWN, observed, unresolved, null, null, null, 0);
        }
        if (missing)
        {
            return new Analysis(Status.MISSING_INPUTS, observed, List.of(), null, null, null, 0);
        }

        int released = 0;
        for (Entry entry : inputs)
        {
            released += entry.getSlotSemantics() == SlotSemantics.NO_INVENTORY_SLOT ? 0
                : entry.getSlotSemantics() == SlotSemantics.ONE_SLOT_PER_UNIT ? entry.getQuantity()
                : quantities.get(entry) == entry.getQuantity() ? positions.get(entry) : 0;
        }
        int output = 0;
        for (Entry entry : outputs)
        {
            output += entry.getSlotSemantics() == SlotSemantics.NO_INVENTORY_SLOT ? 0
                : entry.getSlotSemantics() == SlotSemantics.ONE_SLOT_PER_UNIT ? entry.getQuantity()
                : quantities.get(entry) > 0 ? 0 : 1;
        }
        int available = free.intValue() + released;
        int additional = Math.max(0, output - available);
        return new Analysis(additional == 0 ? Status.READY : Status.INSUFFICIENT_CAPACITY,
            observed, List.of(), released, output, additional == 0 ? available - output : null, additional);
    }

    private static Integer occupied(Entry entry, FactLookup facts, Instant now,
        Map<String, Observation<Double>> observed, List<Requirement> unresolved, Long quantity)
    {
        if (entry.getSlotSemantics() == SlotSemantics.NO_INVENTORY_SLOT)
        {
            return 0;
        }
        Requirement requirement = current(entry.occupiedSlotsFact(), 0, entry.getMaxAgeSeconds(),
            "Directly observed inventory positions occupied by this exact resource-flow item.");
        Long occupied = count(requirement, observe(facts, observed, requirement.getFact()), now, 28, unresolved);
        if (quantity == null || occupied == null)
        {
            return null;
        }
        boolean consistent = entry.getSlotSemantics() == SlotSemantics.ONE_SLOT_PER_UNIT
            ? occupied.longValue() == quantity.longValue() : quantity == 0 ? occupied == 0 : occupied == 1;
        if (!consistent)
        {
            unresolved.add(requirement);
            return null;
        }
        return occupied.intValue();
    }

    private static Long exactCount(Requirement requirement, Observation<Double> observation, Instant now,
        long maximum, List<Requirement> unresolved)
    {
        Long result = count(requirement, observation, now, maximum, unresolved);
        return result;
    }

    private static Long count(Requirement requirement, Observation<Double> observation, Instant now,
        long maximum, List<Requirement> unresolved)
    {
        if (requirement.evaluate(observation, now) == Requirement.Result.UNKNOWN
            || observation.getValue() != Math.rint(observation.getValue()) || observation.getValue() > maximum)
        {
            if (unresolved.stream().noneMatch(value -> value.getFact().equals(requirement.getFact())))
            {
                unresolved.add(requirement);
            }
            return null;
        }
        return observation.getValue().longValue();
    }

    private static Observation<Double> observe(FactLookup facts, Map<String, Observation<Double>> observed, String fact)
    {
        return observed.computeIfAbsent(fact, id ->
        {
            Observation<Double> value = facts.get(id);
            return value == null ? Observation.unknown() : value;
        });
    }

    private static Requirement current(String fact, double target, long maxAgeSeconds, String description)
    {
        return new Requirement(fact, Requirement.Comparison.AT_LEAST, target, description, false, maxAgeSeconds, false);
    }
}
