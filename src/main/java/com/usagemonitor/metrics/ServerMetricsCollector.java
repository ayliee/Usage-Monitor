/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Aggregates all server hardware, system, and game performance metrics into a unified snapshot.
 */
public class ServerMetricsCollector {

    private final JavaPlugin plugin;
    private final TpsTracker tpsTracker;

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

        public MemoryMetrics getMemory() {
            return memory;
        }

        public CpuMetrics getCpu() {
            return cpu;
        }

        public DiskMetrics getDisk() {
            return disk;
        }

        public NetworkAndPlayerMetrics getNetwork() {
            return network;
        }

        public WorldMetrics getWorlds() {
            return worlds;
        }

        public double[] getTps() {
            return tps;
        }

        public double getMspt() {
            return mspt;
        }

        public String getUptime() {
            return uptime;
        }

        public String getServerVersion() {
            return serverVersion;
        }

        public String getBukkitVersion() {
            return bukkitVersion;
        }

        public boolean isOnline() {
            return isOnline;
        }
    }

    public ServerMetricsCollector(JavaPlugin plugin, TpsTracker tpsTracker) {
        this.plugin = plugin;
        this.tpsTracker = tpsTracker;
    }

    public Snapshot collectSnapshot(boolean isOnline) {
        MemoryMetrics memory = MemoryMetrics.collect();
        CpuMetrics cpu = CpuMetrics.collect();
        DiskMetrics disk = DiskMetrics.collect(plugin.getServer().getWorldContainer());
        NetworkAndPlayerMetrics network = NetworkAndPlayerMetrics.collect();
        WorldMetrics worlds = WorldMetrics.collect();

        double[] tps = tpsTracker.getTps();
        double mspt = tpsTracker.getMspt();
        String uptime = tpsTracker.getFormattedUptime();

        String serverVersion = Bukkit.getName() + " " + Bukkit.getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();

        return new Snapshot(memory, cpu, disk, network, worlds, tps, mspt, uptime, serverVersion, bukkitVersion, isOnline);
    }
}
