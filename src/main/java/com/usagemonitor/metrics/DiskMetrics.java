/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import java.io.File;

public class DiskMetrics {

    private final long totalSpaceBytes;
    private final long freeSpaceBytes;
    private final long usableSpaceBytes;
    private final long usedSpaceBytes;

    public DiskMetrics(long total, long free, long usable) {
        this.totalSpaceBytes = total;
        this.freeSpaceBytes = free;
        this.usableSpaceBytes = usable;
        this.usedSpaceBytes = Math.max(0, total - free);
    }

    public static DiskMetrics collect(File serverDir) {
        File root = serverDir != null ? serverDir : new File(".");
        return new DiskMetrics(root.getTotalSpace(), root.getFreeSpace(), root.getUsableSpace());
    }

    public double getUsedPercentage() {
        if (totalSpaceBytes <= 0) return 0.0;
        return ((double) usedSpaceBytes / (double) totalSpaceBytes) * 100.0;
    }

    public long getTotalSpaceBytes() { return totalSpaceBytes; }
    public long getFreeSpaceBytes() { return freeSpaceBytes; }
    public long getUsableSpaceBytes() { return usableSpaceBytes; }
    public long getUsedSpaceBytes() { return usedSpaceBytes; }
}
