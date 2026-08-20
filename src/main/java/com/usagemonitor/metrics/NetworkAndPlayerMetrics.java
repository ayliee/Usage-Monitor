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

/**
 * Captures network & player activity metrics including individual latency (ping).
 */
public class NetworkAndPlayerMetrics {

    public static class PlayerEntry {
        private final String name;
        private final int ping;

        public PlayerEntry(String name, int ping) {
            this.name = name;
            this.ping = ping;
        }

        public String getName() {
            return name;
        }

        public int getPing() {
            return ping;
        }
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
        // Paper 1.16.5+ / 1.21+ main-thread check: Bukkit.getOnlinePlayers()
        // and Player#getPing() are forbidden off the main server thread.
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "NetworkAndPlayerMetrics.collect() must be called on the main server thread. " +
                "Current thread: " + Thread.currentThread().getName() +
                ". This is a bug in Usage Monitor — please report it."
            );
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int max = Bukkit.getMaxPlayers();
        List<PlayerEntry> list = new ArrayList<>();

        for (Player p : onlinePlayers) {
            int ping = extractPing(p);
            list.add(new PlayerEntry(p.getName(), ping));
        }

        return new NetworkAndPlayerMetrics(onlinePlayers.size(), max, list);
    }

    /**
     * Safe multi-version ping extraction.
     */
    public static int extractPing(Player player) {
        if (player == null) return 0;

        // 1. Try Bukkit Player#getPing() (Available in 1.17+ and modern Paper 1.16)
        try {
            Method getPingMethod = player.getClass().getMethod("getPing");
            Object val = getPingMethod.invoke(player);
            if (val instanceof Integer) {
                return (Integer) val;
            }
        } catch (Throwable ignored) {}

        // 2. Try CraftPlayer#getHandle() -> EntityPlayer#ping (Spigot 1.16.5)
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandle.invoke(player);
            if (entityPlayer != null) {
                Field pingField = entityPlayer.getClass().getField("ping");
                return pingField.getInt(entityPlayer);
            }
        } catch (Throwable ignored) {}

        return 0;
    }

    public int getOnlinePlayerCount() {
        return onlinePlayerCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public List<PlayerEntry> getPlayerList() {
        return playerList;
    }
}
