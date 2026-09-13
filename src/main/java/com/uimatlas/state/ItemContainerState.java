package com.uimatlas.state;

import java.util.Map;
import lombok.Value;

/** Occupied slots only; duplicate item IDs in different slots remain distinct. */
@Value
public class ItemContainerState
{
    Map<Integer, ItemStack> slots;

    public ItemContainerState(Map<Integer, ItemStack> slots)
    {
        slots.forEach((slot, item) ->
        {
            if (slot < 0 || item.getItemId() < 0 || item.getQuantity() <= 0)
            {
                throw new IllegalArgumentException("Expected an occupied item slot");
            }
        });
        this.slots = Map.copyOf(slots);
    }

    public int occupiedSlots()
    {
        return slots.size();
    }
}
