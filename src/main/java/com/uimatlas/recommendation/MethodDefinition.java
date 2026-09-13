package com.uimatlas.recommendation;

import java.util.List;
import lombok.Value;

/** Immutable metadata, constructed by the validated resource loader. No game/client objects. */
@Value
public class MethodDefinition
{
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
    XpRate xpRate;
    Costs costs;
    Danger danger;
    String reason;

    public MethodDefinition(String id, String displayName, String category, String activity, Start start,
        List<Requirement> hardRequirements, List<Requirement> preparation, Requirement freeInventorySlots,
        List<Requirement> setupItems, List<Requirement> consumes, List<ResourceAmount> produces,
        List<Requirement> stopConditions, Style style, XpRate xpRate, Costs costs, Danger danger, String reason)
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
