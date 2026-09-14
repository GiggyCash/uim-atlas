package com.uimatlas.state;

import com.uimatlas.recommendation.FactLookup;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable projection of normalized account state; never reads the client or refreshes observations. */
public final class AccountStateFacts implements FactLookup
{
    private static final int INVENTORY_CAPACITY = 28;
    private static final Pattern ITEM_FACT = Pattern.compile(
        "(inventory|equipment|carried)\\.item\\.(0|[1-9][0-9]*)\\.(quantity|usable_slots|occupied_slots)");
    private static final Pattern CAPABILITY_FACT = Pattern.compile(
        "capability\\.[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+");
    private static final Pattern CONTAINER_ID = Pattern.compile("[a-z][a-z0-9_]*");

    private final Map<String, Observation<Double>> facts;
    private final Observation<ItemContainerState> inventory;
    private final Observation<ItemContainerState> equipment;

    /** Use the evaluation time as the cutoff; evaluate this snapshot at that time or later. */
    public AccountStateFacts(AccountState state, Instant asOf)
    {
        Objects.requireNonNull(asOf);
        inventory = observedBy(state.getInventory(), asOf);
        equipment = observedBy(state.getEquipment(), asOf);
        Map<String, Observation<Double>> projected = new HashMap<>();
        Observation<Map<String, SkillState>> skills = observedBy(state.getSkills(), asOf);
        if (skills.isKnown())
        {
            skills.getValue().forEach((id, skill) ->
            {
                String prefix = "skill." + id.toLowerCase(Locale.ROOT);
                projected.put(prefix + ".level", derived(skill.getLevel(), skills));
                projected.put(prefix + ".xp", derived(skill.getExperience(), skills));
            });
        }
        Observation<Map<String, Boolean>> capabilities = observedBy(state.getCapabilities(), asOf);
        if (capabilities.isKnown())
        {
            capabilities.getValue().forEach((id, available) ->
            {
                if (CAPABILITY_FACT.matcher(id).matches())
                {
                    projected.put(id, derived(available ? 1 : 0, capabilities));
                }
            });
        }
        state.getContainers().forEach((id, container) ->
        {
            if (!CONTAINER_ID.matcher(id).matches())
            {
                return;
            }
            String prefix = "container." + id;
            Observation<Boolean> owned = observedBy(container.getOwned(), asOf);
            if (owned.isKnown())
            {
                projected.put(prefix + ".owned", derived(owned.getValue() ? 1 : 0, owned));
            }
            Observation<Map<Integer, Integer>> contents = observedBy(container.getContents(), asOf);
            if (contents.isKnown())
            {
                contents.getValue().forEach((itemId, quantity) -> projected.put(
                    prefix + ".contents." + itemId + ".quantity", derived(quantity, contents)));
            }
            Observation<Integer> freeCapacity = observedBy(container.getFreeCapacity(), asOf);
            if (freeCapacity.isKnown())
            {
                projected.put(prefix + ".free_capacity", derived(freeCapacity.getValue(), freeCapacity));
            }
        });
        // A malformed normalized inventory cannot establish how much usable space remains.
        if (validInventorySlots())
        {
            int occupied = inventory.getValue().occupiedSlots();
            projected.put("inventory.occupied_slots", derived(occupied, inventory));
            projected.put("inventory.free_slots", derived(INVENTORY_CAPACITY - occupied, inventory));
        }
        facts = Map.copyOf(projected);
    }

    @Override
    public Observation<Double> get(String factId)
    {
        Observation<Double> direct = facts.get(factId);
        if (direct != null)
        {
            return direct;
        }
        Matcher match = ITEM_FACT.matcher(factId);
        if (!match.matches())
        {
            return Observation.unknown();
        }
        int itemId;
        try
        {
            itemId = Integer.parseInt(match.group(2));
        }
        catch (NumberFormatException ex)
        {
            return Observation.unknown();
        }
        String scope = match.group(1);
        if (match.group(3).equals("usable_slots"))
        {
            Observation<Double> free = facts.get("inventory.free_slots");
            if (!scope.equals("inventory") || free == null)
            {
                return Observation.unknown();
            }
            long occupiedByItem = inventory.getValue().getSlots().values().stream()
                .filter(item -> item.getItemId() == itemId).count();
            return derived(free.getValue() + occupiedByItem, inventory);
        }
        if (match.group(3).equals("occupied_slots"))
        {
            if (!scope.equals("inventory") || !validInventorySlots())
            {
                return Observation.unknown();
            }
            long occupiedByItem = inventory.getValue().getSlots().values().stream()
                .filter(item -> item.getItemId() == itemId).count();
            return derived(occupiedByItem, inventory);
        }
        if (scope.equals("carried"))
        {
            return carried(itemId);
        }
        return quantity(scope.equals("inventory") ? inventory : equipment, itemId);
    }

    private Observation<Double> carried(int itemId)
    {
        Observation<Double> inInventory = quantity(inventory, itemId);
        Observation<Double> equipped = quantity(equipment, itemId);
        if (!inInventory.isKnown() || !equipped.isKnown())
        {
            return Observation.unknown();
        }
        Instant oldest = inInventory.getObservedAt().isBefore(equipped.getObservedAt())
            ? inInventory.getObservedAt() : equipped.getObservedAt();
        String source = "carried sum [inventory: " + inInventory.getSource()
            + "; equipment: " + equipped.getSource() + "]";
        Observation<Double> result = Observation.verified(inInventory.getValue() + equipped.getValue(), source, oldest);
        return inInventory.getConfidence() == Observation.Confidence.VERIFIED_NOW
            && equipped.getConfidence() == Observation.Confidence.VERIFIED_NOW ? result : result.lastObserved();
    }

    private boolean validInventorySlots()
    {
        return inventory.isKnown() && inventory.getValue().getSlots().keySet().stream()
            .allMatch(slot -> slot < INVENTORY_CAPACITY);
    }

    private static Observation<Double> quantity(Observation<ItemContainerState> container, int itemId)
    {
        if (!container.isKnown())
        {
            return Observation.unknown();
        }
        long total = container.getValue().getSlots().values().stream()
            .filter(item -> item.getItemId() == itemId).mapToLong(ItemStack::getQuantity).sum();
        return derived(total, container);
    }

    private static Observation<Double> derived(double value, Observation<?> input)
    {
        if (!input.isKnown() || value < 0)
        {
            return Observation.unknown();
        }
        Observation<Double> result = Observation.verified(value, input.getSource(), input.getObservedAt());
        return input.getConfidence() == Observation.Confidence.VERIFIED_NOW ? result : result.lastObserved();
    }

    private static <T> Observation<T> observedBy(Observation<T> input, Instant asOf)
    {
        // Taking the oldest carried timestamp must not hide a future-dated constituent.
        return input.isKnown() && input.getObservedAt().isAfter(asOf) ? Observation.unknown() : input;
    }
}
