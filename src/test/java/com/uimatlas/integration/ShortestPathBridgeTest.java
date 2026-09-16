package com.uimatlas.integration;

import com.uimatlas.recommendation.MethodDefinition;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ShortestPathBridgeTest
{
    @Test
    public void explicitRequestPostsTheAuditedProtocolFromTheCurrentClickTimeLocation()
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        EventBus eventBus = mock(EventBus.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3201, 0),
            new WorldPoint(3210, 3211, 1));
        ShortestPathBridge bridge = new ShortestPathBridge(client, eventBus);
        MethodDefinition.RouteTarget target = new MethodDefinition.RouteTarget(1275, 3817, 0);

        verifyNoInteractions(eventBus);
        assertTrue(bridge.requestRoute(target));
        assertTrue(bridge.requestRoute(target));

        ArgumentCaptor<PluginMessage> messages = ArgumentCaptor.forClass(PluginMessage.class);
        verify(eventBus, times(2)).post(messages.capture());
        assertEquals("shortestpath", messages.getAllValues().get(0).getNamespace());
        assertEquals("path", messages.getAllValues().get(0).getName());
        assertEquals(new WorldPoint(3200, 3201, 0), messages.getAllValues().get(0).getData().get("start"));
        assertEquals(new WorldPoint(3210, 3211, 1), messages.getAllValues().get(1).getData().get("start"));
        assertEquals(new WorldPoint(1275, 3817, 0), messages.getAllValues().get(1).getData().get("target"));
        assertEquals(2, messages.getAllValues().get(1).getData().size());
    }

    @Test
    public void unusableClientNeverPostsARoute()
    {
        Client client = mock(Client.class);
        EventBus eventBus = mock(EventBus.class);
        Player player = mock(Player.class);
        ShortestPathBridge bridge = new ShortestPathBridge(client, eventBus);
        MethodDefinition.RouteTarget target = new MethodDefinition.RouteTarget(1275, 3817, 0);

        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertFalse(bridge.requestRoute(target));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(null, player);
        assertFalse(bridge.requestRoute(target));
        when(player.getWorldLocation()).thenReturn(null);
        assertFalse(bridge.requestRoute(target));
        assertFalse(bridge.requestRoute(null));

        verifyNoInteractions(eventBus);
    }
}
