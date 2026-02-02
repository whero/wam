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
     * Handle player login - block non-trusted players during maintenance.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        
        // If maintenance is enabled, check if player is allowed
        if (maintenanceManager.isMaintenanceEnabled()) {
            // OPs are always allowed
            if (player.isOp()) {
                return;
            }
            
            // Check if player is in trusted list
            if (maintenanceManager.getTrustedPlayerUUIDs().contains(player.getUniqueId().toString())) {
                return;
            }
            
            // Block the connection
            String kickMessage = plugin.getConfig().getString("messages.maintenance-kick", 
                "Server is currently under maintenance. Please try again later.");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text(kickMessage));
        }
    }

    /**
     * Handle player join - disable maintenance when trusted player joins.
     */
    @EventHandler(priority = EventPriority.MONITOR)
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
     */
    @EventHandler(priority = EventPriority.MONITOR)
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
