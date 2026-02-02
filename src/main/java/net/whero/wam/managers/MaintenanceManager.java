package net.whero.wam.managers;

import net.whero.wam.WheroAnotherMaintenance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the maintenance mode state, trusted players list, and grace timer.
 */
public class MaintenanceManager {

    private final WheroAnotherMaintenance plugin;
    private boolean maintenanceEnabled;
    private BukkitTask graceTimer;
    private long graceTimerEndTime; // When the grace timer will fire (for display purposes)

    public MaintenanceManager(WheroAnotherMaintenance plugin) {
        this.plugin = plugin;
        this.maintenanceEnabled = plugin.getConfig().getBoolean("maintenance-enabled", true);
    }

    /**
     * Check if maintenance mode is currently enabled.
     */
    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    /**
     * Enable maintenance mode.
     */
    public void enableMaintenance() {
        if (!maintenanceEnabled) {
            maintenanceEnabled = true;
            plugin.getConfig().set("maintenance-enabled", true);
            plugin.saveConfig();
            plugin.getLogger().info("Maintenance mode has been ENABLED");
            
            // Kick all non-trusted, non-OP players
            kickNonTrustedPlayers();
        }
    }

    /**
     * Disable maintenance mode.
     */
    public void disableMaintenance() {
        if (maintenanceEnabled) {
            maintenanceEnabled = false;
            plugin.getConfig().set("maintenance-enabled", false);
            plugin.saveConfig();
            plugin.getLogger().info("Maintenance mode has been DISABLED");
        }
    }

    /**
     * Kick all players who are not OPs or trusted.
     */
    private void kickNonTrustedPlayers() {
        String kickMessage = plugin.getConfig().getString("messages.kick-message", 
            "Server is now in maintenance mode. Please try again later.");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPlayerTrusted(player)) {
                player.kick(net.kyori.adventure.text.Component.text(kickMessage));
            }
        }
    }

    /**
     * Check if a player is trusted (either OP or in the trusted players list).
     */
    public boolean isPlayerTrusted(Player player) {
        if (player.isOp()) {
            return true;
        }
        return getTrustedPlayerUUIDs().contains(player.getUniqueId().toString());
    }

    /**
     * Get the list of trusted player UUIDs from config.
     */
    public List<String> getTrustedPlayerUUIDs() {
        return plugin.getConfig().getStringList("trusted-players");
    }

    /**
     * Get the list of trusted player names from config.
     */
    public List<String> getTrustedPlayerNames() {
        return plugin.getConfig().getStringList("trusted-player-names");
    }

    /**
     * Add a player to the trusted list.
     */
    public boolean addTrustedPlayer(String playerName, UUID playerUUID) {
        List<String> uuids = new ArrayList<>(getTrustedPlayerUUIDs());
        List<String> names = new ArrayList<>(getTrustedPlayerNames());
        
        String uuidString = playerUUID.toString();
        if (uuids.contains(uuidString)) {
            return false; // Already trusted
        }
        
        uuids.add(uuidString);
        names.add(playerName);
        
        plugin.getConfig().set("trusted-players", uuids);
        plugin.getConfig().set("trusted-player-names", names);
        plugin.saveConfig();
        
        return true;
    }

    /**
     * Remove a player from the trusted list by name.
     */
    public boolean removeTrustedPlayer(String playerName) {
        List<String> uuids = new ArrayList<>(getTrustedPlayerUUIDs());
        List<String> names = new ArrayList<>(getTrustedPlayerNames());
        
        int index = -1;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(playerName)) {
                index = i;
                break;
            }
        }
        
        if (index == -1) {
            return false; // Not found
        }
        
        if (index < uuids.size()) {
            uuids.remove(index);
        }
        names.remove(index);
        
        plugin.getConfig().set("trusted-players", uuids);
        plugin.getConfig().set("trusted-player-names", names);
        plugin.saveConfig();
        
        return true;
    }

    /**
     * Get the grace time in minutes.
     */
    public int getGraceTimeMinutes() {
        return plugin.getConfig().getInt("grace-time-minutes", 15);
    }

    /**
     * Set the grace time in minutes.
     */
    public void setGraceTimeMinutes(int minutes) {
        plugin.getConfig().set("grace-time-minutes", minutes);
        plugin.saveConfig();
    }

    /**
     * Start the grace timer to enable maintenance mode after the configured time.
     */
    public void startGraceTimer() {
        cancelGraceTimer(); // Cancel any existing timer
        
        int graceMinutes = getGraceTimeMinutes();
        long graceTicks = graceMinutes * 60L * 20L; // Convert minutes to ticks (20 ticks = 1 second)
        
        graceTimerEndTime = System.currentTimeMillis() + (graceMinutes * 60L * 1000L);
        
        plugin.getLogger().info("Starting grace timer. Maintenance will be enabled in " + graceMinutes + " minutes.");
        
        graceTimer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Check again if there are any trusted players online before enabling
            if (!hasAnyTrustedPlayerOnline()) {
                enableMaintenance();
                broadcastToOps("Maintenance mode has been automatically enabled after grace period.");
            }
            graceTimer = null;
            graceTimerEndTime = 0;
        }, graceTicks);
    }

    /**
     * Cancel the grace timer if it's running.
     */
    public void cancelGraceTimer() {
        if (graceTimer != null) {
            graceTimer.cancel();
            graceTimer = null;
            graceTimerEndTime = 0;
            plugin.getLogger().info("Grace timer cancelled.");
        }
    }

    /**
     * Check if the grace timer is currently running.
     */
    public boolean isGraceTimerRunning() {
        return graceTimer != null;
    }

    /**
     * Get the remaining time on the grace timer in seconds.
     */
    public long getGraceTimerRemainingSeconds() {
        if (graceTimerEndTime == 0) {
            return 0;
        }
        long remaining = graceTimerEndTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    /**
     * Check if any trusted player (OP or in trusted list) is currently online.
     */
    public boolean hasAnyTrustedPlayerOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerTrusted(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Broadcast a message to all online OPs.
     */
    public void broadcastToOps(String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "[WAM] ");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(net.kyori.adventure.text.Component.text(prefix + message));
            }
        }
    }
}
