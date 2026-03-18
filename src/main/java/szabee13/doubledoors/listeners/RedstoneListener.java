package szabee13.doubledoors.listeners;

import szabee13.doubledoors.DoubleDoors;
import szabee13.doubledoors.config.PluginConfig;
import szabee13.doubledoors.util.DoorUtil;
import szabee13.doubledoors.util.ProtectionCompat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.GameEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
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
import org.bukkit.event.world.GenericGameEvent;

/**
 * Handles redstone and villager-triggered door interactions.
 */
public final class RedstoneListener implements Listener {
  /** Ticks to wait before reading door state after a redstone change (vanilla needs 1 tick). */
  private static final long REDSTONE_DELAY_TICKS = 1L;
  /**
   * Ticks to wait before reading door state after a villager interaction.
   *
   * <p>Villager door AI runs asynchronously to the interaction event; using 2 ticks ensures
   * the block data has settled by the time we read and mirror it.</p>
   */
  private static final long VILLAGER_DELAY_TICKS = 2L;

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
    if (DoorInteractListener.isEnabledType(source.getType(), config)) {
      candidates.add(source);
    }

    // Some redstone changes are reported on the source block (for example a lever)
    // rather than directly on the openable, so inspect immediate neighbors too.
    for (BlockFace face : BlockFace.values()) {
      if (!face.isCartesian()) {
        continue;
      }

      Block neighbor = source.getRelative(face);
      if (DoorInteractListener.isEnabledType(neighbor.getType(), config)) {
        candidates.add(neighbor);
      }
    }

    for (Block candidate : candidates) {
      applyConnectedState(candidate, config, true, REDSTONE_DELAY_TICKS);
    }
  }

  /**
   * Handles villager interactions with supported door-like blocks.
   *
   * <p>Uses a 2-tick scheduling delay so the block state has fully settled after the
   * villager's pathfinding AI updates the door before we mirror it to the partner.</p>
   *
   * @param event the entity interaction event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVillagerInteract(EntityInteractEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof Villager)) {
      return;
    }

    Block block = normalizeOriginBlock(event.getBlock());
    PluginConfig config = plugin.getPluginConfig();
    if (!config.isServerWideEnabled() || !config.isEnableVillagerLinkedDoors()) {
      return;
    }
    if (!DoorInteractListener.isEnabledType(block.getType(), config)) {
      return;
    }

    long claimId = ProtectionCompat.getClaimIdAt(plugin, block);
    if (claimId >= 0 && plugin.getClaimSettings().isVillagersBlocked(claimId)) {
      return;
    }

    applyConnectedState(block, config, false, VILLAGER_DELAY_TICKS);
  }

  /**
   * Handles block-data changes caused by villagers (e.g. closing a door after pathfinding through).
   *
   * <p>This catches close events that are not reported as {@link EntityInteractEvent},
   * ensuring the partner block is kept in sync in both directions.</p>
   *
   * @param event the entity change block event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVillagerChangeBlock(EntityChangeBlockEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof Villager)) {
      return;
    }

    Block block = normalizeOriginBlock(event.getBlock());
    PluginConfig config = plugin.getPluginConfig();
    if (!config.isServerWideEnabled() || !config.isEnableVillagerLinkedDoors()) {
      return;
    }
    if (!DoorInteractListener.isEnabledType(block.getType(), config)) {
      return;
    }

    long claimId = ProtectionCompat.getClaimIdAt(plugin, block);
    if (claimId >= 0 && plugin.getClaimSettings().isVillagersBlocked(claimId)) {
      return;
    }

    applyConnectedState(block, config, false, VILLAGER_DELAY_TICKS);
  }

  /**
   * Handles game events to catch close actions natively from villagers.
   *
   * <p>Paper 1.21 no longer fires {@link EntityChangeBlockEvent} for villagers
   * closing doors, but game events are still correctly emitted.</p>
   *
   * @param event the game event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onGenericGameEvent(GenericGameEvent event) {
    if (event.getEvent() != GameEvent.BLOCK_CLOSE && event.getEvent() != GameEvent.BLOCK_OPEN) {
      return;
    }

    Entity entity = event.getEntity();
    if (!(entity instanceof Villager)) {
      return;
    }

    Block block = normalizeOriginBlock(event.getLocation().getBlock());
    PluginConfig config = plugin.getPluginConfig();
    if (!config.isServerWideEnabled() || !config.isEnableVillagerLinkedDoors()) {
      return;
    }
    if (!DoorInteractListener.isEnabledType(block.getType(), config)) {
      return;
    }

    long claimId = ProtectionCompat.getClaimIdAt(plugin, block);
    if (claimId >= 0 && plugin.getClaimSettings().isVillagersBlocked(claimId)) {
      return;
    }

    applyConnectedState(block, config, false, VILLAGER_DELAY_TICKS);
  }

  private void applyConnectedState(Block origin, PluginConfig config, boolean requireOriginStateChange, long delayTicks) {
    if (!config.isEnableRecursiveOpening()) {
      return;
    }

    BlockData beforeData = origin.getBlockData();
    if (!(beforeData instanceof Openable beforeOpenable)) {
      return;
    }
    boolean beforeState = beforeOpenable.isOpen();

    // Read and mirror state after the configured delay so we sync to vanilla's final result.
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
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

        if (linked.isOpen() == openState) {
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
        if (linked.isOpen() == openState) {
          continue;
        }

        linked.setOpen(openState);
        block.setBlockData(linked, false);
      }
    }, delayTicks);
  }

  private Block normalizeOriginBlock(Block block) {
    BlockData blockData = block.getBlockData();
    if (!(blockData instanceof Bisected bisected) || bisected.getHalf() != Half.TOP) {
      return block;
    }

    Block lower = block.getRelative(BlockFace.DOWN);
    if (!(lower.getBlockData() instanceof Door lowerDoor)) {
      return block;
    }

    return lowerDoor.getHalf() == Half.BOTTOM ? lower : block;
  }
}