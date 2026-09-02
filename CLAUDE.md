# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WheroAnotherMaintenance (WAM) is a PaperMC 26.2 Minecraft server plugin (Kotlin, Java 25 toolchain) that automatically manages maintenance mode based on trusted player presence. When a trusted player (OP or explicitly added) joins, maintenance disables; when all trusted players leave, a configurable grace timer starts and then re-enables maintenance, kicking non-trusted players.

## Build Commands

```bash
./gradlew build              # Build JAR → build/libs/WheroAnotherMaintenance-<version>.jar
./gradlew clean build        # Clean rebuild
./gradlew test               # Run all tests
./gradlew test --tests "net.whero.wam.SomeTestClass"            # Single test class
./gradlew test --tests "net.whero.wam.SomeTestClass.testMethod" # Single test method
```

Tests go in `src/test/kotlin/net/whero/wam/`. No linter is configured; CodeQL runs via GitHub Actions.

## Architecture

Three-layer plugin architecture under `src/main/kotlin/net/whero/wam/`:

- **Entry point**: `WheroAnotherMaintenance` (extends JavaPlugin) — initializes managers, registers listeners and commands
- **Event layer** (`listeners/`): `PlayerConnectionListener` — handles PlayerJoin, PlayerQuit, PlayerLogin events to trigger maintenance state changes
- **Business logic** (`managers/`): `MaintenanceManager` — owns maintenance state, grace timer, trusted player list, config persistence
- **Command layer** (`commands/`): `WamCommand` (executor) + `WamTabCompleter` — handles `/wam` subcommands (status, add, del, list, gracetime, on, off, reload)

Key flow: trusted player joins → cancel grace timer → disable maintenance. Last trusted player leaves → start grace timer → enable maintenance + kick non-trusted.

## Code Style

- Kotlin on a Java 25 toolchain, base package `net.whero.wam`, subpackages: `commands`, `listeners`, `managers`
- 4-space indentation, braces on same line, ~120 char line limit
- Import order (blank lines between groups): project → third-party (Adventure) → Bukkit/Paper → Java stdlib
- Adventure API for text (`Component.text()`, `NamedTextColor`) — never legacy `ChatColor`
- Store player UUIDs, not names
- Never block main thread — use `runTaskAsynchronously` for I/O, `runTask` to return to main thread
- Config values always with fallback defaults: `plugin.config.getString("key") ?: "default"`
- Version placeholder in `plugin.yml`: `${version}` (expanded during build)

## Adding Features

**New command**: Add case to `when` in `WamCommand.onCommand()`, create `handle*()` method, update `sendUsage()` help text, add to `SUBCOMMANDS` in `WamTabCompleter`.

**New event listener**: Add `@EventHandler(priority = ...)` method in existing listener class (already registered in main plugin).

**New config option**: Add to `src/main/resources/config.yml` with comments, access via `plugin.config.get*()` with default value.

## Dependencies

- **PaperMC API** `26.2.build.62-beta` (compileOnly — provided at runtime)
- **Adventure API** — bundled with Paper, no separate dependency
- **Kotlin stdlib** — bundled into the jar by the Shadow plugin (`minimize()` strips unused classes); Paper does not provide it at runtime
