package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Hard gates dominate unknowns, which dominate prep. Every unresolved fact stays in diagnostics. */
public final class MethodEvaluator
{
    public enum Status { AVAILABLE, NEEDS_PREP, BLOCKED, UNKNOWN }

    @Value
    public static class Evaluation
    {
        MethodDefinition method;
        Status status;
        List<Requirement> missingPreparation;
        List<Requirement> blockers;
        List<Requirement> unknownRequirements;

        private Evaluation(MethodDefinition method, Status status, List<Requirement> prep,
            List<Requirement> blockers, List<Requirement> unknown)
        {
            this.method = method;
            this.status = status;
            this.missingPreparation = List.copyOf(prep);
            this.blockers = List.copyOf(blockers);
            this.unknownRequirements = List.copyOf(unknown);
        }
    }

    public Evaluation evaluate(MethodDefinition method, Map<String, Observation<Double>> facts, Instant now)
    {
        List<Requirement> blockers = new ArrayList<>();
        List<Requirement> prep = new ArrayList<>();
        List<Requirement> unknown = new ArrayList<>();
        check(method.getHardRequirements(), facts, now, blockers, unknown);
        check(method.getPreparation(), facts, now, prep, unknown);
        check(List.of(method.getFreeInventorySlots()), facts, now, prep, unknown);
        check(method.getSetupItems(), facts, now, prep, unknown);
        check(method.getConsumes(), facts, now, prep, unknown);
        Status status = !blockers.isEmpty() ? Status.BLOCKED
            : !unknown.isEmpty() || method.getDanger() == MethodDefinition.Danger.UNKNOWN ? Status.UNKNOWN
            : !prep.isEmpty() ? Status.NEEDS_PREP : Status.AVAILABLE;
        return new Evaluation(method, status, prep, blockers, unknown);
    }

    private void check(List<Requirement> requirements, Map<String, Observation<Double>> facts, Instant now,
        List<Requirement> missing, List<Requirement> unknown)
    {
        for (Requirement requirement : requirements)
        {
            Requirement.Result result = requirement.evaluate(facts.get(requirement.getFact()), now);
            if (result == Requirement.Result.UNKNOWN)
            {
                unknown.add(requirement);
            }
            else if (result == Requirement.Result.MISSING)
            {
                missing.add(requirement);
            }
        }
    }
}
