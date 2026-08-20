/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;

/**
 * Captures comprehensive JVM Heap, Off-Heap, and Physical System RAM statistics.
 */
public class MemoryMetrics {

    private final long heapUsedBytes;
    private final long heapAllocatedBytes;
    private final long heapMaxBytes;
    private final long heapFreeBytes;

    private final long nonHeapUsedBytes;

    private final long systemTotalBytes;
    private final long systemFreeBytes;
    private final long systemUsedBytes;

    public MemoryMetrics(long heapUsedBytes, long heapAllocatedBytes, long heapMaxBytes,
                         long nonHeapUsedBytes, long systemTotalBytes, long systemFreeBytes) {
        this.heapUsedBytes = heapUsedBytes;
        this.heapAllocatedBytes = heapAllocatedBytes;
        this.heapMaxBytes = heapMaxBytes > 0 ? heapMaxBytes : heapAllocatedBytes;
        this.heapFreeBytes = Math.max(0, this.heapMaxBytes - heapUsedBytes);

        this.nonHeapUsedBytes = nonHeapUsedBytes;

        this.systemTotalBytes = systemTotalBytes;
        this.systemFreeBytes = systemFreeBytes;
        this.systemUsedBytes = Math.max(0, systemTotalBytes - systemFreeBytes);
    }

    public static MemoryMetrics collect() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        long heapUsed = heap.getUsed();
        long heapAllocated = heap.getCommitted();
        long heapMax = heap.getMax();
        long nonHeapUsed = nonHeap.getUsed();

        long sysTotal = -1;
        long sysFree = -1;

        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osBean)) {
                Method getTotalMem = sunOsClass.getMethod("getTotalPhysicalMemorySize");
                Method getFreeMem = sunOsClass.getMethod("getFreePhysicalMemorySize");
                Object totalObj = getTotalMem.invoke(osBean);
                Object freeObj = getFreeMem.invoke(osBean);
                if (totalObj instanceof Long) sysTotal = (Long) totalObj;
                if (freeObj instanceof Long) sysFree = (Long) freeObj;
            }
        } catch (Throwable ignored) {
            // Fallback for JVMs without sun OS MXBean
        }

        return new MemoryMetrics(heapUsed, heapAllocated, heapMax, nonHeapUsed, sysTotal, sysFree);
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
        if (mb >= 1024.0) {
            return String.format("%.2f GB", mb / 1024.0);
        }
        return String.format("%.1f MB", mb);
    }

    public static String createProgressBar(double percentage, int barLength) {
        percentage = Math.max(0.0, Math.min(100.0, percentage));
        int filled = (int) Math.round((percentage / 100.0) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));
        int empty = barLength - filled;

        StringBuilder sb = new StringBuilder("`[");
        for (int i = 0; i < filled; i++) sb.append("█");
        for (int i = 0; i < empty; i++) sb.append("░");
        sb.append(String.format("] %5.1f%%`", percentage));
        return sb.toString();
    }

    public long getHeapUsedBytes() {
        return heapUsedBytes;
    }

    public long getHeapAllocatedBytes() {
        return heapAllocatedBytes;
    }

    public long getHeapMaxBytes() {
        return heapMaxBytes;
    }

    public long getHeapFreeBytes() {
        return heapFreeBytes;
    }

    public long getNonHeapUsedBytes() {
        return nonHeapUsedBytes;
    }

    public long getSystemTotalBytes() {
        return systemTotalBytes;
    }

    public long getSystemFreeBytes() {
        return systemFreeBytes;
    }

    public long getSystemUsedBytes() {
        return systemUsedBytes;
    }
}
