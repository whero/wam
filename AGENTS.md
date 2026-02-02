# AGENTS.md - AI Coding Agent Guidelines

Guidelines for AI coding agents working on WheroAnotherMaintenance (WAM) - a PaperMC 1.21.1 plugin that automatically manages server maintenance mode based on trusted player presence.

## Build Commands

```bash
# Build the plugin (outputs to build/libs/WheroAnotherMaintenance-<version>.jar)
./gradlew build

# Clean and rebuild
./gradlew clean build

# Just compile without packaging
./gradlew compileJava
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

**Note:** Tests go in `src/test/java/net/whero/wam/`.

## Project Structure

```
src/main/
├── java/net/whero/wam/
│   ├── WheroAnotherMaintenance.java  # Main plugin class (extends JavaPlugin)
│   ├── commands/                      # Command handlers
│   ├── listeners/                     # Event listeners
│   └── managers/                      # Business logic
└── resources/
    ├── plugin.yml                     # Plugin metadata
    └── config.yml                     # Default configuration
```

## Code Style Guidelines

### Java Version & Package
- **Java 21** (required by PaperMC 1.21.1)
- Base package: `net.whero.wam`
- Subpackages: `commands`, `listeners`, `managers`

### Import Order (with blank lines between groups)
1. Project imports (`net.whero.wam.*`)
2. Third-party imports (`net.kyori.adventure.*`)
3. Bukkit/Paper imports (`org.bukkit.*`)
4. Java standard library (`java.*`)

### Naming Conventions
- **Classes**: PascalCase (`MaintenanceManager`)
- **Methods**: camelCase, verb-first (`isPlayerTrusted`, `handleAdd`)
- **Constants**: UPPER_SNAKE_CASE (`SUBCOMMANDS`)
- **Fields**: camelCase with `final` where possible
- **Listeners**: `on<EventName>` pattern (`onPlayerJoin`)

### Formatting
- 4-space indentation (no tabs)
- Braces on same line as statement
- Max line length: ~120 characters
- Blank line between methods

### Documentation
- Javadoc (`/** */`) for public classes and methods
- Inline comments (`//`) for non-obvious logic

```java
/**
 * Check if a player is trusted (either OP or in the trusted players list).
 */
public boolean isPlayerTrusted(Player player) {
    if (player.isOp()) {
        return true;
    }
    return getTrustedPlayerUUIDs().contains(player.getUniqueId().toString());
}
```

### Error Handling
- Use early returns for validation
- Catch specific exceptions, not generic `Exception`
- Log errors with `plugin.getLogger()`
- User messages via Adventure API (`Component.text()`, `NamedTextColor`)

### Bukkit/Paper Specifics
- Use Adventure API for text (not legacy `ChatColor`)
- Never block main thread - use `runTaskAsynchronously` for I/O
- Return to main thread with `runTask` for Bukkit API calls
- Use `EventPriority.HIGHEST` for blocking, `MONITOR` for observation
- Store player UUIDs, not names (names can change)
- Config: `plugin.getConfig()` / `plugin.saveConfig()`

### Command Pattern
```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
        sendUsage(sender);
        return true;
    }
    switch (args[0].toLowerCase()) {
        case "subcommand" -> handleSubcommand(sender, args);
        default -> sendUsage(sender);
    }
    return true;
}
```

### Configuration
- Define defaults in `config.yml` with comments
- Always provide fallback values: `getConfig().getString("key", "default")`
- Version placeholder in plugin.yml: `${version}` (expanded during build)

## Dependencies

- **PaperMC API**: 1.21.1-R0.1-SNAPSHOT (compile-only)
- **Adventure API**: Bundled with Paper

## Common Tasks

### Adding a New Command
1. Add case to switch in `WamCommand.onCommand()`
2. Create `handleNewCommand()` method
3. Add to `sendUsage()` help text
4. Add to `SUBCOMMANDS` in `WamTabCompleter`

### Adding a New Event Listener
1. Create method with `@EventHandler(priority = EventPriority.X)`
2. Listener class is already registered in main plugin

### Adding Configuration Options
1. Add to `src/main/resources/config.yml` with comments
2. Access via `plugin.getConfig().get*()` with default value

## Testing the Plugin

1. `./gradlew build`
2. Copy JAR from `build/libs/` to server's `plugins/` folder
3. Restart PaperMC server
4. Test: `/wam status`, `/wam add <player>`, `/wam gracetime 5`
