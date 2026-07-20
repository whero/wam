package net.whero.wam.listeners;

import net.whero.wam.WheroAnotherMaintenance;
import net.whero.wam.managers.MaintenanceManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player connection events to manage maintenance mode automatically.
 */
public class PlayerConnectionListener implements Listener {

    private final WheroAnotherMaintenance plugin;
    private final MaintenanceManager maintenanceManager;

    public PlayerConnectionListener(WheroAnotherMaintenance plugin, MaintenanceManager maintenanceManager) {
        this.plugin = plugin;
        this.maintenanceManager = maintenanceManager;
    }

    /**
     * Handle player login - block players who may not bypass maintenance.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        
        // OPs, trusted players and players with the bypass permission are always allowed
        if (maintenanceManager.canBypassMaintenance(player)) {
            return;
        }
        
        // During the post-startup grace window (maintenance persisted as disabled, but no
        // trusted player has reconnected yet), refuse everyone else so a restart never
        // opens the server to the public
        boolean startupLock = maintenanceManager.isStartupGraceActive()
            && !maintenanceManager.hasAnyTrustedPlayerOnline();
        
        if (maintenanceManager.isMaintenanceEnabled() || startupLock) {
            String kickMessage = plugin.getConfig().getString("messages.maintenance-kick", 
                "Server is currently under maintenance. Please try again later.");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text(kickMessage));
        }
    }

    /**
     * Handle player join - disable maintenance when trusted player joins.
     * Runs at HIGHEST since it mutates plugin state (MONITOR must be observe-only).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if this is a trusted player
        if (maintenanceManager.isPlayerTrusted(player)) {
            // Cancel any running grace timer
            if (maintenanceManager.isGraceTimerRunning()) {
                maintenanceManager.cancelGraceTimer();
                maintenanceManager.broadcastToOps("Grace timer cancelled - trusted player joined.");
            }
            
            // Disable maintenance mode if it's enabled
            if (maintenanceManager.isMaintenanceEnabled()) {
                maintenanceManager.disableMaintenance();
                maintenanceManager.broadcastToOps("Maintenance mode disabled - " + player.getName() + " joined.");
            }
        }
    }

    /**
     * Handle player quit - start grace timer if no trusted players remain.
     * Runs at HIGHEST since it mutates plugin state (MONITOR must be observe-only).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Only care if a trusted player is leaving
        if (!maintenanceManager.isPlayerTrusted(player)) {
            return;
        }
        
        // Check if maintenance is already enabled or timer is running
        if (maintenanceManager.isMaintenanceEnabled() || maintenanceManager.isGraceTimerRunning()) {
            return;
        }
        
        // Schedule a check for after this player has fully left
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Check if any trusted players are still online
            if (!maintenanceManager.hasAnyTrustedPlayerOnline()) {
                // Start the grace timer
                maintenanceManager.startGraceTimer();
                maintenanceManager.broadcastToOps("All trusted players have left. Grace timer started (" + 
                    maintenanceManager.getGraceTimeMinutes() + " minutes).");
            }
        }, 1L); // Run 1 tick later to ensure the player has fully disconnected
    }
}
