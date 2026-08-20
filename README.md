# Usage Monitor

A self-hosted, zero-dependency Minecraft server monitor that maintains a single live Discord message through a webhook. Every refresh, the plugin edits the same message in place — no bot account, no OAuth flow, no permissions to wire up. You create a webhook in your Discord channel, paste the URL into `config.yml`, and a live dashboard appears within five seconds.

The dashboard reports the JVM heap and host RAM, the per-world chunk and entity counts, the rolling TPS and MSPT, the live player count, and the host CPU and disk usage. Each section can be toggled on or off from the config, the accent color is a hex code, and every visual element (down to whether the embed has emoji decoration at all) is controlled from one file.

---

## Table of Contents

- [What it does](#what-it-does)
- [Join the community](#join-the-community)
- [Features](#features)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [In-game commands](#in-game-commands)
- [How it is built](#how-it-is-built)
- [Project layout](#project-layout)
- [Customizing the dashboard](#customizing-the-dashboard)
- [Extending the plugin](#extending-the-plugin)
- [Build from source](#build-from-source)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## What it does

Usage Monitor is a Paper / Spigot / Bukkit plugin (any modern Minecraft server from 1.16.5 through 1.21+) that posts a single live embed to a Discord channel and edits that embed on a fixed interval. The embed contains:

- A title that uses the server's bind address and the literal text "Live Usage"
- A status line with the server's online / offline state and current uptime
- A Resources section with total host RAM, available CPU cores, total disk space, and max JVM heap
- A Configuration section with the software name (Paper, Spigot, Purpur, ...), Bukkit version, current TPS, and MSPT
- A Live Stats section with the current process CPU load, used / total JVM heap with a percentage and a bar, and used / total disk with a percentage and a bar
- A Players section with the online count and the server's player cap
- A Worlds section with the total loaded chunks, total entities, and a per-world breakdown
- An accent color rendered as a thin vertical bar on the left of the embed

Nothing in the embed is required. Every section is independently togglable, the accent color is a config value, the section header text is plain English, and by default the embed has no emoji decoration.

The plugin maintains exactly one message in the channel. On startup it posts the initial embed, captures the message ID returned by Discord, and from then on PATCHes that same message ID on every cycle. If the message is deleted in Discord (manually, by a moderator, or by a different tool) the next cycle will continue to PATCH the now-invalid ID and log a warning telling you to run `/usagemonitor reset`. The plugin never auto-recreates a deleted message, because that was the source of the most common complaint with the older bot-token approach (an edit failure used to trigger a fresh POST, which over time spammed the channel with duplicates).

## Join the community

If you want to ask questions, share your dashboard, or follow updates, the project lives in the following Discord community:

- Discord guild ID: `1414217749038891102`
- Discord invite link placeholder: `https://discord.com/channels/1414217749038891102`

Replace the invite link with a real `https://discord.gg/<invite-code>` URL once you have one set up for your server. The guild ID above is the canonical reference; an invite code is just a way to onboard new people into the guild.

## Features

- **Zero external dependencies.** The plugin uses only the Java standard library (`java.net.http`, `java.lang.management`, `java.nio.file`) and the Bukkit API. There is no shaded JDA, no Gson, no Apache HttpClient, and no third-party webhook library. The whole jar is under 50 KB.
- **Single live message.** The plugin posts exactly one embed and edits it in place. No matter how long the server runs, the channel never accumulates duplicates.
- **Webhook-based authentication.** No bot user, no OAuth, no permissions to configure. Create a webhook in your channel, paste the URL, and the plugin does the rest.
- **Main-thread safe on Paper 1.21+.** All Bukkit API access (`Bukkit.getOnlinePlayers()`, `world.getEntities()`, `Player.getPing()`) is dispatched to the main server thread. The async HTTP I/O never touches Bukkit state. The plugin throws a clear error rather than corrupt data if a code path is ever changed to call Bukkit API off-thread.
- **Configurable everything.** Refresh interval, embed color, every section's visibility, every emoji (or no emoji at all), the title, the footer, the player list truncation, the progress bar width, and the section header text are all in `config.yml`. The plugin never requires a recompile to change the look of the dashboard.
- **Persistent message ID.** The first POST captures the message ID and persists it to `config.yml`. Server restarts, reloads, and config edits all keep editing the same Discord message.
- **Manual reset command.** `/usagemonitor reset` clears the persisted message ID and forces a fresh POST on the next cycle. Use it if the original message was deleted, or if you want to relocate the dashboard to a different channel by changing the webhook URL.
- **Rate-limit safe at the default interval.** A 5-second refresh produces 12 edits per minute, well under Discord's 30 messages per 60 seconds and 5 edits per 5 seconds per webhook limits. The plugin refuses to accept intervals below 1 second.

## How it works

The plugin has four moving parts.

1. **Metric collectors** (the `com.usagemonitor.metrics` package) — a set of small classes that each gather one category of data. `MemoryMetrics` reads the JVM `MemoryMXBean` and the host's physical RAM through `OperatingSystemMXBean`. `CpuMetrics` reads process and system CPU load and thread counts. `DiskMetrics` walks the world container's file store. `TpsTracker` runs a one-tick repeating task that records tick deltas and exposes rolling TPS, MSPT, and uptime. `WorldMetrics` and `NetworkAndPlayerMetrics` are thin wrappers around the Bukkit world and player APIs.
2. **`ServerMetricsCollector`** — runs the collectors on the main server thread, bundles their results into an immutable `Snapshot` record, and returns the snapshot to the caller.
3. **`MonitorUpdateTask`** — a `Runnable` scheduled on the Bukkit scheduler. Every cycle it asks `ServerMetricsCollector` for a snapshot, builds the JSON payload with `DiscordPayloadBuilder`, and either POSTs the initial message (if no ID is saved) or PATCHes the saved ID (if one is).
4. **`DiscordClient`** — a thin async wrapper over Discord's webhook REST API. One method for `POST /webhooks/{id}/{token}?wait=true` to create a message and get its ID back, and one for `PATCH /webhooks/{id}/{token}/messages/{messageId}` to edit it. Uses `java.net.http.HttpClient` with HTTP/2.

The first time the plugin runs, `MonitorUpdateTask.run()` notices that `config.messageId` is empty, calls `DiscordClient.postWebhookMessage()`, captures the ID from the response, and persists it. Every subsequent cycle calls `DiscordClient.editWebhookMessage()` against the persisted ID. If the edit fails with HTTP 404 (message was deleted), 401, or 403, the plugin logs a specific error explaining the cause and continues to use the same ID on the next cycle. The plugin will never auto-POST a new message, because doing so on a recurring edit failure is what created the "wall of duplicates" problem in earlier versions.

The collection step is dispatched to the main server thread because Paper 1.16.5+ and 1.21+ enforce main-thread checks on `Bukkit.getOnlinePlayers()`, `world.getEntities()`, and `Player.getPing()`. The plugin is compiled against JDK 17 because Paper 1.21 requires it at runtime.

## Requirements

- A Minecraft server running Paper, Spigot, Purpur, or any fork that exposes the Bukkit API, version 1.16.5 or later (the plugin is built and tested against 1.21+).
- Java 17 or later on the server (Paper 1.21 requires this anyway).
- A Discord channel where you can create a webhook. Any guild you have the "Manage Webhooks" permission in works.

## Installation

1. **Download the jar.** Grab the latest `UsageMonitor-<version>.jar` from the Releases page of this repository, or build it from source (see below).
2. **Create a Discord webhook.** Open the target channel in your Discord client, click the gear icon to open Channel Settings, go to Integrations, then Webhooks, then New Webhook. Give it a name (it does not have to match the plugin), optionally pick an avatar, and click Copy Webhook URL.
3. **Drop the jar into your server's `plugins/` directory.**
4. **Start the server once.** This will create the default `plugins/UsageMonitor/config.yml` and shut down (or stay up, depending on your server setup).
5. **Edit `plugins/UsageMonitor/config.yml`.** Paste the webhook URL into the `discord.webhook-url` field. Save the file.
6. **Start (or restart) the server.** Within five seconds the plugin will post the initial embed in the channel and edit it on every subsequent cycle.

To upgrade, stop the server, replace the jar, and start. The persisted `discord.message-id` in `config.yml` is preserved, so the new jar will edit the same message the old one was editing. No re-setup required.

## Configuration

Everything is in `plugins/UsageMonitor/config.yml`. The file is heavily commented. The high-level structure is:

```yaml
discord:
  webhook-url: "https://discord.com/api/webhooks/..."   # your webhook URL
  message-id: ""                                        # auto-filled, do not edit

monitor:
  refresh-interval-seconds: 5                           # 5s = 12 edits/min, rate-limit safe
  embed-color: "#2B2D31"                                # accent color (dark grey default)
  progress-bar-length: 12                               # unused, kept for forward-compat
  show-resources: true
  show-configuration: true
  show-live-stats: true
  show-actions: true
  show-world-details: true
  emojis: {}                                            # see "Customizing the dashboard"
```

The `show-*` flags toggle individual sections of the embed. If you only care about TPS and player count, set the other four to `false` and the embed will be much shorter.

The `emojis` map is documented in the config file itself. By default it is empty, which means the embed has no emoji decoration. To add emojis back, fill in any of the 22 keys. Both unicode and Discord custom emoji are supported:

```yaml
emojis:
  resources: "📦"
  live-stats: "<:catpsstats:1234567890123456789>"   # Discord custom emoji
  players:   "<a:catpsplayers:9876543210987654321>" # Discord animated custom emoji
```

The plugin reads the webhook URL once on startup, splits it into the webhook ID and webhook token, and stores them in memory. It does not re-parse the URL on every cycle. If you change the URL, run `/usagemonitor reload` or restart the server.

## In-game commands

All commands require the `usagemonitor.admin` permission (granted to operators by default).

| Command                                | What it does                                                                |
|----------------------------------------|-----------------------------------------------------------------------------|
| `/usagemonitor status`                 | Print the current TPS, MSPT, JVM heap, disk, uptime, and webhook status.    |
| `/usagemonitor reload`                 | Reload `config.yml` and restart the update task.                            |
| `/usagemonitor forceupdate`            | Push an immediate refresh to Discord without waiting for the next cycle.    |
| `/usagemonitor reset`                  | Clear the saved message ID and post a fresh embed on the next cycle.        |
| `/usagemonitor help`                   | Show the in-game help text.                                                 |

Aliases: `/umon`, `/um`.

## How it is built

The project is a standard Maven layout. The build is `mvn package`, which produces a single fat-free jar at `target/UsageMonitor-<version>.jar`. The jar contains:

- The compiled classes under `com/usagemonitor/...`
- `plugin.yml` (Bukkit's plugin descriptor)
- `config.yml` (the default config, written to the plugin's data folder on first run)

The jar does **not** shade or include the Bukkit API. The Spigot API is declared as a `<scope>provided</scope>` dependency in `pom.xml`, so the plugin jar contains only the plugin's own classes. The server provides the Bukkit classes at runtime.

JSON is built with a hand-rolled zero-dependency builder (`com.usagemonitor.discord.JsonObjectBuilder`). It has methods for adding strings, numbers, booleans, nested objects, and lists, and produces a `String` in O(n) without reflection. There is also a `extractStringField` helper that pulls a top-level string value out of a JSON response without parsing the whole document — used to extract the message ID from the POST response.

The webhook is a single REST endpoint per cycle. There is no websocket, no gateway connection, no heartbeat, and no rate-limit tracking. The 12 edits per minute the default configuration produces is so far below the 30/60s and 5/5s limits that no backoff logic is needed. If you set the refresh interval very low, the plugin will still respect the 429 response and log a warning, but it will not back off automatically — you should set the interval to something reasonable.

The main-thread enforcement is implemented by submitting the snapshot collection to the Bukkit scheduler (`Bukkit.getScheduler().runTask(plugin, ...)`) from the async timer. The async timer then continues with the HTTP I/O, and the snapshot is handed off to it via a `CompletableFuture` that the async timer awaits. The first cycle is intentionally delayed by one second (20 ticks) so the dashboard appears quickly after server start without a visible "first paint" delay.

## Project layout

```
Usage-Monitor/
|-- pom.xml
|-- README.md
|-- LICENSE
|-- .gitignore
`-- src/
    `-- main/
        |-- java/
        |   `-- com/
        |       `-- usagemonitor/
        |           |-- UsageMonitorPlugin.java     # entry point, command handler
        |           |-- config/
        |           |   `-- PluginConfig.java      # loads + persists config.yml
        |           |-- discord/
        |           |   |-- DiscordClient.java      # webhook REST wrapper
        |           |   |-- DiscordPayloadBuilder.java  # builds the embed JSON
        |           |   |-- JsonObjectBuilder.java  # zero-dep JSON builder
        |           |   `-- model/
        |           |       `-- EmbedBuilder.java   # fluent embed builder
        |           |-- metrics/
        |           |   |-- ServerMetricsCollector.java  # bundles a snapshot
        |           |   |-- MemoryMetrics.java
        |           |   |-- CpuMetrics.java
        |           |   |-- DiskMetrics.java
        |           |   |-- NetworkAndPlayerMetrics.java
        |           |   |-- WorldMetrics.java
        |           |   `-- TpsTracker.java         # rolling tick deltas
        |           `-- task/
        |               `-- MonitorUpdateTask.java  # the timer + HTTP loop
        `-- resources/
            |-- plugin.yml
            `-- config.yml                          # defaults written on first run
```

## Customizing the dashboard

The easiest customization is editing `config.yml`. The most useful knobs:

- **Change the embed color.** Set `monitor.embed-color` to any 6-digit hex code (without the `#` is also accepted). A common choice is a brand color or a status-driven color; for example you can write a one-line shell script that edits the color to red if the TPS drops below 16, by reloading the plugin after editing the file.
- **Add or remove sections.** Toggle each `show-*` flag independently. The four-section layout (Resources + Configuration + Live Stats + Players + Worlds) is the most common. If you only want a minimal dashboard, disable everything except `show-live-stats` and `show-actions`.
- **Add emojis.** Set the keys in `monitor.emojis`. The default 22 keys are documented in the config file. To get a custom Discord emoji's `<:name:id>` form, type `\:emoji_name:` in any Discord chat and the client will autocomplete to the full string.
- **Change the refresh interval.** Set `monitor.refresh-interval-seconds`. Anything from 1 to 60 is safe. The plugin clamps the value to a minimum of 1 second, because anything below 1 second is below Discord's per-webhook edit granularity and would guarantee a rate limit.
- **Change the progress bar width.** The `progress-bar-length` field controls the inner bar character count. The default of 12 fits cleanly inside a Discord embed field on desktop and mobile.

For deeper changes — adding a new metric, changing the embed shape, or adding multiple webhooks — see [Extending the plugin](#extending-the-plugin) below.

## Extending the plugin

The codebase is structured to make new metrics and new embed sections a one-class change.

**Add a new metric.** Create a class in `com.usagemonitor.metrics` with a static `collect()` method that returns a small immutable record. The `Snapshot` record in `ServerMetricsCollector` already exposes `memory`, `cpu`, `disk`, `network`, and `worlds`; if your new metric is independent of the others, you can read it directly from the embed builder without going through the snapshot. If it is part of the same cycle (e.g. a "process file descriptor count" that you want sampled at the same instant as the others), add a field to `Snapshot` and a step to `ServerMetricsCollector.collectSnapshot()`.

**Add a new embed section.** In `DiscordPayloadBuilder.buildLiveDashboardPayload()`, add another `embed.addField(...)` call inside its own `if (config.isShow<YourSection>())` block. The `EmbedBuilder` is a fluent API; you can also add a `setAuthor()`, `setImage()`, or `setThumbnail()` if you want to attach an image.

**Support multiple webhooks.** The current `DiscordClient` is bound to one webhook ID and one token. To support multiple channels, change `DiscordClient` to take a list of `(id, token)` pairs and iterate over them in `MonitorUpdateTask.dispatch()`. The rate-limit math still holds: if you support N webhooks each at a 5-second interval, you produce N edits per 5 seconds, well under the 5 edits per 5 seconds per webhook limit (it is per-webhook, not per-bot).

**Compile and test.** `mvn clean package` from the project root. The output jar is at `target/UsageMonitor-<version>.jar`. Drop it into your server's `plugins/` folder, restart, and verify the dashboard appears within five seconds.

## Build from source

You need JDK 17+ and Maven 3.6+ on your path.

```bash
git clone https://github.com/ayliee/Usage-Monitor.git
cd Usage-Monitor
mvn clean package
```

The build is reproducible and does not require any network access at build time other than downloading the Spigot API from `hub.spigotmc.org` and Paper's repository at `repo.papermc.io`. The result is at `target/UsageMonitor-<version>.jar`.

To install into a running server:

```bash
cp target/UsageMonitor-*.jar /path/to/server/plugins/
```

Then restart the server, or `/reload confirm` if your server has the reload command enabled (note: a `/reload` will trigger a full plugin re-enable cycle, which is safe for this plugin but a hard restart is always recommended for production).

## Troubleshooting

**The embed never appears in the channel.**
Check the server console for a log line like `[Usage Monitor] Webhook not configured.` That means `discord.webhook-url` is empty or malformed. Edit `config.yml`, paste the full URL, and reload.

**The embed posts once then stops editing.**
The message was probably deleted in Discord, or the webhook was revoked. Run `/usagemonitor reset` in-game to clear the saved message ID. The next cycle will POST a fresh embed and resume editing it.

**The console shows `AsyncCatcher` warnings about `Chunk getEntities`.**
You are on an older version of the plugin (pre-1.0.1). Update to the latest jar.

**The console shows `Failed to edit message: HTTP 429`.**
You set the refresh interval too low. The default of 5 seconds is safe. Anything below 2 seconds risks rate limits.

**The console shows `Failed to edit message: HTTP 403`.**
The webhook was deleted or revoked in Discord. Create a new one, paste the URL into `config.yml`, and reload.

**The dashboard keeps posting new embeds instead of editing one.**
You are on an old version. The current code never auto-reposts; it always edits the persisted message ID. Update to the latest jar.

**The plugin loads but the command does nothing.**
You are not opped and do not have the `usagemonitor.admin` permission. Ops have it by default; non-ops need it granted explicitly.

## License

MIT. See [LICENSE](./LICENSE) for the full text.

Copyright (c) 2026 AeroX Dev
Code by Ayle (@alyfinnn)
