package net.whero.wam.commands

import net.whero.wam.managers.MaintenanceManager

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Tab completion handler for /wam commands.
 */
class WamTabCompleter(private val maintenanceManager: MaintenanceManager) : TabCompleter {

    companion object {
        private val SUBCOMMANDS = listOf(
            "add", "del", "list", "gracetime", "status", "on", "off", "reload"
        )
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (args.size == 1) {
            return filterStartsWith(SUBCOMMANDS, args[0])
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                // Suggest online players
                "add" -> return filterStartsWith(Bukkit.getOnlinePlayers().map { it.name }, args[1])

                // Suggest trusted players
                "del", "delete", "remove" -> return filterStartsWith(maintenanceManager.trustedPlayerNames, args[1])

                // Suggest common time values
                "gracetime", "grace" -> return filterStartsWith(listOf("5", "10", "15", "30", "60"), args[1])
            }
        }

        return emptyList()
    }

    private fun filterStartsWith(options: List<String>, input: String): List<String> {
        val lowerInput = input.lowercase()
        return options.filter { it.lowercase().startsWith(lowerInput) }
    }
}
