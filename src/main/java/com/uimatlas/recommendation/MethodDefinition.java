package com.uimatlas.recommendation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.Value;

/** Immutable metadata, constructed by the validated resource loader. No game/client objects. */
@Value
public class MethodDefinition
{
    public enum DataKind { SYNTHETIC_TEST_ONLY, PRODUCTION }

    public enum Danger
    {
        LOW(0), CAUTION(0.5), HIGH(1), UNKNOWN(1);

        private final double riskFloor;

        Danger(double riskFloor)
        {
            this.riskFloor = riskFloor;
        }

        public double getRiskFloor()
        {
            return riskFloor;
        }
    }

    String id;
    String displayName;
    String category;
    String activity;
    Start start;
    List<Requirement> hardRequirements;
    List<Requirement> preparation;
    Requirement freeInventorySlots;
    List<Requirement> setupItems;
    List<Requirement> consumes;
    List<ResourceAmount> produces;
    List<Requirement> stopConditions;
    Style style;
    /** Legacy synthetic benchmark only; production rates belong to efficiency profiles. */
    XpRate xpRate;
    Costs costs;
    Danger danger;
    String reason;
    DataKind dataKind;
    List<Requirement> optionalSetup;
    List<Source> sources;
    List<Requirement> workingCapacity;
    List<EfficiencyProfile> efficiencyProfiles;
    Optional<ResourceFlow> resourceFlow;

    public MethodDefinition(String id, String displayName, String category, String activity, Start start,
        List<Requirement> hardRequirements, List<Requirement> preparation, Requirement freeInventorySlots,
        List<Requirement> setupItems, List<Requirement> consumes, List<ResourceAmount> produces,
        List<Requirement> stopConditions, Style style, XpRate xpRate, Costs costs, Danger danger, String reason)
    {
        this(id, displayName, category, activity, start, hardRequirements, preparation, freeInventorySlots,
            setupItems, consumes, produces, stopConditions, style, xpRate, costs, danger, reason,
            DataKind.SYNTHETIC_TEST_ONLY, List.of(), List.of());
    }

    public MethodDefinition(String id, String displayName, String category, String activity, Start start,
        List<Requirement> hardRequirements, List<Requirement> preparation, Requirement freeInventorySlots,
        List<Requirement> setupItems, List<Requirement> consumes, List<ResourceAmount> produces,
        List<Requirement> stopConditions, Style style, XpRate xpRate, Costs costs, Danger danger, String reason,
        DataKind dataKind, List<Requirement> optionalSetup, List<Source> sources)
    {
        this(id, displayName, category, activity, start, hardRequirements, preparation, freeInventorySlots,
            setupItems, consumes, produces, stopConditions, style, xpRate, costs, danger, reason,
            dataKind, optionalSetup, sources, List.of(), List.of());
    }

    public MethodDefinition(String id, String displayName, String category, String activity, Start start,
        List<Requirement> hardRequirements, List<Requirement> preparation, Requirement freeInventorySlots,
        List<Requirement> setupItems, List<Requirement> consumes, List<ResourceAmount> produces,
        List<Requirement> stopConditions, Style style, XpRate xpRate, Costs costs, Danger danger, String reason,
        DataKind dataKind, List<Requirement> optionalSetup, List<Source> sources,
        List<Requirement> workingCapacity, List<EfficiencyProfile> efficiencyProfiles)
    {
        this(id, displayName, category, activity, start, hardRequirements, preparation, freeInventorySlots,
            setupItems, consumes, produces, stopConditions, style, xpRate, costs, danger, reason,
            dataKind, optionalSetup, sources, workingCapacity, efficiencyProfiles, Optional.empty());
    }

    public MethodDefinition(String id, String displayName, String category, String activity, Start start,
        List<Requirement> hardRequirements, List<Requirement> preparation, Requirement freeInventorySlots,
        List<Requirement> setupItems, List<Requirement> consumes, List<ResourceAmount> produces,
        List<Requirement> stopConditions, Style style, XpRate xpRate, Costs costs, Danger danger, String reason,
        DataKind dataKind, List<Requirement> optionalSetup, List<Source> sources,
        List<Requirement> workingCapacity, List<EfficiencyProfile> efficiencyProfiles,
        Optional<ResourceFlow> resourceFlow)
    {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.activity = activity;
        this.start = start;
        this.hardRequirements = List.copyOf(hardRequirements);
        this.preparation = List.copyOf(preparation);
        this.freeInventorySlots = freeInventorySlots;
        this.setupItems = List.copyOf(setupItems);
        this.consumes = List.copyOf(consumes);
        this.produces = List.copyOf(produces);
        this.stopConditions = List.copyOf(stopConditions);
        this.style = style;
        this.xpRate = xpRate;
        this.costs = costs;
        this.danger = danger;
        this.reason = reason;
        this.dataKind = dataKind;
        this.optionalSetup = List.copyOf(optionalSetup);
        this.sources = List.copyOf(sources);
        this.workingCapacity = List.copyOf(workingCapacity);
        this.efficiencyProfiles = List.copyOf(efficiencyProfiles);
        this.resourceFlow = resourceFlow;
    }

    @Value
    public static class EfficiencyProfile
    {
        String id;
        int priority;
        List<Requirement> requirements;
        double efficiency;
        Optional<XpRate> xpRate;
        String notes;

        public EfficiencyProfile(String id, int priority, List<Requirement> requirements,
            double efficiency, Optional<XpRate> xpRate, String notes)
        {
            this.id = id;
            this.priority = priority;
            this.requirements = List.copyOf(requirements);
            this.efficiency = efficiency;
            this.xpRate = xpRate;
            this.notes = notes;
        }
    }

    @Value
    public static class Source
    {
        String url;
        LocalDate reviewedAt;
        String notes;
    }

    @Value
    public static class Start
    {
        String location;
        String contact;
        String instruction;
    }

    @Value
    public static class Style
    {
        double attention;
        String playStyle;
        boolean tickManipulation;
    }

    @Value
    public static class XpRate
    {
        double minimum;
        double maximum;
        String assumptions;
    }

    @Value
    public static class ResourceAmount
    {
        String resourceId;
        double quantity;
        String basis;
    }

    @Value
    public static class Costs
    {
        double storageUnlockValue;
        double setupMinutes;
        double transitionMinutes;
        double inventoryDisruption;
        String assumptions;
    }
}
