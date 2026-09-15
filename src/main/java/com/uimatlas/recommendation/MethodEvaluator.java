package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Hard gates dominate unknowns, which dominate prep. Every unresolved fact stays in diagnostics. */
public final class MethodEvaluator
{
    public enum Status { AVAILABLE, NEEDS_PREP, BLOCKED, UNKNOWN }
    public enum GroupResult { SATISFIED, MISSING, UNKNOWN }

    @Value
    public static class AlternativeCheck
    {
        MethodDefinition.RequirementGroup.Alternative alternative;
        GroupResult result;
        List<Requirement> missingRequirements;
        List<Requirement> unknownRequirements;
    }

    @Value
    public static class GroupCheck
    {
        MethodDefinition.RequirementGroup group;
        GroupResult result;
        List<AlternativeCheck> alternatives;
    }

    @Value
    public static class Evaluation
    {
        MethodDefinition method;
        Status status;
        List<Requirement> missingPreparation;
        List<Requirement> blockers;
        List<Requirement> unknownRequirements;
        List<GroupCheck> preparationAnyOf;

        private Evaluation(MethodDefinition method, Status status, List<Requirement> prep,
            List<Requirement> blockers, List<Requirement> unknown, List<GroupCheck> preparationAnyOf)
        {
            this.method = method;
            this.status = status;
            this.missingPreparation = List.copyOf(prep);
            this.blockers = List.copyOf(blockers);
            this.unknownRequirements = List.copyOf(unknown);
            this.preparationAnyOf = List.copyOf(preparationAnyOf);
        }
    }

    public Evaluation evaluate(MethodDefinition method, Map<String, Observation<Double>> facts, Instant now)
    {
        return evaluate(method, fact -> facts.getOrDefault(fact, Observation.unknown()), now);
    }

    public Evaluation evaluate(MethodDefinition method, FactLookup facts, Instant now)
    {
        List<Requirement> blockers = new ArrayList<>();
        List<Requirement> prep = new ArrayList<>();
        List<Requirement> unknown = new ArrayList<>();
        check(method.getHardRequirements(), facts, now, blockers, unknown);
        check(method.getPreparation(), facts, now, prep, unknown);
        check(List.of(method.getFreeInventorySlots()), facts, now, prep, unknown);
        check(method.getSetupItems(), facts, now, prep, unknown);
        check(method.getConsumes(), facts, now, prep, unknown);
        List<GroupCheck> groupChecks = new ArrayList<>();
        method.getPreparationAnyOf().stream().sorted(Comparator.comparing(MethodDefinition.RequirementGroup::getId))
            .forEach(group -> groupChecks.add(check(group, facts, now)));
        boolean unknownGroup = groupChecks.stream().anyMatch(check -> check.getResult() == GroupResult.UNKNOWN);
        boolean missingGroup = groupChecks.stream().anyMatch(check -> check.getResult() == GroupResult.MISSING);
        Status status = !blockers.isEmpty() ? Status.BLOCKED
            : !unknown.isEmpty() || unknownGroup || method.getDanger() == MethodDefinition.Danger.UNKNOWN ? Status.UNKNOWN
            : !prep.isEmpty() || missingGroup ? Status.NEEDS_PREP : Status.AVAILABLE;
        return new Evaluation(method, status, prep, blockers, unknown, groupChecks);
    }

    private GroupCheck check(MethodDefinition.RequirementGroup group, FactLookup facts, Instant now)
    {
        List<AlternativeCheck> alternatives = new ArrayList<>();
        group.getAlternatives().stream()
            .sorted(Comparator.comparing(MethodDefinition.RequirementGroup.Alternative::getId))
            .forEach(alternative ->
            {
                List<Requirement> missing = new ArrayList<>();
                List<Requirement> unknown = new ArrayList<>();
                List<Requirement> requirements = new ArrayList<>(alternative.getRequirements());
                requirements.sort(Comparator.comparing(Requirement::getFact));
                check(requirements, facts, now, missing, unknown);
                GroupResult result = missing.isEmpty() && unknown.isEmpty() ? GroupResult.SATISFIED
                    : !missing.isEmpty() ? GroupResult.MISSING : GroupResult.UNKNOWN;
                alternatives.add(new AlternativeCheck(alternative, result, List.copyOf(missing), List.copyOf(unknown)));
            });
        GroupResult result = alternatives.stream().anyMatch(a -> a.getResult() == GroupResult.SATISFIED)
            ? GroupResult.SATISFIED
            : alternatives.stream().anyMatch(a -> a.getResult() == GroupResult.UNKNOWN)
                ? GroupResult.UNKNOWN : GroupResult.MISSING;
        return new GroupCheck(group, result, List.copyOf(alternatives));
    }

    private void check(List<Requirement> requirements, FactLookup facts, Instant now,
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
