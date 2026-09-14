package com.uimatlas.data;

import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.Requirement;
import com.uimatlas.recommendation.ResourceFlow;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import static com.uimatlas.data.DefinitionJson.require;

/** Cohesive schema-v4 resource-flow parsing kept out of the catalog loader. */
final class ResourceFlowJson
{
    private ResourceFlowJson()
    {
    }

    static ResourceFlow parse(DefinitionJson method, Set<String> facts, Requirement freeSlots,
        Function<DefinitionJson, Requirement> inputParser,
        Function<DefinitionJson, MethodDefinition.ResourceAmount> outputParser)
    {
        require(!freeSlots.isAllowLastObserved(), method.at("freeInventorySlots")
            + ": resource flow requires current slot observation");
        List<ResourceFlow.Entry> inputs = method.list("consumes", value ->
        {
            Requirement requirement = inputParser.apply(value);
            require(!requirement.isAllowLastObserved(),
                value.at("fact") + ": resource-flow inputs require current exact quantities");
            ResourceFlow.SlotSemantics semantics = value.choice("slotSemantics", ResourceFlow.SlotSemantics.class);
            validatePositions(requirement.getFact(), (int) requirement.getTarget(), semantics, facts,
                value.at("slotSemantics"));
            return new ResourceFlow.Entry(requirement.getFact(), (int) requirement.getTarget(), semantics,
                requirement.getMaxAgeSeconds());
        });
        List<ResourceFlow.Entry> outputs = method.list("produces", value ->
        {
            MethodDefinition.ResourceAmount amount = outputParser.apply(value);
            ResourceFlow.SlotSemantics semantics = value.choice("slotSemantics", ResourceFlow.SlotSemantics.class);
            int quantity = (int) amount.getQuantity();
            validatePositions(amount.getResourceId(), quantity, semantics, facts, value.at("slotSemantics"));
            return new ResourceFlow.Entry(amount.getResourceId(), quantity, semantics, freeSlots.getMaxAgeSeconds());
        });
        return new ResourceFlow(inputs, outputs, freeSlots);
    }

    private static void validatePositions(String quantityFact, int quantity,
        ResourceFlow.SlotSemantics semantics, Set<String> facts, String path)
    {
        boolean inventory = quantityFact.matches("inventory\\.item\\.(0|[1-9][0-9]*)\\.quantity");
        boolean external = quantityFact.matches(
            "container\\.[a-z][a-z0-9_]*\\.contents\\.(0|[1-9][0-9]*)\\.quantity");
        require(inventory || external, path + ": unsupported resource-flow quantity fact");
        require(inventory != (semantics == ResourceFlow.SlotSemantics.NO_INVENTORY_SLOT),
            path + ": slot semantics do not match the resource scope");
        if (!inventory)
        {
            return;
        }
        String occupied = quantityFact.substring(0, quantityFact.length() - ".quantity".length())
            + ".occupied_slots";
        require(facts.contains(occupied), path + ": missing declared occupied-slot fact " + occupied);
        require(semantics != ResourceFlow.SlotSemantics.ONE_SLOT_PER_UNIT || quantity <= 28,
            path + ": per-unit slot quantity exceeds inventory capacity");
    }
}
