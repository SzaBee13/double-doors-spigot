package me.szabee.doubledoors.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.YamlConfiguration;

import me.szabee.doubledoors.DoubleDoors;
import me.szabee.doubledoors.storage.SharedSqlStorage;
import me.szabee.doubledoors.util.SchedulerBridge;

/**
 * Manages per-GriefPrevention-claim settings, persisted to {@code claims.yml}.
 *
 * <p>Currently tracks which claims have opted out of villager linked-door behavior.</p>
 */
public final class ClaimSettings {

  private final DoubleDoors plugin;
  private final File dataFile;
  private final SharedSqlStorage sqlStorage;
  private final boolean useSql;
  private final Set<Long> villagersBlockedClaims = ConcurrentHashMap.newKeySet();

  /**
   * Loads claim settings from {@code claims.yml}.
   *
   * @param plugin the plugin instance
   */
  public ClaimSettings(DoubleDoors plugin) {
    this.plugin = plugin;
    this.dataFile = new File(plugin.getDataFolder(), "claims.yml");
    this.sqlStorage = plugin.getSqlStorage();
    this.useSql = plugin.getPluginConfig().isSqlEnabled() && sqlStorage != null;
    load();
  }

  /**
   * Reloads all claim settings from disk, clearing in-memory state.
   */
  public void load() {
    if (useSql) {
      villagersBlockedClaims.clear();
      villagersBlockedClaims.addAll(sqlStorage.loadVillagersBlockedClaims());
      return;
    }

    YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
    villagersBlockedClaims.clear();
    List<?> blocked = data.getList("villagersBlocked");
    if (blocked != null) {
      for (Object entry : blocked) {
        if (entry instanceof Number n) {
          villagersBlockedClaims.add(n.longValue());
        }
      }
    }
  }

  /**
   * Saves all claim settings synchronously to {@code claims.yml}.
   */
  public void save() {
    if (useSql) {
      return;
    }

    YamlConfiguration data = new YamlConfiguration();
    data.set("villagersBlocked", List.copyOf(villagersBlockedClaims));
    try {
      data.save(dataFile);
    } catch (IOException e) {
      plugin.getLogger().warning("Could not save claims.yml: %s".formatted(e.getMessage()));
    }
  }

  /**
   * Saves asynchronously; safe to call from the main thread after every mutation.
   */
  public void saveAsync() {
    SchedulerBridge.runAsync(plugin, this::save);
  }

  private void saveAsync(long claimId, boolean blocked) {
    SchedulerBridge.runAsync(plugin, () -> {
      if (useSql) {
        sqlStorage.setVillagersBlocked(claimId, blocked);
        return;
      }
      save();
    });
  }

  /**
   * Returns whether villager linked-door openings are blocked for the given claim.
   *
   * @param claimId the GriefPrevention claim ID
   * @return true if villagers are blocked in this claim
   */
  public boolean isVillagersBlocked(long claimId) {
    return villagersBlockedClaims.contains(claimId);
  }

  /**
   * Toggles villager linked-door access for the given claim.
   *
   * @param claimId the GriefPrevention claim ID
   * @return true if villagers are now blocked, false if now allowed
   */
  public boolean toggleVillagersBlocked(long claimId) {
    if (villagersBlockedClaims.remove(claimId)) {
      saveAsync(claimId, false);
      return false;
    }
    villagersBlockedClaims.add(claimId);
    saveAsync(claimId, true);
    return true;
  }
}

