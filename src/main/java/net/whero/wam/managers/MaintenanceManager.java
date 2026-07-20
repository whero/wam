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

    /** Permission that allows joining during maintenance without being OP or trusted. */
    public static final String BYPASS_PERMISSION = "wam.bypass";

    /** Valid range for the grace time, enforced for both commands and hand-edited configs. */
    public static final int MIN_GRACE_MINUTES = 1;
    public static final int MAX_GRACE_MINUTES = 1440; // 24 hours
    public static final int DEFAULT_GRACE_MINUTES = 15;

    private final WheroAnotherMaintenance plugin;
    private boolean maintenanceEnabled;
    private BukkitTask graceTimer;
    private long graceTimerEndTime; // When the grace timer will fire (for display purposes)
    private boolean startupGraceActive = true; // Until the post-startup check runs

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
            
            // Kick all players who may not bypass maintenance
            kickNonBypassPlayers();
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
     * Kick all players who may not bypass maintenance mode.
     */
    private void kickNonBypassPlayers() {
        String kickMessage = plugin.getConfig().getString("messages.kick-message", 
            "Server is now in maintenance mode. Please try again later.");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canBypassMaintenance(player)) {
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
     * Check if a player may bypass maintenance mode: OP, on the trusted list,
     * or holding the {@link #BYPASS_PERMISSION} permission.
     */
    public boolean canBypassMaintenance(Player player) {
        if (isPlayerTrusted(player)) {
            return true;
        }
        return player.hasPermission(BYPASS_PERMISSION);
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
     * Add a player to the trusted list. Any stale entries for the same name
     * (e.g. added under a different UUID) are replaced so names and UUIDs
     * can never desync into duplicate trust entries.
     */
    public boolean addTrustedPlayer(String playerName, UUID playerUUID) {
        List<String> uuids = new ArrayList<>(getTrustedPlayerUUIDs());
        List<String> names = new ArrayList<>(getTrustedPlayerNames());
        
        String uuidString = playerUUID.toString();
        if (uuids.contains(uuidString)) {
            return false; // Already trusted
        }
        
        removeEntriesForName(uuids, names, playerName);
        
        uuids.add(uuidString);
        names.add(playerName);
        
        plugin.getConfig().set("trusted-players", uuids);
        plugin.getConfig().set("trusted-player-names", names);
        plugin.saveConfig();
        
        return true;
    }

    /**
     * Remove a player from the trusted list by name. All matching entries are
     * removed so revocation is guaranteed even if the lists ever desynced.
     */
    public boolean removeTrustedPlayer(String playerName) {
        List<String> uuids = new ArrayList<>(getTrustedPlayerUUIDs());
        List<String> names = new ArrayList<>(getTrustedPlayerNames());
        
        if (!removeEntriesForName(uuids, names, playerName)) {
            return false; // Not found
        }
        
        plugin.getConfig().set("trusted-players", uuids);
        plugin.getConfig().set("trusted-player-names", names);
        plugin.saveConfig();
        
        return true;
    }

    /**
     * Remove every entry matching the given name (case-insensitive) from both
     * lists, keeping them index-aligned. Returns true if anything was removed.
     */
    private boolean removeEntriesForName(List<String> uuids, List<String> names, String playerName) {
        boolean removed = false;
        for (int i = names.size() - 1; i >= 0; i--) {
            if (names.get(i).equalsIgnoreCase(playerName)) {
                names.remove(i);
                if (i < uuids.size()) {
                    uuids.remove(i);
                }
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Get the grace time in minutes. Values outside the valid range (e.g. from a
     * hand-edited config) fall back to the default so the timer can never break.
     */
    public int getGraceTimeMinutes() {
        int minutes = plugin.getConfig().getInt("grace-time-minutes", DEFAULT_GRACE_MINUTES);
        if (minutes < MIN_GRACE_MINUTES || minutes > MAX_GRACE_MINUTES) {
            plugin.getLogger().warning("grace-time-minutes value " + minutes + " is out of range ("
                + MIN_GRACE_MINUTES + "-" + MAX_GRACE_MINUTES + "), using " + DEFAULT_GRACE_MINUTES + " instead.");
            return DEFAULT_GRACE_MINUTES;
        }
        return minutes;
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
     * Whether the post-startup grace window is still active. While it is active
     * and no trusted player is online, non-trusted players are refused at login
     * so a restart never opens the server to everyone.
     */
    public boolean isStartupGraceActive() {
        return startupGraceActive;
    }

    /**
     * End the startup grace window (called by the post-startup check).
     */
    public void clearStartupGrace() {
        startupGraceActive = false;
    }

    /**
     * Re-read persisted state after a config reload so runtime state stays in
     * sync with the file.
     */
    public void reload() {
        maintenanceEnabled = plugin.getConfig().getBoolean("maintenance-enabled", true);
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
