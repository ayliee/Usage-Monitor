/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.discord;

import com.usagemonitor.config.PluginConfig;
import com.usagemonitor.discord.model.EmbedBuilder;
import com.usagemonitor.metrics.MemoryMetrics;
import com.usagemonitor.metrics.NetworkAndPlayerMetrics;
import com.usagemonitor.metrics.ServerMetricsCollector;
import com.usagemonitor.metrics.WorldMetrics;

// Builds the live dashboard embed. Emojis default to empty so the embed is
// text-only; setting any monitor.emojis.* key brings them back.
public class DiscordPayloadBuilder {

    private final PluginConfig config;
    private final String serverName;

    public DiscordPayloadBuilder(PluginConfig config, String serverName) {
        this.config = config;
        this.serverName = serverName == null || serverName.isEmpty() ? "Minecraft Server" : serverName;
    }

    private String es(String key) {
        String e = config.emoji(key);
        return e.isEmpty() ? "" : e + " ";
    }

    public String buildLiveDashboardPayload(ServerMetricsCollector.Snapshot snapshot) {
        int color = parseHexColor(config.getEmbedColor());
        if (!snapshot.isOnline()) color = 0xE74C3C;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(color);
        embed.setTitle(es("title") + escape(serverName) + " • Live Usage");

        StringBuilder desc = new StringBuilder();
        if (snapshot.isOnline()) {
            desc.append("**Status:** ").append(es("status-online")).append("ONLINE  •  Uptime `")
                    .append(snapshot.getUptime()).append("`");
        } else {
            desc.append("**Status:** ").append(es("status-offline")).append("OFFLINE  •  Last uptime `")
                    .append(snapshot.getUptime()).append("`");
        }
        embed.setDescription(desc.toString());

        if (config.isShowResources()) {
            StringBuilder val = new StringBuilder();
            val.append("> **").append(es("ram")).append("RAM:** `")
                    .append(MemoryMetrics.formatBytes(snapshot.getMemory().getSystemTotalBytes())).append("`\n");
            val.append("> **").append(es("cpu")).append("CPU:** `")
                    .append(snapshot.getCpu().getAvailableProcessors()).append(" Cores`\n");
            val.append("> **").append(es("storage")).append("Storage:** `")
                    .append(MemoryMetrics.formatBytes(snapshot.getDisk().getTotalSpaceBytes())).append("`\n");
            val.append("> **").append(es("jvm-heap")).append("JVM Heap:** `")
                    .append(MemoryMetrics.formatBytes(snapshot.getMemory().getHeapMaxBytes())).append("`");
            embed.addField(es("resources") + "Resources", val.toString(), true);
        }

        if (config.isShowConfiguration()) {
            double[] tps = snapshot.getTps();
            String tpsEmoji = tps[0] >= 19.0 ? es("status-online") : (tps[0] >= 15.0 ? "" : es("status-offline"));
            StringBuilder val = new StringBuilder();
            val.append("> **").append(es("software")).append("Software:** `")
                    .append(escape(stripBrackets(snapshot.getServerVersion()))).append("`\n");
            val.append("> **").append(es("bukkit")).append("Bukkit:** `")
                    .append(escape(snapshot.getBukkitVersion())).append("`\n");
            val.append("> **").append(es("tps")).append("TPS (1m):** ").append(tpsEmoji)
                    .append("`").append(String.format("%.2f", tps[0])).append("`\n");
            val.append("> **").append(es("mspt")).append("MSPT:** `")
                    .append(String.format("%.2f ms", snapshot.getMspt())).append("`");
            embed.addField(es("configuration") + "Configuration", val.toString(), true);
        }

        if (config.isShowLiveStats()) {
            StringBuilder val = new StringBuilder();
            double procCpu = snapshot.getCpu().getProcessCpuLoad();
            String cpuStr = procCpu >= 0 ? String.format("%.1f%%", procCpu) : "N/A";
            double cpuPct = Math.max(0, Math.min(100, procCpu));
            val.append("> **").append(es("cpu")).append("CPU:** `").append(cpuStr).append("` ")
                    .append(miniBar(cpuPct, 8)).append("\n");
            MemoryMetrics mem = snapshot.getMemory();
            double memPct = mem.getHeapUsagePercentage();
            val.append("> **").append(es("memory")).append("Memory:** `")
                    .append(MemoryMetrics.formatBytes(mem.getHeapUsedBytes())).append("` / `")
                    .append(MemoryMetrics.formatBytes(mem.getHeapMaxBytes())).append("` (")
                    .append(String.format("%.1f%%", memPct)).append(") ")
                    .append(miniBar(memPct, 8)).append("\n");
            double diskPct = snapshot.getDisk().getUsedPercentage();
            val.append("> **").append(es("disk")).append("Disk:** `")
                    .append(MemoryMetrics.formatBytes(snapshot.getDisk().getUsedSpaceBytes())).append("` / `")
                    .append(MemoryMetrics.formatBytes(snapshot.getDisk().getTotalSpaceBytes())).append("` (")
                    .append(String.format("%.1f%%", diskPct)).append(") ")
                    .append(miniBar(diskPct, 8));
            embed.addField(es("live-stats") + "Live Stats", val.toString(), false);
        }

        if (config.isShowActions()) {
            NetworkAndPlayerMetrics net = snapshot.getNetwork();
            StringBuilder val = new StringBuilder();
            val.append("> **").append(es("players-online")).append("Players Online:** `")
                    .append(net.getOnlinePlayerCount()).append(" / ").append(net.getMaxPlayers()).append("`");
            embed.addField(es("players") + "Players", val.toString(), false);
        }

        if (config.isShowWorldDetailsSafe()) {
            WorldMetrics wm = snapshot.getWorlds();
            StringBuilder val = new StringBuilder();
            val.append("> **").append(es("chunks")).append("Chunks:** `")
                    .append(wm.getTotalLoadedChunks()).append("`  •  ");
            val.append("**").append(es("entities")).append("Entities:** `")
                    .append(wm.getTotalEntities()).append("`");
            if (wm.getWorlds().size() <= 6) {
                val.append("\n");
                int i = 0;
                for (WorldMetrics.WorldDetail wd : wm.getWorlds()) {
                    if (i++ > 0) val.append("  •  ");
                    val.append("`").append(escape(wd.getName())).append("`: ")
                            .append(wd.getLoadedChunks()).append("c / ").append(wd.getEntityCount()).append("e");
                }
            }
            embed.addField(es("worlds") + "Worlds", val.toString(), false);
        }

        return new JsonObjectBuilder()
                .add("embeds", java.util.List.of(embed.toJsonObject()))
                .build();
    }

    private static String miniBar(double percent, int width) {
        if (width < 1) width = 1;
        double clamped = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round(clamped / 100.0 * width);
        StringBuilder sb = new StringBuilder(width + 2);
        sb.append('[');
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        sb.append(']');
        return sb.toString();
    }

    private static int parseHexColor(String hex) {
        try {
            if (hex == null) return 0x2B2D31;
            String clean = hex.trim().replace("#", "");
            if (clean.isEmpty()) return 0x2B2D31;
            return Integer.parseInt(clean, 16);
        } catch (NumberFormatException e) {
            return 0x2B2D31;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.length() > 256 ? s.substring(0, 256) : s;
    }

    private static String stripBrackets(String s) {
        return s == null ? "" : s;
    }
}
