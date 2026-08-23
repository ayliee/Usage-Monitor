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
        private final String name;
        private final String environment;
        private final int loadedChunks;
        private final int entityCount;

        public WorldDetail(String name, String environment, int loadedChunks, int entityCount) {
            this.name = name;
            this.environment = environment;
            this.loadedChunks = loadedChunks;
            this.entityCount = entityCount;
        }

        public String getName() { return name; }
        public String getEnvironment() { return environment; }
        public int getLoadedChunks() { return loadedChunks; }
        public int getEntityCount() { return entityCount; }
    }

    private final int totalLoadedChunks;
    private final int totalEntities;
    private final List<WorldDetail> worlds;

    public WorldMetrics(int totalLoadedChunks, int totalEntities, List<WorldDetail> worlds) {
        this.totalLoadedChunks = totalLoadedChunks;
        this.totalEntities = totalEntities;
        this.worlds = worlds;
    }

    public static WorldMetrics collect() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "WorldMetrics.collect() must run on the main server thread. Current: "
                + Thread.currentThread().getName());
        }

        int totalChunks = 0;
        int totalEnts = 0;
        List<WorldDetail> list = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            int chunks = world.getLoadedChunks().length;
            int ents = world.getEntities().size();
            totalChunks += chunks;
            totalEnts += ents;
            list.add(new WorldDetail(
                    world.getName(),
                    world.getEnvironment().name().toLowerCase(),
                    chunks, ents
            ));
        }

        return new WorldMetrics(totalChunks, totalEnts, list);
    }

    public int getTotalLoadedChunks() { return totalLoadedChunks; }
    public int getTotalEntities() { return totalEntities; }
    public List<WorldDetail> getWorlds() { return worlds; }
}
