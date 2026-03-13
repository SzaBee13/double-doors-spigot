package szabee13.doubledoors.listeners;

import szabee13.doubledoors.DoubleDoors;
import szabee13.doubledoors.config.PluginConfig;
import szabee13.doubledoors.util.DoorUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;

/**
 * Handles redstone and villager-triggered door interactions.
 */
public final class RedstoneListener implements Listener {
  private final DoubleDoors plugin;

  /**
   * Creates a new redstone/villager listener.
   *
   * @param plugin the plugin instance
   */
  public RedstoneListener(DoubleDoors plugin) {
    this.plugin = plugin;
  }

  /**
   * Handles redstone power changes for supported door-like blocks.
   *
   * @param event the redstone event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockRedstone(BlockRedstoneEvent event) {
    int oldCurrent = event.getOldCurrent();
    int newCurrent = event.getNewCurrent();
    boolean wasPowered = oldCurrent > 0;
    boolean nowPowered = newCurrent > 0;
    if (wasPowered == nowPowered) {
      return;
    }

    PluginConfig config = plugin.getPluginConfig();
    if (!config.isServerWideEnabled()) {
      return;
    }

    Block source = event.getBlock();

    Set<Block> candidates = new HashSet<>();
    if (isEnabledType(source.getType(), config)) {
      candidates.add(source);
    }

    // Some redstone changes are reported on the source block (for example a lever)
    // rather than directly on the openable, so inspect immediate neighbors too.
    for (BlockFace face : BlockFace.values()) {
      if (!face.isCartesian()) {
        continue;
      }

      Block neighbor = source.getRelative(face);
      if (isEnabledType(neighbor.getType(), config)) {
        candidates.add(neighbor);
      }
    }

    for (Block candidate : candidates) {
      applyConnectedState(candidate, config, true);
    }
  }

  /**
   * Handles villager interactions with supported door-like blocks.
   *
   * @param event the entity interaction event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVillagerInteract(EntityInteractEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof Villager)) {
      return;
    }

    Block block = event.getBlock();
    PluginConfig config = plugin.getPluginConfig();
    if (!config.isServerWideEnabled()) {
      return;
    }
    if (!isEnabledType(block.getType(), config)) {
      return;
    }

    applyConnectedState(block, config, false);
  }

  /**
   * Handles villager block changes for supported door-like blocks.
   *
   * @param event the entity change block event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVillagerChangeBlock(EntityChangeBlockEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof Villager)) {
      return;
    }

    Block block = event.getBlock();
    PluginConfig config = plugin.getPluginConfig();
    if (!config.isServerWideEnabled()) {
      return;
    }
    if (!isEnabledType(block.getType(), config)) {
      return;
    }

    applyConnectedState(block, config, false);
  }

  private void applyConnectedState(Block origin, PluginConfig config, boolean requireOriginStateChange) {
    if (!config.isEnableRecursiveOpening()) {
      return;
    }

    BlockData beforeData = origin.getBlockData();
    if (!(beforeData instanceof Openable beforeOpenable)) {
      return;
    }
    boolean beforeState = beforeOpenable.isOpen();

    // Read and mirror state next tick so we sync to vanilla's final result.
    plugin.getServer().getScheduler().runTask(plugin, () -> {
      BlockData originData = origin.getBlockData();
      if (!(originData instanceof Openable openable)) {
        return;
      }

      boolean openState = openable.isOpen();
      if (requireOriginStateChange && beforeState == openState) {
        return;
      }

      if (originData instanceof Door) {
        Block partner = DoorUtil.findMirroredDoubleDoorPartner(origin);
        if (partner == null) {
          return;
        }

        BlockData partnerData = partner.getBlockData();
        if (!(partnerData instanceof Openable linked)) {
          return;
        }

        linked.setOpen(openState);
        partner.setBlockData(linked, false);

        // Keep the upper half of the partner door in sync too.
        Block partnerTop = partner.getRelative(BlockFace.UP);
        BlockData topData = partnerTop.getBlockData();
        if (topData instanceof Openable topOpenable) {
          topOpenable.setOpen(openState);
          partnerTop.setBlockData(topData, false);
        }
        return;
      }

      Set<Block> connected = DoorUtil.findConnectedDoors(origin, config.getRecursiveOpeningMaxBlocksDistance());
      if (connected.isEmpty()) {
        return;
      }

      // Snapshot first to avoid any ordering effects while mutating a connected component.
      Map<Block, BlockData> snapshot = new HashMap<>();
      for (Block block : connected) {
        snapshot.put(block, block.getBlockData());
      }

      for (Map.Entry<Block, BlockData> entry : snapshot.entrySet()) {
        Block block = entry.getKey();
        BlockData data = entry.getValue();
        if (!(data instanceof Openable linked)) {
          continue;
        }

        linked.setOpen(openState);
        block.setBlockData(linked, false);
      }
    });
  }

  private boolean isEnabledType(Material material, PluginConfig config) {
    String name = material.name();
    if (name.endsWith("_DOOR")) {
      return config.isEnableDoors();
    }
    if (name.endsWith("_FENCE_GATE")) {
      return config.isEnableFenceGates();
    }
    if (name.endsWith("_TRAPDOOR")) {
      return config.isEnableTrapdoors();
    }
    return false;
  }
}
