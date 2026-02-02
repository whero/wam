package net.whero.wam;

import net.whero.wam.commands.WamCommand;
import net.whero.wam.commands.WamTabCompleter;
import net.whero.wam.listeners.PlayerConnectionListener;
import net.whero.wam.managers.MaintenanceManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WheroAnotherMaintenance (WAM) - A PaperMC plugin for automatic maintenance mode management.
 * 
 * Maintenance mode is automatically disabled when an OP or trusted player connects,
 * and automatically enabled after a configurable grace period when all OPs/trusted players disconnect.
 */
public class WheroAnotherMaintenance extends JavaPlugin {

    private MaintenanceManager maintenanceManager;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize the maintenance manager
        maintenanceManager = new MaintenanceManager(this);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(
            new PlayerConnectionListener(this, maintenanceManager), 
            this
        );
        
        // Register commands
        WamCommand wamCommand = new WamCommand(this, maintenanceManager);
        getCommand("wam").setExecutor(wamCommand);
        getCommand("wam").setTabCompleter(new WamTabCompleter(maintenanceManager));
        
        getLogger().info("WheroAnotherMaintenance has been enabled!");
        getLogger().info("Maintenance mode is currently: " + 
            (maintenanceManager.isMaintenanceEnabled() ? "ENABLED" : "DISABLED"));
    }

    @Override
    public void onDisable() {
        // Cancel any running timers
        if (maintenanceManager != null) {
            maintenanceManager.cancelGraceTimer();
        }
        
        getLogger().info("WheroAnotherMaintenance has been disabled!");
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }
}
