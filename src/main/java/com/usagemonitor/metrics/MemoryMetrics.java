/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public class MemoryMetrics {

    private final long heapUsedBytes;
    private final long heapAllocatedBytes;
    private final long heapMaxBytes;
    private final long heapFreeBytes;
    private final long nonHeapUsedBytes;
    private final long systemTotalBytes;
    private final long systemFreeBytes;
    private final long systemUsedBytes;

    public MemoryMetrics(long heapUsed, long heapAllocated, long heapMax,
                         long nonHeapUsed, long sysTotal, long sysFree) {
        this.heapUsedBytes = heapUsed;
        this.heapAllocatedBytes = heapAllocated;
        this.heapMaxBytes = heapMax > 0 ? heapMax : heapAllocated;
        this.heapFreeBytes = Math.max(0, this.heapMaxBytes - heapUsed);
        this.nonHeapUsedBytes = nonHeapUsed;
        this.systemTotalBytes = sysTotal;
        this.systemFreeBytes = sysFree;
        this.systemUsedBytes = Math.max(0, sysTotal - sysFree);
    }

    public static MemoryMetrics collect() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

        long sysTotal = -1;
        long sysFree = -1;
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            Class<?> sunOs = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOs.isInstance(os)) {
                Object t = sunOs.getMethod("getTotalPhysicalMemorySize").invoke(os);
                Object f = sunOs.getMethod("getFreePhysicalMemorySize").invoke(os);
                if (t instanceof Long) sysTotal = (Long) t;
                if (f instanceof Long) sysFree = (Long) f;
            }
        } catch (Throwable ignored) {
        }

        return new MemoryMetrics(
                heap.getUsed(), heap.getCommitted(), heap.getMax(),
                nonHeap.getUsed(), sysTotal, sysFree);
    }

    public double getHeapUsagePercentage() {
        if (heapMaxBytes <= 0) return 0.0;
        return ((double) heapUsedBytes / (double) heapMaxBytes) * 100.0;
    }

    public double getSystemUsagePercentage() {
        if (systemTotalBytes <= 0) return 0.0;
        return ((double) systemUsedBytes / (double) systemTotalBytes) * 100.0;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) return String.format("%.2f GB", mb / 1024.0);
        return String.format("%.1f MB", mb);
    }

    public static String createProgressBar(double percentage, int barLength) {
        percentage = Math.max(0.0, Math.min(100.0, percentage));
        int filled = (int) Math.round((percentage / 100.0) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));
        StringBuilder sb = new StringBuilder("`[");
        for (int i = 0; i < filled; i++) sb.append('\u2588');
        for (int i = 0; i < barLength - filled; i++) sb.append('\u2591');
        sb.append(String.format("] %5.1f%%`", percentage));
        return sb.toString();
    }

    public long getHeapUsedBytes() { return heapUsedBytes; }
    public long getHeapAllocatedBytes() { return heapAllocatedBytes; }
    public long getHeapMaxBytes() { return heapMaxBytes; }
    public long getHeapFreeBytes() { return heapFreeBytes; }
    public long getNonHeapUsedBytes() { return nonHeapUsedBytes; }
    public long getSystemTotalBytes() { return systemTotalBytes; }
    public long getSystemFreeBytes() { return systemFreeBytes; }
    public long getSystemUsedBytes() { return systemUsedBytes; }
}
