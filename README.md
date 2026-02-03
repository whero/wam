# WheroAnotherMaintenance (WAM)

[![PaperMC](https://img.shields.io/badge/PaperMC-1.21.1-blue)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-GPL2-green)](LICENSE)

A smart maintenance mode plugin for PaperMC that automatically manages server access based on trusted player presence. No more forgetting to turn maintenance on or off!

⚠️  This plugin is only created for my own use case and YMMV. Use at your own risk. ⚠️
🪲 The code is created quickly using AI assistance, bugs do exist. Use at your own risk. 🪲

## How It Works

```
🟢 OP/Trusted player joins → Maintenance OFF (server open to all)
🔴 All OPs/Trusted leave → Grace timer starts → Maintenance ON
```

Unlike traditional maintenance plugins where you manually toggle maintenance mode, WAM automates this based on who's online:

- **Maintenance disables** instantly when an OP or trusted player connects
- **Maintenance enables** automatically after a configurable grace period (default: 15 minutes) when all trusted players disconnect
- Perfect for development servers, private servers, or any server that should only be "open" when admins are around

## Features

- 🔄 **Automatic Mode Switching** - No manual commands needed for day-to-day operation
- ⏱️ **Configurable Grace Period** - Give yourself time to reconnect without the server closing
- 👥 **Trusted Player List** - Add non-OP players who should also control maintenance state
- 🚫 **Connection Blocking** - Non-trusted players are kicked/blocked during maintenance
- 💬 **Customizable Messages** - Configure kick messages to your liking
- 🔧 **Manual Override** - Commands available when you need direct control

## Installation

1. Download the latest release from [Releases](../../releases)
2. Place `WheroAnotherMaintenance-x.x.x.jar` in your server's `plugins/` folder
3. Restart your server
4. (Optional) Configure `plugins/WheroAnotherMaintenance/config.yml`

**Requirements:**
- PaperMC 1.21.1 or compatible fork
- Java 21+

## Commands

All commands require the `wam.admin` permission (default: OP).

| Command | Description |
|---------|-------------|
| `/wam status` | Show current maintenance state and timer info |
| `/wam add <player>` | Add a player to the trusted list |
| `/wam del <player>` | Remove a player from the trusted list |
| `/wam list` | Show all trusted players |
| `/wam gracetime [minutes]` | View or set the grace period |
| `/wam on` | Manually enable maintenance |
| `/wam off` | Manually disable maintenance |
| `/wam reload` | Reload configuration |

**Aliases:** `/maintenance`, `/maint`

## Configuration

```yaml
# Whether maintenance mode is currently enabled
maintenance-enabled: true

# Grace time in minutes before maintenance enables after all trusted players leave
grace-time-minutes: 15

# Trusted players (managed via commands, but can be edited manually)
trusted-players: []
trusted-player-names: []

# Customizable messages
messages:
  prefix: "[WAM] "
  kick-message: "Server is now in maintenance mode. Please try again later."
  maintenance-kick: "Server is currently under maintenance. Please try again later."
```

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `wam.admin` | Access to all WAM commands | OP |

**Note:** Server OPs are always trusted automatically and don't need to be added to the trusted list.

## Use Cases

### Development Server
Keep your dev server closed to players except when developers are online testing.

### Private Server
Automatically lock down your server when admins aren't around to moderate.

### Event Server
Open the server only when event staff are present to manage things.

## Building from Source

```bash
# Clone the repository
git clone https://github.com/whero/whero-another-maintenance.git
cd whero-another-maintenance

# Build the plugin
./gradlew build

# Find the JAR in build/libs/
```

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

This project is open source. See [LICENSE](LICENSE) for details.

## Acknowledgments

Inspired by [kennytv/Maintenance](https://github.com/kennytv/Maintenance) - a fantastic maintenance plugin with more features for larger server networks. WAM focuses specifically on automatic maintenance toggling based on player presence.
