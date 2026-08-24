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
        final String name;
        final int ping;
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

    public NetworkAndPlayerMetrics(int online, int max, List<PlayerEntry> list) {
        this.onlinePlayerCount = online;
        this.maxPlayers = max;
        this.playerList = list;
    }

    public static NetworkAndPlayerMetrics collect() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "NetworkAndPlayerMetrics.collect() must run on the main thread. Current: "
                + Thread.currentThread().getName());
        }

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int max = Bukkit.getMaxPlayers();
        List<PlayerEntry> list = new ArrayList<>();
        for (Player p : online) {
            list.add(new PlayerEntry(p.getName(), extractPing(p)));
        }
        return new NetworkAndPlayerMetrics(online.size(), max, list);
    }

    private static int extractPing(Player p) {
        if (p == null) return 0;
        // Modern Bukkit API (1.17+, also backported to Paper 1.16).
        try {
            Method getPing = p.getClass().getMethod("getPing");
            Object v = getPing.invoke(p);
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) {
        }
        // Fallback for older Spigot 1.16.5 - go through NMS CraftPlayer.handle().ping
        try {
            Method getHandle = p.getClass().getMethod("getHandle");
            Object nms = getHandle.invoke(p);
            if (nms != null) return nms.getClass().getField("ping").getInt(nms);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public int getOnlinePlayerCount() { return onlinePlayerCount; }
    public int getMaxPlayers() { return maxPlayers; }
    public List<PlayerEntry> getPlayerList() { return playerList; }
}
