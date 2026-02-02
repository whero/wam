package net.whero.wam.commands;

import net.whero.wam.managers.MaintenanceManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completion handler for /wam commands.
 */
public class WamTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "add", "del", "list", "gracetime", "status", "on", "off", "reload"
    );

    private final MaintenanceManager maintenanceManager;

    public WamTabCompleter(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "add":
                    // Suggest online players
                    return filterStartsWith(
                        Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()),
                        args[1]
                    );
                    
                case "del":
                case "delete":
                case "remove":
                    // Suggest trusted players
                    return filterStartsWith(maintenanceManager.getTrustedPlayerNames(), args[1]);
                    
                case "gracetime":
                case "grace":
                    // Suggest common time values
                    return filterStartsWith(Arrays.asList("5", "10", "15", "30", "60"), args[1]);
            }
        }

        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> options, String input) {
        String lowerInput = input.toLowerCase();
        return options.stream()
            .filter(option -> option.toLowerCase().startsWith(lowerInput))
            .collect(Collectors.toList());
    }
}
