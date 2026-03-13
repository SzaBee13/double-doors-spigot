package szabee13.doubledoors;

import szabee13.doubledoors.config.PluginConfig;
import szabee13.doubledoors.listeners.DoorInteractListener;
import szabee13.doubledoors.listeners.RedstoneListener;
import szabee13.doubledoors.util.ProtectionCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for DoubleDoors.
 */
public final class DoubleDoors extends JavaPlugin implements CommandExecutor, TabCompleter {
  private PluginConfig pluginConfig;
  private final Set<UUID> disabledPlayers = new HashSet<>();

  /**
   * Gets the plugin configuration wrapper.
   *
   * @return the active plugin config
   */
  public PluginConfig getPluginConfig() {
    return pluginConfig;
  }

  /**
   * Checks whether the player can interact with a linked door block according to
   * active protection plugins.
   *
   * @param player the interacting player
   * @param linkedBlock the linked block to toggle
   * @return true if interaction should be allowed
   */
  public boolean canOpenLinkedDoor(Player player, Block linkedBlock) {
    return ProtectionCompat.canOpenLinkedDoor(this, player, linkedBlock);
  }

  /**
   * Checks whether double-door logic is enabled for a given player.
   *
   * @param player the player to check
   * @return true if behavior is enabled for the player
   */
  public boolean isEnabledForPlayer(Player player) {
    return !disabledPlayers.contains(player.getUniqueId());
  }

  /**
   * Toggles double-door behavior for the given player.
   *
   * @param player the player to toggle
   * @return true if now enabled, false if now disabled
   */
  public boolean toggleForPlayer(Player player) {
    UUID id = player.getUniqueId();
    if (disabledPlayers.contains(id)) {
      disabledPlayers.remove(id);
      return true;
    }

    disabledPlayers.add(id);
    return false;
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    pluginConfig = new PluginConfig(this);

    getServer().getPluginManager().registerEvents(new DoorInteractListener(this), this);
    getServer().getPluginManager().registerEvents(new RedstoneListener(this), this);

    if (getCommand("doubledoors") != null) {
      getCommand("doubledoors").setExecutor(this);
      getCommand("doubledoors").setTabCompleter(this);
    }

    PluginManager pluginManager = getServer().getPluginManager();
    if (pluginManager.isPluginEnabled("LuckPerms")) {
      getLogger().info("LuckPerms detected: permissions handled via doubledoors.* nodes.");
    }
    if (pluginManager.isPluginEnabled("GriefPrevention")) {
      getLogger().info("GriefPrevention detected: linked door opens will respect claim build checks.");
    }
    if (pluginManager.isPluginEnabled("Geyser-Spigot") || pluginManager.isPluginEnabled("floodgate")) {
      getLogger().info("Geyser/Floodgate detected: duplicate interaction debounce is active.");
    }

    getLogger().info("DoubleDoors enabled.");
  }

  @Override
  public void onDisable() {
    getLogger().info("DoubleDoors disabled.");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("doubledoors")) {
      return false;
    }

    if (args.length == 0) {
      sender.sendMessage("Usage: /doubledoors <reload|toggle|server-toggle>");
      return true;
    }

    if (args[0].equalsIgnoreCase("reload")) {
      if (!sender.hasPermission("doubledoors.reload")) {
        sender.sendMessage("You do not have permission to use this command.");
        return true;
      }

      reloadConfig();
      pluginConfig.reload();
      sender.sendMessage("DoubleDoors config reloaded.");
      return true;
    }

    if (args[0].equalsIgnoreCase("toggle")) {
      if (!(sender instanceof Player player)) {
        sender.sendMessage("Only players can use /doubledoors toggle.");
        return true;
      }

      if (!sender.hasPermission("doubledoors.toggle")) {
        sender.sendMessage("You do not have permission to use this command.");
        return true;
      }

      boolean enabled = toggleForPlayer(player);
      sender.sendMessage(enabled ? "DoubleDoors enabled for you." : "DoubleDoors disabled for you.");
      return true;
    }

    if (args[0].equalsIgnoreCase("server-toggle")) {
      if (!sender.hasPermission("doubledoors.server-toggle")) {
        sender.sendMessage("You do not have permission to use this command.");
        return true;
      }

      boolean nextState = !pluginConfig.isServerWideEnabled();
      pluginConfig.setServerWideEnabled(nextState);
      sender.sendMessage(nextState
          ? "DoubleDoors server-wide behavior enabled."
          : "DoubleDoors server-wide behavior disabled.");
      return true;
    }

    sender.sendMessage("Usage: /doubledoors <reload|toggle|server-toggle>");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> completions = new ArrayList<>();
    if (!command.getName().equalsIgnoreCase("doubledoors")) {
      return completions;
    }

    if (args.length == 1) {
      if ("reload".startsWith(args[0].toLowerCase())) {
        completions.add("reload");
      }
      if ("toggle".startsWith(args[0].toLowerCase())) {
        completions.add("toggle");
      }
      if ("server-toggle".startsWith(args[0].toLowerCase())) {
        completions.add("server-toggle");
      }
    }
    return completions;
  }
}
