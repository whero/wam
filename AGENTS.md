# AGENTS.md - AI Coding Agent Guidelines

Guidelines for AI coding agents working on WheroAnotherMaintenance (WAM) - a PaperMC 26.2 plugin written in Kotlin that automatically manages server maintenance mode based on trusted player presence.

## Build Commands

```bash
# Build the plugin (outputs to build/libs/WheroAnotherMaintenance-<version>.jar)
./gradlew build

# Clean and rebuild
./gradlew clean build

# Just compile without packaging
./gradlew compileKotlin
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "net.whero.wam.SomeTestClass"

# Run a single test method
./gradlew test --tests "net.whero.wam.SomeTestClass.testMethodName"
```

**Note:** Tests go in `src/test/kotlin/net/whero/wam/`.

## Project Structure

```
src/main/
├── kotlin/net/whero/wam/
│   ├── WheroAnotherMaintenance.kt   # Main plugin class (extends JavaPlugin)
│   ├── commands/                    # Command handlers
│   ├── listeners/                   # Event listeners
│   └── managers/                    # Business logic
└── resources/
    ├── plugin.yml                   # Plugin metadata
    └── config.yml                   # Default configuration
```

## Code Style Guidelines

### Kotlin Version & Package
- **Kotlin 2.4** on a **Java 25** toolchain (required by PaperMC 26.2)
- Base package: `net.whero.wam`
- Subpackages: `commands`, `listeners`, `managers`

### Import Order (with blank lines between groups)
1. Project imports (`net.whero.wam.*`)
2. Third-party imports (`net.kyori.adventure.*`)
3. Bukkit/Paper imports (`org.bukkit.*`)
4. Java standard library (`java.*`)

### Naming Conventions
- **Classes**: PascalCase (`MaintenanceManager`)
- **Functions**: camelCase, verb-first (`isPlayerTrusted`, `handleAdd`)
- **Constants**: UPPER_SNAKE_CASE (`SUBCOMMANDS`) — `const val` in a `companion object`
- **Properties**: camelCase with `val` where possible
- **Listeners**: `on<EventName>` pattern (`onPlayerJoin`)

### Formatting
- 4-space indentation (no tabs)
- Max line length: ~120 characters
- Blank line between methods

### Documentation
- KDoc (`/** */`) for public classes and functions
- Inline comments (`//`) for non-obvious logic

```kotlin
/**
 * Check if a player is trusted (either OP or in the trusted players list).
 */
fun isPlayerTrusted(player: Player): Boolean {
    if (player.isOp) {
        return true
    }
    return trustedPlayerUUIDs.contains(player.uniqueId.toString())
}
```

### Error Handling
- Use early returns for validation
- Catch specific exceptions, not generic `Exception`
- Prefer null-safe idioms (`?.`, `?:`, `toIntOrNull()`) over try/catch for parsing
- Log errors with `plugin.logger`
- User messages via Adventure API (`Component.text()`, `NamedTextColor`)

### Bukkit/Paper Specifics
- Use Adventure API for text (not legacy `ChatColor`)
- Never block main thread - use `runTaskAsynchronously` for I/O
- Return to main thread with `runTask` for Bukkit API calls
- Wrap scheduler lambdas in `Runnable { }` — raw lambdas are ambiguous with Bukkit scheduler overloads
- Use `EventPriority.HIGHEST` for blocking, `MONITOR` for observation
- Store player UUIDs, not names (names can change)
- Config: `plugin.config` / `plugin.saveConfig()`
- Treat Bukkit API return types as nullable unless a default is supplied (`config.getString("key") ?: "default"`)

### Command Pattern
```kotlin
override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
    if (args.isEmpty()) {
        sendUsage(sender)
        return true
    }
    when (args[0].lowercase()) {
        "subcommand" -> handleSubcommand(sender, args)
        else -> sendUsage(sender)
    }
    return true
}
```

### Configuration
- Define defaults in `config.yml` with comments
- Always provide fallback values: `config.getString("key") ?: "default"`
- Version placeholder in plugin.yml: `${version}` (expanded during build)

## Dependencies

- **PaperMC API**: 26.2.build.62-beta (compile-only)
- **Adventure API**: Bundled with Paper
- **Kotlin stdlib**: Bundled into the jar by the Shadow plugin (`minimize()` strips unused classes); Paper does not provide it at runtime

## Common Tasks

### Adding a New Command
1. Add case to `when` in `WamCommand.onCommand()`
2. Create `handleNewCommand()` method
3. Add to `sendUsage()` help text
4. Add to `SUBCOMMANDS` in `WamTabCompleter`

### Adding a New Event Listener
1. Create method with `@EventHandler(priority = EventPriority.X)`
2. Listener class is already registered in main plugin

### Adding Configuration Options
1. Add to `src/main/resources/config.yml` with comments
2. Access via `plugin.config.get*()` with default value

## Testing the Plugin

1. `./gradlew build`
2. Copy JAR from `build/libs/` to server's `plugins/` folder
3. Restart PaperMC server
4. Test: `/wam status`, `/wam add <player>`, `/wam gracetime 5`
