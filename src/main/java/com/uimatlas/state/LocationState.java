package com.uimatlas.state;

import java.util.Set;
import lombok.Value;

/** Scene coordinates belong to worldViewId; instances are not overworld destinations. */
@Value
public class LocationState
{
    int world;
    Set<String> worldTypes;
    int x;
    int y;
    int plane;
    int regionId;
    int worldViewId;
    boolean instanced;

    public LocationState(int world, Set<String> worldTypes, int x, int y, int plane,
        int regionId, int worldViewId, boolean instanced)
    {
        this.world = world;
        this.worldTypes = Set.copyOf(worldTypes);
        this.x = x;
        this.y = y;
        this.plane = plane;
        this.regionId = regionId;
        this.worldViewId = worldViewId;
        this.instanced = instanced;
    }
}
