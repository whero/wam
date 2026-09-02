package net.whero.wam.commands

import net.whero.wam.WheroAnotherMaintenance
import net.whero.wam.managers.MaintenanceManager

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Command handler for /wam commands.
 */
class WamCommand(
    private val plugin: WheroAnotherMaintenance,
    private val maintenanceManager: MaintenanceManager,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "add" -> handleAdd(sender, args)
            "del", "delete", "remove" -> handleDel(sender, args)
            "list" -> handleList(sender)
            "gracetime", "grace" -> handleGraceTime(sender, args)
            "status" -> handleStatus(sender)
            "on" -> handleOn(sender)
            "off" -> handleOff(sender)
            "reload" -> handleReload(sender)
            else -> sendUsage(sender)
        }

        return true
    }

    private fun handleAdd(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /wam add <username>", NamedTextColor.RED))
            return
        }

        val playerName = args[1]

        // Try to get the player (online or offline)
        val onlinePlayer = Bukkit.getPlayer(playerName)

        if (onlinePlayer != null) {
            // Player is online, use their UUID directly
            if (maintenanceManager.addTrustedPlayer(onlinePlayer.name, onlinePlayer.uniqueId)) {
                sender.sendMessage(Component.text("Added ${onlinePlayer.name} to trusted players list.", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(Component.text("${onlinePlayer.name} is already in the trusted players list.", NamedTextColor.YELLOW))
            }
            return
        }

        // Player is offline. Only trust the server's cached profile: never store
        // name-derived "offline UUIDs", which never match real players on online-mode
        // servers and are trivially spoofable on offline-mode servers.
        // getOfflinePlayerIfCached is a non-blocking in-memory lookup, safe on the main thread.
        val cachedPlayer = Bukkit.getOfflinePlayerIfCached(playerName)
        if (cachedPlayer == null) {
            sender.sendMessage(Component.text("Player $playerName has never joined this server. They must join once before they can be trusted.", NamedTextColor.RED))
            return
        }

        val resolvedName = cachedPlayer.name ?: playerName
        if (maintenanceManager.addTrustedPlayer(resolvedName, cachedPlayer.uniqueId)) {
            sender.sendMessage(Component.text("Added $resolvedName to trusted players list.", NamedTextColor.GREEN))
        } else {
            sender.sendMessage(Component.text("$resolvedName is already in the trusted players list.", NamedTextColor.YELLOW))
        }
    }

    private fun handleDel(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /wam del <username>", NamedTextColor.RED))
            return
        }

        val playerName = args[1]

        if (maintenanceManager.removeTrustedPlayer(playerName)) {
            sender.sendMessage(Component.text("Removed $playerName from trusted players list.", NamedTextColor.GREEN))
        } else {
            sender.sendMessage(Component.text("$playerName is not in the trusted players list.", NamedTextColor.RED))
        }
    }

    private fun handleList(sender: CommandSender) {
        val trustedNames = maintenanceManager.trustedPlayerNames

        if (trustedNames.isEmpty()) {
            sender.sendMessage(Component.text("No trusted players configured. OPs are always trusted.", NamedTextColor.YELLOW))
            return
        }

        sender.sendMessage(Component.text("Trusted Players (${trustedNames.size}):", NamedTextColor.GOLD))
        for (name in trustedNames) {
            sender.sendMessage(Component.text("  - $name", NamedTextColor.WHITE))
        }
        sender.sendMessage(Component.text("Note: OPs are always trusted automatically.", NamedTextColor.GRAY))
    }

    private fun handleGraceTime(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            val currentGrace = maintenanceManager.graceTimeMinutes
            sender.sendMessage(Component.text("Current grace time: $currentGrace minutes", NamedTextColor.GOLD))
            sender.sendMessage(Component.text("Usage: /wam gracetime <minutes>", NamedTextColor.GRAY))
            return
        }

        val minutes = args[1].toIntOrNull()
        if (minutes == null) {
            sender.sendMessage(Component.text("Invalid number. Usage: /wam gracetime <minutes>", NamedTextColor.RED))
            return
        }
        if (minutes < MaintenanceManager.MIN_GRACE_MINUTES) {
            sender.sendMessage(Component.text("Grace time must be at least ${MaintenanceManager.MIN_GRACE_MINUTES} minute.", NamedTextColor.RED))
            return
        }
        if (minutes > MaintenanceManager.MAX_GRACE_MINUTES) {
            sender.sendMessage(Component.text("Grace time cannot exceed ${MaintenanceManager.MAX_GRACE_MINUTES} minutes (24 hours).", NamedTextColor.RED))
            return
        }

        maintenanceManager.setGraceTimeMinutes(minutes)
        sender.sendMessage(Component.text("Grace time set to $minutes minutes.", NamedTextColor.GREEN))
    }

    private fun handleStatus(sender: CommandSender) {
        val maintenanceEnabled = maintenanceManager.isMaintenanceEnabled
        val timerRunning = maintenanceManager.isGraceTimerRunning
        val graceMinutes = maintenanceManager.graceTimeMinutes

        sender.sendMessage(Component.text("=== WAM Status ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text(
            "Maintenance Mode: " + if (maintenanceEnabled) "ENABLED" else "DISABLED",
            if (maintenanceEnabled) NamedTextColor.RED else NamedTextColor.GREEN
        ))
        sender.sendMessage(Component.text("Grace Time: $graceMinutes minutes", NamedTextColor.WHITE))

        if (timerRunning) {
            val remainingSeconds = maintenanceManager.graceTimerRemainingSeconds
            val remainingMinutes = remainingSeconds / 60
            val remainingSecs = remainingSeconds % 60
            sender.sendMessage(Component.text("Grace Timer: RUNNING (${remainingMinutes}m ${remainingSecs}s remaining)", NamedTextColor.YELLOW))
        } else {
            sender.sendMessage(Component.text("Grace Timer: Not running", NamedTextColor.GRAY))
        }

        val trustedCount = maintenanceManager.trustedPlayerNames.size
        sender.sendMessage(Component.text("Trusted Players: $trustedCount (+ OPs)", NamedTextColor.WHITE))

        val hasTrustedOnline = maintenanceManager.hasAnyTrustedPlayerOnline()
        sender.sendMessage(Component.text(
            "Trusted Online: " + if (hasTrustedOnline) "Yes" else "No",
            if (hasTrustedOnline) NamedTextColor.GREEN else NamedTextColor.GRAY
        ))
    }

    private fun handleOn(sender: CommandSender) {
        if (maintenanceManager.isMaintenanceEnabled) {
            sender.sendMessage(Component.text("Maintenance mode is already enabled.", NamedTextColor.YELLOW))
            return
        }

        maintenanceManager.cancelGraceTimer() // Cancel any running timer
        maintenanceManager.enableMaintenance()
        sender.sendMessage(Component.text("Maintenance mode has been manually enabled.", NamedTextColor.GREEN))
    }

    private fun handleOff(sender: CommandSender) {
        if (!maintenanceManager.isMaintenanceEnabled) {
            sender.sendMessage(Component.text("Maintenance mode is already disabled.", NamedTextColor.YELLOW))
            return
        }

        maintenanceManager.cancelGraceTimer() // Cancel any running timer
        maintenanceManager.disableMaintenance()
        sender.sendMessage(Component.text("Maintenance mode has been manually disabled.", NamedTextColor.GREEN))
    }

    private fun handleReload(sender: CommandSender) {
        plugin.reloadConfig()
        maintenanceManager.reload() // Re-sync in-memory state with the file
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN))
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("=== WheroAnotherMaintenance Commands ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/wam add <username>", NamedTextColor.WHITE).append(Component.text(" - Add a trusted player", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam del <username>", NamedTextColor.WHITE).append(Component.text(" - Remove a trusted player", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam list", NamedTextColor.WHITE).append(Component.text(" - List trusted players", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam gracetime [minutes]", NamedTextColor.WHITE).append(Component.text(" - View/set grace time", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam status", NamedTextColor.WHITE).append(Component.text(" - Show current status", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam on", NamedTextColor.WHITE).append(Component.text(" - Manually enable maintenance", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam off", NamedTextColor.WHITE).append(Component.text(" - Manually disable maintenance", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/wam reload", NamedTextColor.WHITE).append(Component.text(" - Reload configuration", NamedTextColor.GRAY)))
    }
}
