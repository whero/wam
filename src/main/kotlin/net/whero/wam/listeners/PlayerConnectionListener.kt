package net.whero.wam.listeners

import net.whero.wam.WheroAnotherMaintenance
import net.whero.wam.managers.MaintenanceManager

import net.kyori.adventure.text.Component

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Handles player connection events to manage maintenance mode automatically.
 */
class PlayerConnectionListener(
    private val plugin: WheroAnotherMaintenance,
    private val maintenanceManager: MaintenanceManager,
) : Listener {

    /**
     * Handle player login - block players who may not bypass maintenance.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player

        // OPs, trusted players and players with the bypass permission are always allowed
        if (maintenanceManager.canBypassMaintenance(player)) {
            return
        }

        if (shouldDenyAccess()) {
            val kickMessage = plugin.config.getString("messages.maintenance-kick")
                ?: "Server is currently under maintenance. Please try again later."
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text(kickMessage))
        }
    }

    /**
     * Whether players who may not bypass must be denied access right now:
     * maintenance is on, or the post-startup grace window is still active
     * with no trusted player online (a restart must never open the server
     * to the public).
     */
    private fun shouldDenyAccess(): Boolean {
        return maintenanceManager.isMaintenanceEnabled ||
            (maintenanceManager.isStartupGraceActive && !maintenanceManager.hasAnyTrustedPlayerOnline())
    }

    /**
     * Handle player join - disable maintenance when trusted player joins.
     * Runs at HIGHEST since it mutates plugin state (MONITOR must be observe-only).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Backstop: another plugin registered at the same priority could have
        // overridden the login check with event.allow(). Re-validate here so a
        // denied player never actually enters the world.
        if (shouldDenyAccess() && !maintenanceManager.canBypassMaintenance(player)) {
            plugin.logger.warning(
                "Kicking ${player.name}: reached join while access is denied " +
                    "(another plugin may have overridden the login check)."
            )
            val kickMessage = plugin.config.getString("messages.kick-message")
                ?: "Server is now in maintenance mode. Please try again later."
            player.kick(Component.text(kickMessage))
            return
        }

        // Check if this is a trusted player
        if (maintenanceManager.isPlayerTrusted(player)) {
            // Cancel any running grace timer
            if (maintenanceManager.isGraceTimerRunning) {
                maintenanceManager.cancelGraceTimer()
                maintenanceManager.broadcastToOps("Grace timer cancelled - trusted player joined.")
            }

            // Disable maintenance mode if it's enabled
            if (maintenanceManager.isMaintenanceEnabled) {
                maintenanceManager.disableMaintenance()
                maintenanceManager.broadcastToOps("Maintenance mode disabled - ${player.name} joined.")
            }
        }
    }

    /**
     * Handle player quit - start grace timer if no trusted players remain.
     * Runs at HIGHEST since it mutates plugin state (MONITOR must be observe-only).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Only care if a trusted player is leaving
        if (!maintenanceManager.isPlayerTrusted(player)) {
            return
        }

        // Check if maintenance is already enabled or timer is running
        if (maintenanceManager.isMaintenanceEnabled || maintenanceManager.isGraceTimerRunning) {
            return
        }

        // Schedule a check for after this player has fully left
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // Check if any trusted players are still online
            if (!maintenanceManager.hasAnyTrustedPlayerOnline()) {
                // Start the grace timer
                maintenanceManager.startGraceTimer()
                maintenanceManager.broadcastToOps(
                    "All trusted players have left. Grace timer started (${maintenanceManager.graceTimeMinutes} minutes)."
                )
            }
        }, 1L) // Run 1 tick later to ensure the player has fully disconnected
    }
}
