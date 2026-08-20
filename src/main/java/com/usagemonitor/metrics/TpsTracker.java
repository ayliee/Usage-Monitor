/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks server TPS (Ticks Per Second) and MSPT (Milliseconds Per Tick)
 * across Spigot, Paper, Purpur, and Bukkit versions 1.16.5 through 1.21+.
 */
public class TpsTracker implements Runnable {

    private final Plugin plugin;
    private BukkitTask task;

    private static final int TICK_HISTORY_SIZE = 100;
    private final Deque<Long> tickHistory = new ArrayDeque<>(TICK_HISTORY_SIZE);
    private long lastTickTimeNano = 0;

    private double fallbackTps = 20.0;
    private double currentMspt = 0.0;

    private final long startTimeMillis;

    // Reflection cache for Spigot / NMS
    private static Object minecraftServerInstance = null;
    private static Field recentTpsField = null;
    private static boolean reflectionAttempted = false;

    public TpsTracker(Plugin plugin) {
        this.plugin = plugin;
        this.startTimeMillis = System.currentTimeMillis();
    }

    public void start() {
        this.lastTickTimeNano = System.nanoTime();
        // Run on the primary server tick thread every 1 tick
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        long now = System.nanoTime();
        if (lastTickTimeNano > 0) {
            long deltaNano = now - lastTickTimeNano;
            synchronized (tickHistory) {
                if (tickHistory.size() >= TICK_HISTORY_SIZE) {
                    tickHistory.pollFirst();
                }
                tickHistory.addLast(deltaNano);

                // Calculate MSPT
                long totalDelta = 0;
                for (Long d : tickHistory) {
                    totalDelta += d;
                }
                double avgDeltaMs = (totalDelta / (double) tickHistory.size()) / 1_000_000.0;
                this.currentMspt = avgDeltaMs;

                // Fallback TPS calculation
                if (avgDeltaMs <= 50.0) {
                    this.fallbackTps = 20.0;
                } else {
                    this.fallbackTps = Math.min(20.0, 1000.0 / avgDeltaMs);
                }
            }
        }
        lastTickTimeNano = now;
    }

    /**
     * Retrieves TPS array [1m, 5m, 15m].
     */
    public double[] getTps() {
        // 1. Try Paper / Purpur native Server.getTPS()
        try {
            Method getTPSMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            Object result = getTPSMethod.invoke(Bukkit.getServer());
            if (result instanceof double[]) {
                double[] tps = (double[]) result;
                return new double[]{
                        Math.min(20.0, Math.max(0.0, tps[0])),
                        Math.min(20.0, Math.max(0.0, tps[1])),
                        Math.min(20.0, Math.max(0.0, tps[2]))
                };
            }
        } catch (Throwable ignored) {
            // Not Paper or method not accessible
        }

        // 2. Try Spigot MinecraftServer.getServer().recentTps
        try {
            ensureReflectionInit();
            if (minecraftServerInstance != null && recentTpsField != null) {
                double[] tps = (double[]) recentTpsField.get(minecraftServerInstance);
                if (tps != null && tps.length >= 3) {
                    return new double[]{
                            Math.min(20.0, Math.max(0.0, tps[0])),
                            Math.min(20.0, Math.max(0.0, tps[1])),
                            Math.min(20.0, Math.max(0.0, tps[2]))
                    };
                }
            }
        } catch (Throwable ignored) {
            // Reflection fallback failed
        }

        // 3. Built-in rolling tick tracker fallback
        return new double[]{fallbackTps, fallbackTps, fallbackTps};
    }

    private static synchronized void ensureReflectionInit() {
        if (reflectionAttempted) return;
        reflectionAttempted = true;

        try {
            Class<?> craftServerClass = Bukkit.getServer().getClass();
            Method getServerMethod = craftServerClass.getMethod("getServer");
            minecraftServerInstance = getServerMethod.invoke(Bukkit.getServer());
            if (minecraftServerInstance != null) {
                recentTpsField = minecraftServerInstance.getClass().getField("recentTps");
            }
        } catch (Throwable ignored) {
            // Field not found or accessible
        }
    }

    public double getMspt() {
        return currentMspt;
    }

    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    public String getFormattedUptime() {
        long totalSeconds = getUptimeMillis() / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        } else {
            return String.format("%02dm %02ds", minutes, seconds);
        }
    }
}
