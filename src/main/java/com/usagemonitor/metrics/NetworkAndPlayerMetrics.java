/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NetworkAndPlayerMetrics {

    public static class PlayerEntry {
        private final String name;
        private final int ping;

        public PlayerEntry(String name, int ping) {
            this.name = name;
            this.ping = ping;
        }

        public String getName() { return name; }
        public int getPing() { return ping; }
    }

    private final int onlinePlayerCount;
    private final int maxPlayers;
    private final List<PlayerEntry> playerList;

    public NetworkAndPlayerMetrics(int onlinePlayerCount, int maxPlayers, List<PlayerEntry> playerList) {
        this.onlinePlayerCount = onlinePlayerCount;
        this.maxPlayers = maxPlayers;
        this.playerList = playerList;
    }

    public static NetworkAndPlayerMetrics collect() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "NetworkAndPlayerMetrics.collect() must run on the main server thread. Current: "
                + Thread.currentThread().getName());
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int max = Bukkit.getMaxPlayers();
        List<PlayerEntry> list = new ArrayList<>();
        for (Player p : onlinePlayers) {
            list.add(new PlayerEntry(p.getName(), extractPing(p)));
        }
        return new NetworkAndPlayerMetrics(onlinePlayers.size(), max, list);
    }

    private static int extractPing(Player player) {
        if (player == null) return 0;
        try {
            Method getPing = player.getClass().getMethod("getPing");
            Object val = getPing.invoke(player);
            if (val instanceof Integer) return (Integer) val;
        } catch (Throwable ignored) {
        }
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandle.invoke(player);
            if (entityPlayer != null) {
                Field pingField = entityPlayer.getClass().getField("ping");
                return pingField.getInt(entityPlayer);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public int getOnlinePlayerCount() { return onlinePlayerCount; }
    public int getMaxPlayers() { return maxPlayers; }
    public List<PlayerEntry> getPlayerList() { return playerList; }
}
