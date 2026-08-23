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

    public DiskMetrics(long totalSpaceBytes, long freeSpaceBytes, long usableSpaceBytes) {
        this.totalSpaceBytes = totalSpaceBytes;
        this.freeSpaceBytes = freeSpaceBytes;
        this.usableSpaceBytes = usableSpaceBytes;
        this.usedSpaceBytes = Math.max(0, totalSpaceBytes - freeSpaceBytes);
    }

    public static DiskMetrics collect(File serverDirectory) {
        File root = serverDirectory != null ? serverDirectory : new File(".");
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
