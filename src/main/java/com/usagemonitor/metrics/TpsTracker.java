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

// Three-tier TPS resolution: Paper/Purpur native -> Spigot NMS via reflection
// -> built-in tick-delta tracker. Reflection is one-shot and cached.
public class TpsTracker implements Runnable {

    private final Plugin plugin;
    private BukkitTask task;

    private static final int HISTORY = 100;
    private final Deque<Long> tickDeltas = new ArrayDeque<>(HISTORY);
    private long lastTickNs = 0;

    private double fallbackTps = 20.0;
    private double currentMspt = 0.0;
    private final long startMs = System.currentTimeMillis();

    private static Object nmsServer = null;
    private static Field recentTpsField = null;
    private static boolean reflectionTried = false;

    public TpsTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.lastTickNs = System.nanoTime();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    @Override
    public void run() {
        long now = System.nanoTime();
        if (lastTickNs > 0) {
            long delta = now - lastTickNs;
            synchronized (tickDeltas) {
                if (tickDeltas.size() >= HISTORY) tickDeltas.pollFirst();
                tickDeltas.addLast(delta);
                long total = 0;
                for (Long d : tickDeltas) total += d;
                double avgMs = (total / (double) tickDeltas.size()) / 1_000_000.0;
                currentMspt = avgMs;
                fallbackTps = avgMs <= 50.0 ? 20.0 : Math.min(20.0, 1000.0 / avgMs);
            }
        }
        lastTickNs = now;
    }

    public double[] getTps() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            Object r = m.invoke(Bukkit.getServer());
            if (r instanceof double[]) return clamp((double[]) r);
        } catch (Throwable ignored) {
        }
        try {
            ensureReflection();
            if (nmsServer != null && recentTpsField != null) {
                double[] r = (double[]) recentTpsField.get(nmsServer);
                if (r != null && r.length >= 3) return clamp(r);
            }
        } catch (Throwable ignored) {
        }
        return new double[]{fallbackTps, fallbackTps, fallbackTps};
    }

    private static double[] clamp(double[] t) {
        return new double[]{
                Math.min(20.0, Math.max(0.0, t[0])),
                Math.min(20.0, Math.max(0.0, t[1])),
                Math.min(20.0, Math.max(0.0, t[2]))
        };
    }

    // TODO: revisit once Paper ships a non-reflective TPS API
    private static synchronized void ensureReflection() {
        if (reflectionTried) return;
        reflectionTried = true;
        try {
            Class<?> craft = Bukkit.getServer().getClass();
            nmsServer = craft.getMethod("getServer").invoke(Bukkit.getServer());
            if (nmsServer != null) recentTpsField = nmsServer.getClass().getField("recentTps");
        } catch (Throwable ignored) {
        }
    }

    public double getMspt() { return currentMspt; }

    public long getUptimeMillis() {
        return System.currentTimeMillis() - startMs;
    }

    public String getFormattedUptime() {
        long s = getUptimeMillis() / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (d > 0)  return String.format("%dd %02dh %02dm %02ds", d, h, m, sec);
        if (h > 0) return String.format("%02dh %02dm %02ds", h, m, sec);
        return String.format("%02dm %02ds", m, sec);
    }
}
