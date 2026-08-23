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

// TPS comes from Paper/Purpur's getTPS() when available, otherwise via reflection
// to Spigot's MinecraftServer.recentTps, otherwise from a built-in tick-delta
// tracker. Reflection is one-shot and cached.
public class TpsTracker implements Runnable {

    private final Plugin plugin;
    private BukkitTask task;

    private static final int TICK_HISTORY_SIZE = 100;
    private final Deque<Long> tickHistory = new ArrayDeque<>(TICK_HISTORY_SIZE);
    private long lastTickTimeNano = 0;

    private double fallbackTps = 20.0;
    private double currentMspt = 0.0;

    private final long startTimeMillis;

    private static Object minecraftServerInstance = null;
    private static Field recentTpsField = null;
    private static boolean reflectionAttempted = false;

    public TpsTracker(Plugin plugin) {
        this.plugin = plugin;
        this.startTimeMillis = System.currentTimeMillis();
    }

    public void start() {
        this.lastTickTimeNano = System.nanoTime();
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
                if (tickHistory.size() >= TICK_HISTORY_SIZE) tickHistory.pollFirst();
                tickHistory.addLast(deltaNano);

                long totalDelta = 0;
                for (Long d : tickHistory) totalDelta += d;
                double avgDeltaMs = (totalDelta / (double) tickHistory.size()) / 1_000_000.0;
                this.currentMspt = avgDeltaMs;
                this.fallbackTps = avgDeltaMs <= 50.0
                        ? 20.0
                        : Math.min(20.0, 1000.0 / avgDeltaMs);
            }
        }
        lastTickTimeNano = now;
    }

    public double[] getTps() {
        try {
            Method getTPS = Bukkit.getServer().getClass().getMethod("getTPS");
            Object result = getTPS.invoke(Bukkit.getServer());
            if (result instanceof double[]) {
                double[] tps = (double[]) result;
                return clamp(tps);
            }
        } catch (Throwable ignored) {
        }
        try {
            ensureReflectionInit();
            if (minecraftServerInstance != null && recentTpsField != null) {
                double[] tps = (double[]) recentTpsField.get(minecraftServerInstance);
                if (tps != null && tps.length >= 3) return clamp(tps);
            }
        } catch (Throwable ignored) {
        }
        return new double[]{fallbackTps, fallbackTps, fallbackTps};
    }

    private static double[] clamp(double[] tps) {
        return new double[]{
                Math.min(20.0, Math.max(0.0, tps[0])),
                Math.min(20.0, Math.max(0.0, tps[1])),
                Math.min(20.0, Math.max(0.0, tps[2]))
        };
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
        }
    }

    public double getMspt() { return currentMspt; }

    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    public String getFormattedUptime() {
        long totalSeconds = getUptimeMillis() / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0)  return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        if (hours > 0) return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        return String.format("%02dm %02ds", minutes, seconds);
    }
}
