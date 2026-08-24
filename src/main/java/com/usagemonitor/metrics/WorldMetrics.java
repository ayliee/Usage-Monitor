/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class WorldMetrics {

    public static class WorldDetail {
        final String name;
        final String environment;
        final int loadedChunks;
        final int entityCount;
        public WorldDetail(String n, String env, int c, int e) {
            this.name = n; this.environment = env; this.loadedChunks = c; this.entityCount = e;
        }
        public String getName() { return name; }
        public String getEnvironment() { return environment; }
        public int getLoadedChunks() { return loadedChunks; }
        public int getEntityCount() { return entityCount; }
    }

    private final int totalLoadedChunks;
    private final int totalEntities;
    private final List<WorldDetail> worlds;

    public WorldMetrics(int totalChunks, int totalEnts, List<WorldDetail> worlds) {
        this.totalLoadedChunks = totalChunks;
        this.totalEntities = totalEnts;
        this.worlds = worlds;
    }

    public static WorldMetrics collect() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "WorldMetrics.collect() must run on the main thread. Current: "
                + Thread.currentThread().getName());
        }

        int totalChunks = 0;
        int totalEnts = 0;
        List<WorldDetail> list = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            int chunks = w.getLoadedChunks().length;
            int ents = w.getEntities().size();
            totalChunks += chunks;
            totalEnts += ents;
            list.add(new WorldDetail(w.getName(), w.getEnvironment().name().toLowerCase(), chunks, ents));
        }
        return new WorldMetrics(totalChunks, totalEnts, list);
    }

    public int getTotalLoadedChunks() { return totalLoadedChunks; }
    public int getTotalEntities() { return totalEntities; }
    public List<WorldDetail> getWorlds() { return worlds; }
}
