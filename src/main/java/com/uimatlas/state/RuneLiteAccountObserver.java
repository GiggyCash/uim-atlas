package com.uimatlas.state;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;

/** All client reads happen here, on the client thread, after server updates (GameTick). */
@Slf4j
@Singleton
public class RuneLiteAccountObserver
{
    private static final String POH_OWNED = "capability.poh.owned";
    private static final String PLANK_SACK = "plank_sack";
    private static final int PLANK_SACK_CAPACITY = 28;
    private static final Map<Integer, Integer> PLANK_SACK_CONTENTS = Map.of(
        ItemID.WOODPLANK, VarbitID.PLANK_SACK_PLAIN,
        ItemID.PLANK_OAK, VarbitID.PLANK_SACK_OAK,
        ItemID.PLANK_TEAK, VarbitID.PLANK_SACK_TEAK,
        ItemID.PLANK_MAHOGANY, VarbitID.PLANK_SACK_MAHOGANY,
        ItemID.PLANK_CAMPHOR, VarbitID.PLANK_SACK_CAMPHOR,
        ItemID.PLANK_IRONWOOD, VarbitID.PLANK_SACK_IRONWOOD,
        ItemID.PLANK_ROSEWOOD, VarbitID.PLANK_SACK_ROSEWOOD);

    private final Client client;
    private final AccountStateService states;
    private boolean skillsDirty = true;
    private boolean inventoryDirty = true;
    private boolean equipmentDirty = true;
    private boolean questsDirty = true;
    private boolean containersDirty = true;
    private long accountHash = -1;

    @Inject
    public RuneLiteAccountObserver(Client client, AccountStateService states)
    {
        this.client = client;
        this.states = states;
    }

    public void reset()
    {
        states.reset();
        accountHash = -1;
        skillsDirty = inventoryDirty = equipmentDirty = questsDirty = containersDirty = true;
    }

    public void skillsChanged()
    {
        skillsDirty = true;
    }

    public void containerChanged(int id)
    {
        inventoryDirty |= id == InventoryID.INV;
        equipmentDirty |= id == InventoryID.WORN;
        containersDirty |= id == InventoryID.INV;
    }

    public void varbitChanged(int id)
    {
        containersDirty |= PLANK_SACK_CONTENTS.containsValue(id);
    }

    public void questsChanged()
    {
        questsDirty = true;
    }

    /** Marks only the supported observation families whose planner freshness window is expiring. */
    public void factsChanged(Collection<String> facts)
    {
        for (String fact : facts)
        {
            skillsDirty |= fact.startsWith("skill.");
            inventoryDirty |= fact.startsWith("inventory.") || fact.startsWith("carried.")
                || fact.startsWith("container.");
            equipmentDirty |= fact.startsWith("equipment.") || fact.startsWith("carried.");
            questsDirty |= fact.startsWith("quest.") || fact.equals("account.quest_points");
            containersDirty |= fact.startsWith("container.");
        }
    }

    /** Returns true when facts used by planning were refreshed or their values changed. */
    public boolean refresh()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            boolean changed = states.getSnapshot().isLoggedIn();
            reset();
            return changed;
        }
        long currentHash = client.getAccountHash();
        if (currentHash == -1)
        {
            boolean changed = states.getSnapshot().isLoggedIn();
            reset();
            return changed;
        }
        boolean changed = false;
        if (currentHash != accountHash)
        {
            reset();
            accountHash = currentHash;
            changed = true;
        }

        Instant now = Instant.now();
        AccountState previous = states.getSnapshot();
        AccountState.AccountStateBuilder next = previous.toBuilder().loggedIn(true);
        Observation<ItemContainerState> inventory = previous.getInventory();
        AccountMode mode = AccountMode.fromId(client.getVarbitValue(VarbitID.IRONMAN));
        Observation<AccountMode> accountMode = mode == AccountMode.UNKNOWN ? Observation.unknown()
            : Observation.verified(mode, "RuneLite: account-mode varbit", now);
        Observation<Map<String, Boolean>> capabilities = readCapabilities(now);
        changed |= !sameValue(previous.getAccountMode(), accountMode)
            || !sameValue(previous.getCapabilities(), capabilities);
        next.accountMode(accountMode);
        next.capabilities(capabilities);

        if (skillsDirty)
        {
            Observation<Map<String, SkillState>> skills = readSkills(now);
            next.skills(skills);
            skillsDirty = !skills.isKnown();
            changed |= skills.isKnown() || !sameValue(previous.getSkills(), skills);
        }
        if (inventoryDirty)
        {
            inventory = readContainer(InventoryID.INV, now);
            next.inventory(inventory);
            inventoryDirty = !inventory.isKnown();
            containersDirty = true;
            changed |= inventory.isKnown() || !sameValue(previous.getInventory(), inventory);
        }
        if (equipmentDirty)
        {
            Observation<ItemContainerState> equipment = readContainer(InventoryID.WORN, now);
            next.equipment(equipment);
            equipmentDirty = !equipment.isKnown();
            changed |= equipment.isKnown() || !sameValue(previous.getEquipment(), equipment);
        }
        if (questsDirty)
        {
            // Quest.getState runs a read-only client script; never call it inside a script event.
            questsDirty = false;
            next.quests(readQuests(now));
            next.questPoints(readQuestPoints(now));
            changed = true;
        }
        if (containersDirty)
        {
            Map<String, ContainerState> containers = readContainers(inventory, now);
            next.containers(containers);
            ContainerState sack = containers.get(PLANK_SACK);
            containersDirty = sack.getOwned().isKnown() && !sack.getContents().isKnown();
            changed |= !Objects.equals(previous.getContainers(), containers);
        }
        next.location(readLocation(now));
        states.publish(next.build());
        return changed;
    }

    private boolean sameValue(Observation<?> left, Observation<?> right)
    {
        return left.isKnown() == right.isKnown() && left.getConfidence() == right.getConfidence()
            && Objects.equals(left.getValue(), right.getValue());
    }

    private Map<String, ContainerState> readContainers(Observation<ItemContainerState> inventory, Instant now)
    {
        if (!inventory.isKnown() || inventory.getValue().getSlots().values().stream()
            .noneMatch(item -> item.getItemId() == ItemID.PLANK_SACK))
        {
            // Inventory absence proves only "not currently carried", never global non-ownership.
            return Map.of(PLANK_SACK, ContainerState.unknown());
        }
        Observation<Boolean> owned = Observation.verified(true, inventory.getSource(), inventory.getObservedAt());
        try
        {
            Map<Integer, Integer> contents = new HashMap<>();
            int total = 0;
            for (Map.Entry<Integer, Integer> entry : PLANK_SACK_CONTENTS.entrySet())
            {
                int quantity = client.getServerVarbitValue(entry.getValue());
                if (quantity < 0 || quantity > PLANK_SACK_CAPACITY)
                {
                    return Map.of(PLANK_SACK, new ContainerState(owned,
                        Observation.unknown(), Observation.unknown()));
                }
                contents.put(entry.getKey(), quantity);
                total += quantity;
            }
            if (total > PLANK_SACK_CAPACITY)
            {
                return Map.of(PLANK_SACK, new ContainerState(owned,
                    Observation.unknown(), Observation.unknown()));
            }
            String source = "RuneLite: server plank-sack content varbits";
            return Map.of(PLANK_SACK, new ContainerState(owned,
                Observation.map(contents, source, now),
                Observation.verified(PLANK_SACK_CAPACITY - total, source, now)));
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to observe carried plank sack contents", ex);
            return Map.of(PLANK_SACK, new ContainerState(owned,
                Observation.unknown(), Observation.unknown()));
        }
    }

    private Observation<Map<String, Boolean>> readCapabilities(Instant now)
    {
        try
        {
            int houseLocation = client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION);
            if (houseLocation <= 0)
            {
                // RuneLite names the positive location signal, but does not document zero as verified non-ownership.
                return Observation.unknown();
            }
            return Observation.map(Map.of(POH_OWNED, true),
                "RuneLite: server varbit POH_HOUSE_LOCATION", now);
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to observe player-owned house location", ex);
            return Observation.unknown();
        }
    }

    // RuneLite still includes the deprecated aggregate in values(); it must not count as a skill.
    @SuppressWarnings("deprecation")
    private Observation<Map<String, SkillState>> readSkills(Instant now)
    {
        Map<String, SkillState> skills = new HashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            int level = client.getRealSkillLevel(skill);
            int xp = client.getSkillExperience(skill);
            if (level < 1 || xp < 0)
            {
                return Observation.unknown();
            }
            skills.put(skill.name(), new SkillState(level, client.getBoostedSkillLevel(skill), xp));
        }
        return Observation.map(skills, "RuneLite: skills", now);
    }

    private Observation<ItemContainerState> readContainer(int id, Instant now)
    {
        ItemContainer container = client.getItemContainer(id);
        if (container == null)
        {
            return Observation.unknown();
        }
        Map<Integer, ItemStack> slots = new HashMap<>();
        Item[] items = container.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            Item item = items[slot];
            if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
            {
                slots.put(slot, new ItemStack(item.getId(), item.getQuantity()));
            }
        }
        return Observation.verified(new ItemContainerState(slots), "RuneLite: container " + id, now);
    }

    private Observation<Map<Integer, QuestStatus>> readQuests(Instant now)
    {
        Map<Integer, QuestStatus> quests = new HashMap<>();
        for (Quest quest : Quest.values())
        {
            try
            {
                quests.put(quest.getId(), QuestStatus.valueOf(quest.getState(client).name()));
            }
            catch (RuntimeException ex)
            {
                // Preserve the unknown result and retry at the next quest-list refresh/login.
                quests.put(quest.getId(), QuestStatus.UNKNOWN);
                log.debug("Unable to observe quest {}", quest.getId(), ex);
            }
        }
        // No stable event identifies every quest-stage change. Never imply continuous verification.
        return Observation.map(quests, "RuneLite: Quest.getState", now).lastObserved();
    }

    private Observation<Integer> readQuestPoints(Instant now)
    {
        try
        {
            int points = client.getVarpValue(VarPlayerID.QP);
            return points < 0 ? Observation.unknown()
                : Observation.verified(points, "RuneLite: VarPlayerID.QP", now).lastObserved();
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to observe quest points", ex);
            return Observation.unknown();
        }
    }

    private Observation<LocationState> readLocation(Instant now)
    {
        Player player = client.getLocalPlayer();
        if (player == null || client.getWorld() <= 0)
        {
            return Observation.unknown();
        }
        WorldView view = player.getWorldView();
        WorldPoint point = player.getWorldLocation();
        if (view == null || point == null)
        {
            return Observation.unknown();
        }
        Set<String> worldTypes = client.getWorldType().stream().map(Enum::name).collect(Collectors.toSet());
        return Observation.verified(new LocationState(client.getWorld(), worldTypes,
            point.getX(), point.getY(), point.getPlane(), point.getRegionID(), view.getId(), view.isInstance()),
            "RuneLite: local player/world", now);
    }
}
