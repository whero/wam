package net.whero.wam.managers

import net.whero.wam.WheroAnotherMaintenance

import net.kyori.adventure.text.Component

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask

import java.util.UUID

/**
 * Manages the maintenance mode state, trusted players list, and grace timer.
 */
class MaintenanceManager(private val plugin: WheroAnotherMaintenance) {

    companion object {
        /** Permission that allows joining during maintenance without being OP or trusted. */
        const val BYPASS_PERMISSION = "wam.bypass"

        /** Valid range for the grace time, enforced for both commands and hand-edited configs. */
        const val MIN_GRACE_MINUTES = 1
        const val MAX_GRACE_MINUTES = 1440 // 24 hours
        const val DEFAULT_GRACE_MINUTES = 15
    }

    /** Whether maintenance mode is currently enabled. */
    var isMaintenanceEnabled = plugin.config.getBoolean("maintenance-enabled", true)
        private set

    init {
        // Fail closed if a hand-edited config desynced the two parallel lists:
        // an orphan UUID would be a trust entry invisible to /wam list
        reconcileTrustedLists()
    }

    private var graceTimer: BukkitTask? = null
    private var graceTimerEndTime: Long = 0 // When the grace timer will fire (for display purposes)

    /**
     * Whether the post-startup grace window is still active. While it is active
     * and no trusted player is online, non-trusted players are refused at login
     * so a restart never opens the server to everyone.
     */
    var isStartupGraceActive = true // Until the post-startup check runs
        private set

    /**
     * Enable maintenance mode.
     */
    fun enableMaintenance() {
        if (!isMaintenanceEnabled) {
            isMaintenanceEnabled = true
            plugin.config.set("maintenance-enabled", true)
            plugin.saveConfig()
            plugin.logger.info("Maintenance mode has been ENABLED")

            // Kick all players who may not bypass maintenance
            kickNonBypassPlayers()
        }
    }

    /**
     * Disable maintenance mode.
     */
    fun disableMaintenance() {
        if (isMaintenanceEnabled) {
            isMaintenanceEnabled = false
            plugin.config.set("maintenance-enabled", false)
            plugin.saveConfig()
            plugin.logger.info("Maintenance mode has been DISABLED")
        }
    }

    /**
     * Kick all players who may not bypass maintenance mode.
     */
    private fun kickNonBypassPlayers() {
        val kickMessage = plugin.config.getString("messages.kick-message")
            ?: "Server is now in maintenance mode. Please try again later."

        for (player in Bukkit.getOnlinePlayers()) {
            if (!canBypassMaintenance(player)) {
                player.kick(Component.text(kickMessage))
            }
        }
    }

    /**
     * Check if a player is trusted (either OP or in the trusted players list).
     */
    fun isPlayerTrusted(player: Player): Boolean {
        if (player.isOp) {
            return true
        }
        return trustedPlayerUUIDs.contains(player.uniqueId.toString())
    }

    /**
     * Check if a player may bypass maintenance mode: OP, on the trusted list,
     * or holding the [BYPASS_PERMISSION] permission.
     */
    fun canBypassMaintenance(player: Player): Boolean {
        if (isPlayerTrusted(player)) {
            return true
        }
        return player.hasPermission(BYPASS_PERMISSION)
    }

    /** The list of trusted player UUIDs from config. */
    val trustedPlayerUUIDs: List<String>
        get() = plugin.config.getStringList("trusted-players")

    /** The list of trusted player names from config. */
    val trustedPlayerNames: List<String>
        get() = plugin.config.getStringList("trusted-player-names")

    /**
     * Add a player to the trusted list. Any stale entries for the same name
     * (e.g. added under a different UUID) are replaced so names and UUIDs
     * can never desync into duplicate trust entries.
     */
    fun addTrustedPlayer(playerName: String, playerUUID: UUID): Boolean {
        val uuids = trustedPlayerUUIDs.toMutableList()
        val names = trustedPlayerNames.toMutableList()

        val uuidString = playerUUID.toString()
        if (uuids.contains(uuidString)) {
            return false // Already trusted
        }

        removeEntriesForName(uuids, names, playerName)

        uuids.add(uuidString)
        names.add(playerName)

        plugin.config.set("trusted-players", uuids)
        plugin.config.set("trusted-player-names", names)
        plugin.saveConfig()

        return true
    }

    /**
     * Remove a player from the trusted list by name. All matching entries are
     * removed so revocation is guaranteed even if the lists ever desynced.
     */
    fun removeTrustedPlayer(playerName: String): Boolean {
        val uuids = trustedPlayerUUIDs.toMutableList()
        val names = trustedPlayerNames.toMutableList()

        if (!removeEntriesForName(uuids, names, playerName)) {
            return false // Not found
        }

        plugin.config.set("trusted-players", uuids)
        plugin.config.set("trusted-player-names", names)
        plugin.saveConfig()

        return true
    }

    /**
     * Remove a player from the trusted list by UUID. The index-aligned name
     * entry is removed as well. Returns the removed name (or the UUID string
     * if the name entry was missing), or null if the UUID was not trusted.
     *
     * This is the only reliable way to revoke trust for a player who renamed:
     * their new name no longer matches the stored name entry.
     */
    fun removeTrustedPlayerByUuid(playerUUID: UUID): String? {
        val uuids = trustedPlayerUUIDs.toMutableList()
        val names = trustedPlayerNames.toMutableList()

        val index = uuids.indexOf(playerUUID.toString())
        if (index < 0) {
            return null
        }

        uuids.removeAt(index)
        val removedName = if (index < names.size) names.removeAt(index) else playerUUID.toString()

        plugin.config.set("trusted-players", uuids)
        plugin.config.set("trusted-player-names", names)
        plugin.saveConfig()

        return removedName
    }

    /**
     * Keep the two parallel trusted-player lists index-aligned. If a hand-edited
     * config made their lengths differ, both are truncated to the shorter length
     * (fail closed: an orphan UUID would be a trust entry invisible to /wam list).
     */
    private fun reconcileTrustedLists() {
        val uuids = trustedPlayerUUIDs.toMutableList()
        val names = trustedPlayerNames.toMutableList()
        if (uuids.size == names.size) {
            return
        }

        val commonSize = minOf(uuids.size, names.size)
        plugin.logger.warning(
            "trusted-players (${uuids.size}) and trusted-player-names (${names.size}) are out of sync; " +
                "keeping only the first $commonSize aligned entries. Check your config."
        )
        while (uuids.size > commonSize) uuids.removeAt(uuids.size - 1)
        while (names.size > commonSize) names.removeAt(names.size - 1)

        plugin.config.set("trusted-players", uuids)
        plugin.config.set("trusted-player-names", names)
        plugin.saveConfig()
    }

    /**
     * Remove every entry matching the given name (case-insensitive) from both
     * lists, keeping them index-aligned. Returns true if anything was removed.
     */
    private fun removeEntriesForName(uuids: MutableList<String>, names: MutableList<String>, playerName: String): Boolean {
        var removed = false
        for (i in names.indices.reversed()) {
            if (names[i].equals(playerName, ignoreCase = true)) {
                names.removeAt(i)
                if (i < uuids.size) {
                    uuids.removeAt(i)
                }
                removed = true
            }
        }
        return removed
    }

    /**
     * The grace time in minutes. Values outside the valid range (e.g. from a
     * hand-edited config) fall back to the default so the timer can never break.
     */
    val graceTimeMinutes: Int
        get() {
            val minutes = plugin.config.getInt("grace-time-minutes", DEFAULT_GRACE_MINUTES)
            if (minutes < MIN_GRACE_MINUTES || minutes > MAX_GRACE_MINUTES) {
                plugin.logger.warning(
                    "grace-time-minutes value $minutes is out of range " +
                        "($MIN_GRACE_MINUTES-$MAX_GRACE_MINUTES), using $DEFAULT_GRACE_MINUTES instead."
                )
                return DEFAULT_GRACE_MINUTES
            }
            return minutes
        }

    /**
     * Set the grace time in minutes.
     */
    fun setGraceTimeMinutes(minutes: Int) {
        plugin.config.set("grace-time-minutes", minutes)
        plugin.saveConfig()
    }

    /**
     * Start the grace timer to enable maintenance mode after the configured time.
     */
    fun startGraceTimer() {
        cancelGraceTimer() // Cancel any existing timer

        val graceMinutes = graceTimeMinutes
        val graceTicks = graceMinutes * 60L * 20L // Convert minutes to ticks (20 ticks = 1 second)

        graceTimerEndTime = System.currentTimeMillis() + graceMinutes * 60L * 1000L

        plugin.logger.info("Starting grace timer. Maintenance will be enabled in $graceMinutes minutes.")

        graceTimer = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Check again if there are any trusted players online before enabling
            if (!hasAnyTrustedPlayerOnline()) {
                enableMaintenance()
                broadcastToOps("Maintenance mode has been automatically enabled after grace period.")
            }
            graceTimer = null
            graceTimerEndTime = 0
        }, graceTicks)
    }

    /**
     * Cancel the grace timer if it's running.
     */
    fun cancelGraceTimer() {
        graceTimer?.let {
            it.cancel()
            graceTimer = null
            graceTimerEndTime = 0
            plugin.logger.info("Grace timer cancelled.")
        }
    }

    /**
     * Check if the grace timer is currently running.
     */
    val isGraceTimerRunning: Boolean
        get() = graceTimer != null

    /**
     * The remaining time on the grace timer in seconds.
     */
    val graceTimerRemainingSeconds: Long
        get() {
            if (graceTimerEndTime == 0L) {
                return 0
            }
            val remaining = graceTimerEndTime - System.currentTimeMillis()
            return maxOf(0L, remaining / 1000)
        }

    /**
     * Check if any trusted player (OP or in trusted list) is currently online.
     */
    fun hasAnyTrustedPlayerOnline(): Boolean {
        return Bukkit.getOnlinePlayers().any { isPlayerTrusted(it) }
    }

    /**
     * End the startup grace window (called by the post-startup check).
     */
    fun clearStartupGrace() {
        isStartupGraceActive = false
    }

    /**
     * Re-read persisted state after a config reload so runtime state stays in
     * sync with the file. If maintenance was switched on by hand-editing the
     * config, enforce it immediately (kick players who may not bypass).
     */
    fun reload() {
        val wasEnabled = isMaintenanceEnabled
        isMaintenanceEnabled = plugin.config.getBoolean("maintenance-enabled", true)
        reconcileTrustedLists()

        if (isMaintenanceEnabled) {
            // A running grace timer is meaningless while maintenance is on; cancel it
            // so it cannot fire later with a misleading "automatically enabled" message
            cancelGraceTimer()
            if (!wasEnabled) {
                plugin.logger.info("Config reload enabled maintenance mode — kicking non-bypass players.")
                kickNonBypassPlayers()
            }
        }
    }

    /**
     * Broadcast a message to all online OPs.
     */
    fun broadcastToOps(message: String) {
        val prefix = plugin.config.getString("messages.prefix") ?: "[WAM] "
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.isOp) {
                player.sendMessage(Component.text(prefix + message))
            }
        }
    }
}
