/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ServerMetricsCollector {

    private final JavaPlugin plugin;
    private final TpsTracker tps;

    public ServerMetricsCollector(JavaPlugin plugin, TpsTracker tps) {
        this.plugin = plugin;
        this.tps = tps;
    }

    public static class Snapshot {
        private final MemoryMetrics memory;
        private final CpuMetrics cpu;
        private final DiskMetrics disk;
        private final NetworkAndPlayerMetrics network;
        private final WorldMetrics worlds;
        private final double[] tps;
        private final double mspt;
        private final String uptime;
        private final String serverVersion;
        private final String bukkitVersion;
        private final boolean isOnline;

        public Snapshot(MemoryMetrics memory, CpuMetrics cpu, DiskMetrics disk,
                        NetworkAndPlayerMetrics network, WorldMetrics worlds,
                        double[] tps, double mspt, String uptime,
                        String serverVersion, String bukkitVersion, boolean isOnline) {
            this.memory = memory;
            this.cpu = cpu;
            this.disk = disk;
            this.network = network;
            this.worlds = worlds;
            this.tps = tps;
            this.mspt = mspt;
            this.uptime = uptime;
            this.serverVersion = serverVersion;
            this.bukkitVersion = bukkitVersion;
            this.isOnline = isOnline;
        }

        public MemoryMetrics getMemory() { return memory; }
        public CpuMetrics getCpu() { return cpu; }
        public DiskMetrics getDisk() { return disk; }
        public NetworkAndPlayerMetrics getNetwork() { return network; }
        public WorldMetrics getWorlds() { return worlds; }
        public double[] getTps() { return tps; }
        public double getMspt() { return mspt; }
        public String getUptime() { return uptime; }
        public String getServerVersion() { return serverVersion; }
        public String getBukkitVersion() { return bukkitVersion; }
        public boolean isOnline() { return isOnline; }
    }

    public Snapshot collectSnapshot(boolean isOnline) {
        File worldContainer = plugin.getServer().getWorldContainer();
        return new Snapshot(
                MemoryMetrics.collect(),
                CpuMetrics.collect(),
                DiskMetrics.collect(worldContainer),
                NetworkAndPlayerMetrics.collect(),
                WorldMetrics.collect(),
                tps.getTps(),
                tps.getMspt(),
                tps.getFormattedUptime(),
                Bukkit.getName() + " " + Bukkit.getVersion(),
                Bukkit.getBukkitVersion(),
                isOnline
        );
    }
}
