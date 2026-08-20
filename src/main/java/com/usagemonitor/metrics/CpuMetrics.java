/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;

/**
 * Captures CPU utilization (Process & System) and JVM thread counts.
 */
public class CpuMetrics {

    private final double processCpuLoad; // 0.0 to 100.0
    private final double systemCpuLoad;  // 0.0 to 100.0
    private final int availableProcessors;

    private final int liveThreadCount;
    private final int peakThreadCount;
    private final int daemonThreadCount;

    public CpuMetrics(double processCpuLoad, double systemCpuLoad, int availableProcessors,
                      int liveThreadCount, int peakThreadCount, int daemonThreadCount) {
        this.processCpuLoad = processCpuLoad;
        this.systemCpuLoad = systemCpuLoad;
        this.availableProcessors = availableProcessors;
        this.liveThreadCount = liveThreadCount;
        this.peakThreadCount = peakThreadCount;
        this.daemonThreadCount = daemonThreadCount;
    }

    public static CpuMetrics collect() {
        double procCpu = -1.0;
        double sysCpu = -1.0;
        int processors = Runtime.getRuntime().availableProcessors();

        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osBean)) {
                Method getProcessCpu = sunOsClass.getMethod("getProcessCpuLoad");
                Method getSystemCpu = sunOsClass.getMethod("getSystemCpuLoad");

                Object procVal = getProcessCpu.invoke(osBean);
                Object sysVal = getSystemCpu.invoke(osBean);

                if (procVal instanceof Double) {
                    double val = (Double) procVal;
                    if (val >= 0.0) procCpu = val * 100.0;
                }
                if (sysVal instanceof Double) {
                    double val = (Double) sysVal;
                    if (val >= 0.0) sysCpu = val * 100.0;
                }
            }
        } catch (Throwable ignored) {
            // Fallback for standard JVM
        }

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int liveThreads = threadBean.getThreadCount();
        int peakThreads = threadBean.getPeakThreadCount();
        int daemonThreads = threadBean.getDaemonThreadCount();

        return new CpuMetrics(procCpu, sysCpu, processors, liveThreads, peakThreads, daemonThreads);
    }

    public double getProcessCpuLoad() {
        return processCpuLoad;
    }

    public double getSystemCpuLoad() {
        return systemCpuLoad;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public int getLiveThreadCount() {
        return liveThreadCount;
    }

    public int getPeakThreadCount() {
        return peakThreadCount;
    }

    public int getDaemonThreadCount() {
        return daemonThreadCount;
    }
}
