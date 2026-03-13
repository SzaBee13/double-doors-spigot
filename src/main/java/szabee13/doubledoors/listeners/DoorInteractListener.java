package szabee13.doubledoors.listeners;

import szabee13.doubledoors.DoubleDoors;
import szabee13.doubledoors.config.PluginConfig;
import szabee13.doubledoors.util.DoorUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Handles player interactions with doors, gates, and trapdoors.
 */
public final class DoorInteractListener implements Listener {
  private static final long DUPLICATE_INTERACTION_WINDOW_NANOS = 80_000_000L;

  private final DoubleDoors plugin;
  private final Map<UUID, InteractionStamp> lastInteractionByPlayer = new HashMap<>();

  /**
   * Creates a new interaction listener.
   *
   * @param plugin the plugin instance
   */
  public DoorInteractListener(DoubleDoors plugin) {
    this.plugin = plugin;
  }

  /**
   * Handles right-click block interactions for door-like blocks.
   *
   * @param event the interact event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Block clicked = event.getClickedBlock();
    if (clicked == null) {
      return;
    }

    Player player = event.getPlayer();
    PluginConfig config = plugin.getPluginConfig();

    if (!config.isServerWideEnabled()) {
      return;
    }

    if (!plugin.isEnabledForPlayer(player)) {
      return;
    }
    if (!player.hasPermission("doubledoors.use")) {
      return;
    }
    if (player.isSneaking()) {
      return;
    }
    if (!isEnabledType(clicked.getType(), config)) {
      return;
    }
    if (isDuplicateInteraction(player, clicked)) {
      return;
    }

    applyConnectedState(player, clicked, config);
  }

  private void applyConnectedState(Player player, Block origin, PluginConfig config) {
    if (!config.isEnableRecursiveOpening()) {
      return;
    }
    if (!(origin.getBlockData() instanceof Openable)) {
      return;
    }

    // Schedule for the next tick so we read the state AFTER vanilla has processed the
    // click. Reading it here (at MONITOR) would give the pre-click state on Paper 1.21,
    // causing the partner/connected blocks to be set to the wrong state.
    plugin.getServer().getScheduler().runTask(plugin, () -> {
      BlockData originData = origin.getBlockData();
      if (!(originData instanceof Openable openable)) {
        return;
      }

      boolean openState = openable.isOpen();

      if (originData instanceof Door) {
        Block partner = DoorUtil.findMirroredDoubleDoorPartner(origin);
        if (partner == null) {
          return;
        }

        BlockData partnerData = partner.getBlockData();
        if (!(partnerData instanceof Openable linked)) {
          return;
        }
        if (!plugin.canOpenLinkedDoor(player, partner)) {
          return;
        }

        linked.setOpen(openState);
        partner.setBlockData(linked, false);

        // Doors are two blocks tall — update the upper half explicitly so both
        // halves stay in sync (setBlockData with applyPhysics=false does not
        // automatically propagate the open state to the adjacent half).
        Block partnerTop = partner.getRelative(BlockFace.UP);
        BlockData topData = partnerTop.getBlockData();
        if (topData instanceof Openable topOpenable) {
          topOpenable.setOpen(openState);
          partnerTop.setBlockData(topData, false);
        }
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

  private boolean isDuplicateInteraction(Player player, Block clicked) {
    long now = System.nanoTime();
    UUID playerId = player.getUniqueId();
    InteractionStamp previous = lastInteractionByPlayer.get(playerId);

    InteractionStamp current = new InteractionStamp(
        clicked.getWorld().getUID(),
        clicked.getX(),
        clicked.getY(),
        clicked.getZ(),
        now
    );
    lastInteractionByPlayer.put(playerId, current);

    if (previous == null) {
      return false;
    }

    if (!previous.sameBlock(clicked)) {
      return false;
    }

    return (now - previous.timestampNanos()) <= DUPLICATE_INTERACTION_WINDOW_NANOS;
  }

  private record InteractionStamp(UUID worldId, int x, int y, int z, long timestampNanos) {
    private boolean sameBlock(Block block) {
      return worldId.equals(block.getWorld().getUID())
          && x == block.getX()
          && y == block.getY()
          && z == block.getZ();
    }
  }
}
