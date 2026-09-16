package com.uimatlas.integration;

import com.uimatlas.recommendation.MethodDefinition;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/** Optional fire-and-forget boundary for Shortest Path's public PluginMessage contract. */
@Singleton
public final class ShortestPathBridge
{
    static final String NAMESPACE = "shortestpath";
    static final String PATH_MESSAGE = "path";

    private final Client client;
    private final EventBus eventBus;

    @Inject
    public ShortestPathBridge(Client client, EventBus eventBus)
    {
        this.client = client;
        this.eventBus = eventBus;
    }

    /**
     * Posts one explicit request using the player's location at call time. A true result confirms only
     * that Atlas posted the request; Shortest Path has no supported acknowledgement protocol.
     */
    public boolean requestRoute(MethodDefinition.RouteTarget target)
    {
        if (target == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }
        Player player = client.getLocalPlayer();
        WorldPoint start = player == null ? null : player.getWorldLocation();
        if (start == null)
        {
            return false;
        }
        WorldPoint destination = new WorldPoint(target.getX(), target.getY(), target.getPlane());
        eventBus.post(new PluginMessage(NAMESPACE, PATH_MESSAGE,
            Map.of("start", start, "target", destination)));
        return true;
    }
}
