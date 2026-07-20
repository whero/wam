package net.whero.wam.commands;

import net.whero.wam.WheroAnotherMaintenance;
import net.whero.wam.managers.MaintenanceManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command handler for /wam commands.
 */
public class WamCommand implements CommandExecutor {

    private final WheroAnotherMaintenance plugin;
    private final MaintenanceManager maintenanceManager;

    public WamCommand(WheroAnotherMaintenance plugin, MaintenanceManager maintenanceManager) {
        this.plugin = plugin;
        this.maintenanceManager = maintenanceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                handleAdd(sender, args);
                break;
            case "del":
            case "delete":
            case "remove":
                handleDel(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "gracetime":
            case "grace":
                handleGraceTime(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "on":
                handleOn(sender);
                break;
            case "off":
                handleOff(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /wam add <username>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        
        // Try to get the player (online or offline)
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        
        if (onlinePlayer != null) {
            // Player is online, use their UUID directly
            if (maintenanceManager.addTrustedPlayer(onlinePlayer.getName(), onlinePlayer.getUniqueId())) {
                sender.sendMessage(Component.text("Added " + onlinePlayer.getName() + " to trusted players list.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text(onlinePlayer.getName() + " is already in the trusted players list.", NamedTextColor.YELLOW));
            }
            return;
        }
        
        // Player is offline. Only trust the server's cached profile: never store
        // name-derived "offline UUIDs", which never match real players on online-mode
        // servers and are trivially spoofable on offline-mode servers.
        // getOfflinePlayerIfCached is a non-blocking in-memory lookup, safe on the main thread.
        var cachedPlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cachedPlayer == null) {
            sender.sendMessage(Component.text("Player " + playerName + " has never joined this server. They must join once before they can be trusted.", NamedTextColor.RED));
            return;
        }
        
        String resolvedName = cachedPlayer.getName() != null ? cachedPlayer.getName() : playerName;
        if (maintenanceManager.addTrustedPlayer(resolvedName, cachedPlayer.getUniqueId())) {
            sender.sendMessage(Component.text("Added " + resolvedName + " to trusted players list.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(resolvedName + " is already in the trusted players list.", NamedTextColor.YELLOW));
        }
    }

    private void handleDel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /wam del <username>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        
        if (maintenanceManager.removeTrustedPlayer(playerName)) {
            sender.sendMessage(Component.text("Removed " + playerName + " from trusted players list.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(playerName + " is not in the trusted players list.", NamedTextColor.RED));
        }
    }

    private void handleList(CommandSender sender) {
        List<String> trustedNames = maintenanceManager.getTrustedPlayerNames();
        
        if (trustedNames.isEmpty()) {
            sender.sendMessage(Component.text("No trusted players configured. OPs are always trusted.", NamedTextColor.YELLOW));
            return;
        }
        
        sender.sendMessage(Component.text("Trusted Players (" + trustedNames.size() + "):", NamedTextColor.GOLD));
        for (String name : trustedNames) {
            sender.sendMessage(Component.text("  - " + name, NamedTextColor.WHITE));
        }
        sender.sendMessage(Component.text("Note: OPs are always trusted automatically.", NamedTextColor.GRAY));
    }

    private void handleGraceTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            int currentGrace = maintenanceManager.getGraceTimeMinutes();
            sender.sendMessage(Component.text("Current grace time: " + currentGrace + " minutes", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Usage: /wam gracetime <minutes>", NamedTextColor.GRAY));
            return;
        }

        try {
            int minutes = Integer.parseInt(args[1]);
            if (minutes < MaintenanceManager.MIN_GRACE_MINUTES) {
                sender.sendMessage(Component.text("Grace time must be at least " + MaintenanceManager.MIN_GRACE_MINUTES + " minute.", NamedTextColor.RED));
                return;
            }
            if (minutes > MaintenanceManager.MAX_GRACE_MINUTES) {
                sender.sendMessage(Component.text("Grace time cannot exceed " + MaintenanceManager.MAX_GRACE_MINUTES + " minutes (24 hours).", NamedTextColor.RED));
                return;
            }
            
            maintenanceManager.setGraceTimeMinutes(minutes);
            sender.sendMessage(Component.text("Grace time set to " + minutes + " minutes.", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number. Usage: /wam gracetime <minutes>", NamedTextColor.RED));
        }
    }

    private void handleStatus(CommandSender sender) {
        boolean maintenanceEnabled = maintenanceManager.isMaintenanceEnabled();
        boolean timerRunning = maintenanceManager.isGraceTimerRunning();
        int graceMinutes = maintenanceManager.getGraceTimeMinutes();
        
        sender.sendMessage(Component.text("=== WAM Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Maintenance Mode: " + (maintenanceEnabled ? "ENABLED" : "DISABLED"), 
            maintenanceEnabled ? NamedTextColor.RED : NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Grace Time: " + graceMinutes + " minutes", NamedTextColor.WHITE));
        
        if (timerRunning) {
            long remainingSeconds = maintenanceManager.getGraceTimerRemainingSeconds();
            long remainingMinutes = remainingSeconds / 60;
            long remainingSecs = remainingSeconds % 60;
            sender.sendMessage(Component.text("Grace Timer: RUNNING (" + remainingMinutes + "m " + remainingSecs + "s remaining)", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Grace Timer: Not running", NamedTextColor.GRAY));
        }
        
        int trustedCount = maintenanceManager.getTrustedPlayerNames().size();
        sender.sendMessage(Component.text("Trusted Players: " + trustedCount + " (+ OPs)", NamedTextColor.WHITE));
        
        boolean hasTrustedOnline = maintenanceManager.hasAnyTrustedPlayerOnline();
        sender.sendMessage(Component.text("Trusted Online: " + (hasTrustedOnline ? "Yes" : "No"), 
            hasTrustedOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    private void handleOn(CommandSender sender) {
        if (maintenanceManager.isMaintenanceEnabled()) {
            sender.sendMessage(Component.text("Maintenance mode is already enabled.", NamedTextColor.YELLOW));
            return;
        }
        
        maintenanceManager.cancelGraceTimer(); // Cancel any running timer
        maintenanceManager.enableMaintenance();
        sender.sendMessage(Component.text("Maintenance mode has been manually enabled.", NamedTextColor.GREEN));
    }

    private void handleOff(CommandSender sender) {
        if (!maintenanceManager.isMaintenanceEnabled()) {
            sender.sendMessage(Component.text("Maintenance mode is already disabled.", NamedTextColor.YELLOW));
            return;
        }
        
        maintenanceManager.cancelGraceTimer(); // Cancel any running timer
        maintenanceManager.disableMaintenance();
        sender.sendMessage(Component.text("Maintenance mode has been manually disabled.", NamedTextColor.GREEN));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        maintenanceManager.reload(); // Re-sync in-memory state with the file
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== WheroAnotherMaintenance Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/wam add <username>", NamedTextColor.WHITE).append(Component.text(" - Add a trusted player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam del <username>", NamedTextColor.WHITE).append(Component.text(" - Remove a trusted player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam list", NamedTextColor.WHITE).append(Component.text(" - List trusted players", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam gracetime [minutes]", NamedTextColor.WHITE).append(Component.text(" - View/set grace time", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam status", NamedTextColor.WHITE).append(Component.text(" - Show current status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam on", NamedTextColor.WHITE).append(Component.text(" - Manually enable maintenance", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam off", NamedTextColor.WHITE).append(Component.text(" - Manually disable maintenance", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wam reload", NamedTextColor.WHITE).append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
    }
}
