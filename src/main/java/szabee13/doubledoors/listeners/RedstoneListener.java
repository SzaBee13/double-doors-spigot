package szabee13.doubledoors.listeners;

import szabee13.doubledoors.DoubleDoors;
import szabee13.doubledoors.config.PluginConfig;
import szabee13.doubledoors.util.DoorUtil;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
    boolean isPowered = newCurrent > 0;
    if (wasPowered == isPowered) {
      return;
    }

    Block block = event.getBlock();
    PluginConfig config = plugin.getPluginConfig();
    if (!isEnabledType(block.getType(), config)) {
      return;
    }

    applyConnectedState(block, config, isPowered);
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
    if (!isEnabledType(block.getType(), config)) {
      return;
    }

    applyConnectedState(block, config, null);
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
    if (!isEnabledType(block.getType(), config)) {
      return;
    }

    applyConnectedState(block, config, null);
  }

  private void applyConnectedState(Block origin, PluginConfig config, Boolean explicitOpenState) {
    if (!config.isEnableRecursiveOpening()) {
      return;
    }

    BlockData originData = origin.getBlockData();
    if (!(originData instanceof Openable openable)) {
      return;
    }

    boolean openState = explicitOpenState != null ? explicitOpenState : !openable.isOpen();

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
      return;
    }

    Set<Block> connected = DoorUtil.findConnectedDoors(origin, config.getRecursiveOpeningMaxBlocksDistance());

    for (Block block : connected) {
      BlockData data = block.getBlockData();
      if (!(data instanceof Openable linked)) {
        continue;
      }

      linked.setOpen(openState);
      block.setBlockData(linked, false);
    }
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
