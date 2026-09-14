package com.uimatlas.state;

import java.util.Map;
import java.util.OptionalInt;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Session-only snapshot. There are deliberately no RuneLite objects in this model. */
@Value
@Builder(toBuilder = true)
public class AccountState
{
    boolean loggedIn;
    @Builder.Default
    Observation<AccountMode> accountMode = Observation.unknown();
    @Builder.Default
    Observation<Map<String, SkillState>> skills = Observation.unknown();
    @Builder.Default
    Observation<Map<Integer, QuestStatus>> quests = Observation.unknown();
    @Builder.Default
    Observation<Integer> questPoints = Observation.unknown();
    @Builder.Default
    Observation<Map<String, Boolean>> capabilities = Observation.unknown();
    @Singular("container")
    Map<String, ContainerState> containers;
    @Builder.Default
    Observation<ItemContainerState> inventory = Observation.unknown();
    @Builder.Default
    Observation<ItemContainerState> equipment = Observation.unknown();
    @Builder.Default
    Observation<LocationState> location = Observation.unknown();

    public static AccountState empty()
    {
        return builder().build();
    }

    public boolean isUltimateIronman()
    {
        return accountMode.isKnown() && accountMode.getValue() == AccountMode.ULTIMATE_IRONMAN;
    }

    public boolean isLoaded()
    {
        return loggedIn && accountMode.isKnown() && skills.isKnown() && inventory.isKnown()
            && equipment.isKnown() && location.isKnown() && quests.isKnown()
            && !quests.getValue().containsValue(QuestStatus.UNKNOWN);
    }

    public OptionalInt totalLevel()
    {
        return skills.isKnown()
            ? OptionalInt.of(skills.getValue().values().stream().mapToInt(SkillState::getLevel).sum())
            : OptionalInt.empty();
    }
}
