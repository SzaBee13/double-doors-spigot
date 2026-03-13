package szabee13.doubledoors.config;

import szabee13.doubledoors.DoubleDoors;

/**
 * Configuration wrapper for DoubleDoors.
 */
public final class PluginConfig {
  private final DoubleDoors plugin;

  private boolean enableRecursiveOpening;
  private int recursiveOpeningMaxBlocksDistance;
  private boolean enableDoors;
  private boolean enableFenceGates;
  private boolean enableTrapdoors;
  private boolean serverWideEnabled;

  /**
   * Creates and loads a plugin config wrapper.
   *
   * @param plugin the plugin instance
   */
  public PluginConfig(DoubleDoors plugin) {
    this.plugin = plugin;
    reload();
  }

  /**
   * Reloads config values from config.yml.
   */
  public void reload() {
    enableRecursiveOpening = plugin.getConfig().getBoolean("enableRecursiveOpening", true);
    recursiveOpeningMaxBlocksDistance = plugin.getConfig().getInt("recursiveOpeningMaxBlocksDistance", 10);
    if (recursiveOpeningMaxBlocksDistance < 1) {
      recursiveOpeningMaxBlocksDistance = 1;
    }
    if (recursiveOpeningMaxBlocksDistance > 32) {
      recursiveOpeningMaxBlocksDistance = 32;
    }

    enableDoors = plugin.getConfig().getBoolean("enableDoors", true);
    enableFenceGates = plugin.getConfig().getBoolean("enableFenceGates", true);
    enableTrapdoors = plugin.getConfig().getBoolean("enableTrapdoors", true);
    serverWideEnabled = plugin.getConfig().getBoolean("serverWideEnabled", true);
  }

  /**
   * Gets whether recursive opening is enabled.
   *
   * @return true when recursive opening is enabled
   */
  public boolean isEnableRecursiveOpening() {
    return enableRecursiveOpening;
  }

  /**
   * Gets the recursive max distance.
   *
   * @return max recursive distance between 1 and 32
   */
  public int getRecursiveOpeningMaxBlocksDistance() {
    return recursiveOpeningMaxBlocksDistance;
  }

  /**
   * Gets whether door support is enabled.
   *
   * @return true when door support is enabled
   */
  public boolean isEnableDoors() {
    return enableDoors;
  }

  /**
   * Gets whether fence gate support is enabled.
   *
   * @return true when fence gate support is enabled
   */
  public boolean isEnableFenceGates() {
    return enableFenceGates;
  }

  /**
   * Gets whether trapdoor support is enabled.
   *
   * @return true when trapdoor support is enabled
   */
  public boolean isEnableTrapdoors() {
    return enableTrapdoors;
  }

  /**
   * Gets whether linked opening is enabled globally for the server.
   *
   * @return true when server-wide behavior is enabled
   */
  public boolean isServerWideEnabled() {
    return serverWideEnabled;
  }

  /**
   * Sets and persists whether linked opening is enabled globally for the server.
   *
   * @param enabled true to enable globally, false to disable globally
   */
  public void setServerWideEnabled(boolean enabled) {
    serverWideEnabled = enabled;
    plugin.getConfig().set("serverWideEnabled", enabled);
    plugin.saveConfig();
  }
}
