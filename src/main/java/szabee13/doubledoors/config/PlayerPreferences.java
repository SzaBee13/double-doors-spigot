package szabee13.doubledoors.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import szabee13.doubledoors.DoubleDoors;

/**
 * Manages per-player preferences, persisted to {@code players.yml} inside the plugin data folder.
 *
 * <p>Each player can independently enable/disable linked-opening for the plugin overall,
 * or for specific block types (doors, fence gates, trapdoors).</p>
 */
public final class PlayerPreferences {

  private final DoubleDoors plugin;
  private final File dataFile;
  private YamlConfiguration data;
  private final Map<UUID, PlayerPref> cache = new HashMap<>();

  /**
   * Loads player preferences from {@code players.yml}.
   *
   * @param plugin the plugin instance
   */
  public PlayerPreferences(DoubleDoors plugin) {
    this.plugin = plugin;
    this.dataFile = new File(plugin.getDataFolder(), "players.yml");
    load();
  }

  /**
   * Reloads all preferences from disk, clearing the in-memory cache.
   */
  public void load() {
    data = YamlConfiguration.loadConfiguration(dataFile);
    cache.clear();
    for (String key : data.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(key);
        boolean enabled = data.getBoolean(key + ".enabled", true);
        boolean doors = data.getBoolean(key + ".enableDoors", true);
        boolean gates = data.getBoolean(key + ".enableFenceGates", true);
        boolean trapdoors = data.getBoolean(key + ".enableTrapdoors", true);
        cache.put(uuid, new PlayerPref(enabled, doors, gates, trapdoors));
      } catch (IllegalArgumentException ignored) {
        // Non-UUID top-level key — skip silently.
      }
    }
  }

  /**
   * Saves all in-memory preferences synchronously to {@code players.yml}.
   */
  public void save() {
    for (Map.Entry<UUID, PlayerPref> entry : cache.entrySet()) {
      String key = entry.getKey().toString();
      PlayerPref pref = entry.getValue();
      data.set(key + ".enabled", pref.enabled());
      data.set(key + ".enableDoors", pref.enableDoors());
      data.set(key + ".enableFenceGates", pref.enableFenceGates());
      data.set(key + ".enableTrapdoors", pref.enableTrapdoors());
    }
    try {
      data.save(dataFile);
    } catch (IOException e) {
      plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
    }
  }

  /** Saves asynchronously; safe to call from the main thread after every mutation. */
  private void saveAsync() {
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
  }

  private PlayerPref getOrDefault(UUID uuid) {
    return cache.computeIfAbsent(uuid, k -> new PlayerPref(true, true, true, true));
  }

  /** Returns whether the player has not globally disabled linked-door behavior. */
  public boolean isEnabled(UUID uuid) {
    return getOrDefault(uuid).enabled();
  }

  /** Returns whether the player has doors linking enabled. */
  public boolean isDoorsEnabled(UUID uuid) {
    return getOrDefault(uuid).enableDoors();
  }

  /** Returns whether the player has fence-gate linking enabled. */
  public boolean isFenceGatesEnabled(UUID uuid) {
    return getOrDefault(uuid).enableFenceGates();
  }

  /** Returns whether the player has trapdoor linking enabled. */
  public boolean isTrapdoorsEnabled(UUID uuid) {
    return getOrDefault(uuid).enableTrapdoors();
  }

  /**
   * Toggles the player's global linked-door on/off switch.
   *
   * @param uuid the player's unique ID
   * @return {@code true} if the feature is now enabled, {@code false} if now disabled
   */
  public boolean toggleAll(UUID uuid) {
    PlayerPref current = getOrDefault(uuid);
    boolean next = !current.enabled();
    cache.put(uuid, new PlayerPref(next, current.enableDoors(), current.enableFenceGates(), current.enableTrapdoors()));
    saveAsync();
    return next;
  }

  /**
   * Toggles the door-linking preference for the given player.
   *
   * @param uuid the player's unique ID
   * @return the new enabled state
   */
  public boolean toggleDoors(UUID uuid) {
    PlayerPref current = getOrDefault(uuid);
    boolean next = !current.enableDoors();
    cache.put(uuid, new PlayerPref(current.enabled(), next, current.enableFenceGates(), current.enableTrapdoors()));
    saveAsync();
    return next;
  }

  /**
   * Toggles the fence-gate-linking preference for the given player.
   *
   * @param uuid the player's unique ID
   * @return the new enabled state
   */
  public boolean toggleFenceGates(UUID uuid) {
    PlayerPref current = getOrDefault(uuid);
    boolean next = !current.enableFenceGates();
    cache.put(uuid, new PlayerPref(current.enabled(), current.enableDoors(), next, current.enableTrapdoors()));
    saveAsync();
    return next;
  }

  /**
   * Toggles the trapdoor-linking preference for the given player.
   *
   * @param uuid the player's unique ID
   * @return the new enabled state
   */
  public boolean toggleTrapdoors(UUID uuid) {
    PlayerPref current = getOrDefault(uuid);
    boolean next = !current.enableTrapdoors();
    cache.put(uuid, new PlayerPref(current.enabled(), current.enableDoors(), current.enableFenceGates(), next));
    saveAsync();
    return next;
  }

  /**
   * Immutable snapshot of a single player's preferences.
   *
   * @param enabled          global linked-door on/off
   * @param enableDoors      door-linking on/off
   * @param enableFenceGates fence-gate-linking on/off
   * @param enableTrapdoors  trapdoor-linking on/off
   */
  public record PlayerPref(
      boolean enabled,
      boolean enableDoors,
      boolean enableFenceGates,
      boolean enableTrapdoors
  ) {}
}
