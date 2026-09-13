package com.uimatlas.state;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
import net.runelite.api.gameval.VarbitID;

/** All client reads happen here, on the client thread, after server updates (GameTick). */
@Slf4j
@Singleton
public class RuneLiteAccountObserver
{
    private final Client client;
    private final AccountStateService states;
    private boolean skillsDirty = true;
    private boolean inventoryDirty = true;
    private boolean equipmentDirty = true;
    private boolean questsDirty = true;
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
        skillsDirty = inventoryDirty = equipmentDirty = questsDirty = true;
    }

    public void skillsChanged()
    {
        skillsDirty = true;
    }

    public void containerChanged(int id)
    {
        inventoryDirty |= id == InventoryID.INV;
        equipmentDirty |= id == InventoryID.WORN;
    }

    public void questsChanged()
    {
        questsDirty = true;
    }

    public void refresh()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            reset();
            return;
        }
        long currentHash = client.getAccountHash();
        if (currentHash == -1)
        {
            reset();
            return;
        }
        if (currentHash != accountHash)
        {
            reset();
            accountHash = currentHash;
        }

        Instant now = Instant.now();
        AccountState previous = states.getSnapshot();
        AccountState.AccountStateBuilder next = previous.toBuilder().loggedIn(true);
        AccountMode mode = AccountMode.fromId(client.getVarbitValue(VarbitID.IRONMAN));
        next.accountMode(mode == AccountMode.UNKNOWN ? Observation.unknown()
            : Observation.verified(mode, "RuneLite: account-mode varbit", now));

        if (skillsDirty)
        {
            Observation<Map<String, SkillState>> skills = readSkills(now);
            next.skills(skills);
            skillsDirty = !skills.isKnown();
        }
        if (inventoryDirty)
        {
            Observation<ItemContainerState> inventory = readContainer(InventoryID.INV, now);
            next.inventory(inventory);
            inventoryDirty = !inventory.isKnown();
        }
        if (equipmentDirty)
        {
            Observation<ItemContainerState> equipment = readContainer(InventoryID.WORN, now);
            next.equipment(equipment);
            equipmentDirty = !equipment.isKnown();
        }
        if (questsDirty)
        {
            // Quest.getState runs a read-only client script; never call it inside a script event.
            questsDirty = false;
            next.quests(readQuests(now));
        }
        next.location(readLocation(now));
        states.publish(next.build());
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
