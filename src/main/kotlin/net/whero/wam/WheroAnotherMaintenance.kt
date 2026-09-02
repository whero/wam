package net.whero.wam

import net.whero.wam.commands.WamCommand
import net.whero.wam.commands.WamTabCompleter
import net.whero.wam.listeners.PlayerConnectionListener
import net.whero.wam.managers.MaintenanceManager

import org.bukkit.plugin.java.JavaPlugin

/**
 * WheroAnotherMaintenance (WAM) - A PaperMC plugin for automatic maintenance mode management.
 *
 * Maintenance mode is automatically disabled when an OP or trusted player connects,
 * and automatically enabled after a configurable grace period when all OPs/trusted players disconnect.
 */
class WheroAnotherMaintenance : JavaPlugin() {

    lateinit var maintenanceManager: MaintenanceManager
        private set

    override fun onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig()

        // Initialize the maintenance manager
        maintenanceManager = MaintenanceManager(this)

        // Register event listeners
        server.pluginManager.registerEvents(PlayerConnectionListener(this, maintenanceManager), this)

        // Register commands
        val wamCommand = WamCommand(this, maintenanceManager)
        val wam = requireNotNull(getCommand("wam")) { "Command 'wam' is not registered in plugin.yml" }
        wam.setExecutor(wamCommand)
        wam.tabCompleter = WamTabCompleter(maintenanceManager)

        logger.info("WheroAnotherMaintenance has been enabled!")
        logger.info(
            "Maintenance mode is currently: " +
                if (maintenanceManager.isMaintenanceEnabled) "ENABLED" else "DISABLED"
        )

        // In offline mode, UUIDs are derived from usernames and are not authenticated,
        // so the trusted players list can be bypassed by name spoofing
        if (!server.onlineMode) {
            logger.warning("Server is running in OFFLINE mode: player UUIDs are not authenticated.")
            logger.warning(
                "The trusted players list can be bypassed by name spoofing. Use online mode or an authentication plugin."
            )
        }

        // Check after 30 seconds if no trusted players are online; if so, enable maintenance.
        // This handles the case where the server restarts while a trusted player was online
        // (maintenance was disabled) but no trusted player reconnects after the restart.
        // While this window is open, the login listener refuses non-trusted players so the
        // restart never opens the server to everyone.
        server.scheduler.runTaskLater(this, Runnable {
            try {
                if (!maintenanceManager.isMaintenanceEnabled && !maintenanceManager.hasAnyTrustedPlayerOnline()) {
                    logger.info("No trusted players online after startup — enabling maintenance mode.")
                    maintenanceManager.enableMaintenance()
                }
            } finally {
                maintenanceManager.clearStartupGrace()
            }
        }, 30 * 20L) // 30 seconds in ticks
    }

    override fun onDisable() {
        // Cancel any running timers
        if (::maintenanceManager.isInitialized) {
            maintenanceManager.cancelGraceTimer()
        }

        logger.info("WheroAnotherMaintenance has been disabled!")
    }
}
