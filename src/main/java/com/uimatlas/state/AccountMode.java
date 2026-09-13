package com.uimatlas.state;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The small account-mode protocol, not a game-knowledge catalogue. */
@Getter
@RequiredArgsConstructor
public enum AccountMode
{
    NORMAL(0, "Normal"),
    IRONMAN(1, "Ironman"),
    ULTIMATE_IRONMAN(2, "Ultimate Ironman"),
    HARDCORE_IRONMAN(3, "Hardcore Ironman"),
    GROUP_IRONMAN(4, "Group Ironman"),
    HARDCORE_GROUP_IRONMAN(5, "Hardcore Group Ironman"),
    UNRANKED_GROUP_IRONMAN(6, "Unranked Group Ironman"),
    UNKNOWN(-1, "Unknown");

    private final int id;
    private final String displayName;

    public static AccountMode fromId(int id)
    {
        return Arrays.stream(values()).filter(mode -> mode.id == id).findFirst().orElse(UNKNOWN);
    }
}
