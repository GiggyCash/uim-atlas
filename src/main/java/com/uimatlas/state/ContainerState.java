package com.uimatlas.state;

import java.util.Map;
import lombok.Value;

/** Generic, independently trusted observations for one storage container. */
@Value
public class ContainerState
{
    Observation<Boolean> owned;
    Observation<Map<Integer, Integer>> contents;
    Observation<Integer> freeCapacity;

    public ContainerState(Observation<Boolean> owned, Observation<Map<Integer, Integer>> contents,
        Observation<Integer> freeCapacity)
    {
        this.owned = owned == null ? Observation.unknown() : owned;
        this.contents = contents == null ? Observation.unknown() : contents;
        this.freeCapacity = freeCapacity == null ? Observation.unknown() : freeCapacity;
        if (this.contents.isKnown())
        {
            this.contents.getValue().forEach((itemId, quantity) ->
            {
                if (itemId == null || itemId < 0 || quantity == null || quantity < 0)
                {
                    throw new IllegalArgumentException("Container contents require non-negative item IDs and quantities");
                }
            });
        }
        if (this.freeCapacity.isKnown() && this.freeCapacity.getValue() < 0)
        {
            throw new IllegalArgumentException("Container free capacity cannot be negative");
        }
    }

    public static ContainerState unknown()
    {
        return new ContainerState(Observation.unknown(), Observation.unknown(), Observation.unknown());
    }
}
