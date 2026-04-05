package me.szabee.doubledoors.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

/**
 * Utility methods for finding connected door-like blocks.
 */
public final class DoorUtil {
  private DoorUtil() {
  }

  /**
   * Finds the adjacent mirrored partner door for a standard double-door setup.
   *
   * <p>A valid partner has the same material and facing, opposite hinge, and is side-by-side.
   * This matches the usual double-door shape where both door handles face each other.</p>
   *
   * @param origin the clicked or triggered door block (upper or lower half)
   * @return the lower-half partner door block, or null when no mirrored partner exists
   */
  public static Block findMirroredDoubleDoorPartner(Block origin) {
    Block originBase = toLowerDoorBlock(origin);
    if (originBase == null) {
      return null;
    }

    Door originDoor = (Door) originBase.getBlockData();
    BlockFace facing = originDoor.getFacing();

    Block leftMatch = matchingMirroredDoor(originBase, originDoor, originBase.getRelative(leftOf(facing)));
    if (leftMatch != null) {
      return leftMatch;
    }

    return matchingMirroredDoor(originBase, originDoor, originBase.getRelative(rightOf(facing)));
  }

  /**
   * Finds connected blocks with the same material using BFS.
   *
   * @param origin the origin block
   * @param maxDistance max BFS depth in block steps
   * @return connected same-material blocks excluding origin
   */
  public static Set<Block> findConnectedDoors(Block origin, int maxDistance) {
    Set<Block> result = new HashSet<>();
    if (origin == null || maxDistance < 1) {
      return result;
    }

    Material originType = origin.getType();
    World world = origin.getWorld();
    ArrayDeque<SearchNode> queue = new ArrayDeque<>();
    Set<Long> visited = new HashSet<>();

    queue.add(new SearchNode(origin, 0));
    visited.add(coordHash(origin));

    while (!queue.isEmpty()) {
      SearchNode node = queue.poll();
      Block current = node.block();
      int depth = node.depth();

      if (depth > 0) {
        result.add(current);
      }

      if (depth >= maxDistance) {
        continue;
      }

      addNeighborIfMatching(originType, world, current.getX() + 1, current.getY(), current.getZ(), depth, visited, queue);
      addNeighborIfMatching(originType, world, current.getX() - 1, current.getY(), current.getZ(), depth, visited, queue);
      addNeighborIfMatching(originType, world, current.getX(), current.getY() + 1, current.getZ(), depth, visited, queue);
      addNeighborIfMatching(originType, world, current.getX(), current.getY() - 1, current.getZ(), depth, visited, queue);
      addNeighborIfMatching(originType, world, current.getX(), current.getY(), current.getZ() + 1, depth, visited, queue);
      addNeighborIfMatching(originType, world, current.getX(), current.getY(), current.getZ() - 1, depth, visited, queue);
    }

    return result;
  }

  private static Block matchingMirroredDoor(Block originBase, Door originDoor, Block candidate) {
    Block candidateBase = toLowerDoorBlock(candidate);
    if (candidateBase == null) {
      return null;
    }
    if (candidateBase.getType() != originBase.getType()) {
      return null;
    }

    Door candidateDoor = (Door) candidateBase.getBlockData();
    if (candidateDoor.getFacing() != originDoor.getFacing()) {
      return null;
    }
    if (candidateDoor.getHinge() == originDoor.getHinge()) {
      return null;
    }

    return candidateBase;
  }

  private static Block toLowerDoorBlock(Block block) {
    if (block == null) {
      return null;
    }
    if (!(block.getBlockData() instanceof Door doorData)) {
      return null;
    }

    if (doorData.getHalf() == Bisected.Half.BOTTOM) {
      return block;
    }

    Block lower = block.getRelative(BlockFace.DOWN);
    if (!(lower.getBlockData() instanceof Door lowerDoor)) {
      return null;
    }
    return lowerDoor.getHalf() == Bisected.Half.BOTTOM ? lower : null;
  }

  private static BlockFace leftOf(BlockFace facing) {
    return switch (facing) {
      case NORTH -> BlockFace.WEST;
      case SOUTH -> BlockFace.EAST;
      case EAST -> BlockFace.NORTH;
      case WEST -> BlockFace.SOUTH;
      default -> BlockFace.SELF;
    };
  }

  private static BlockFace rightOf(BlockFace facing) {
    return switch (facing) {
      case NORTH -> BlockFace.EAST;
      case SOUTH -> BlockFace.WEST;
      case EAST -> BlockFace.SOUTH;
      case WEST -> BlockFace.NORTH;
      default -> BlockFace.SELF;
    };
  }

  private static void addNeighborIfMatching(
      Material originType,
      World world,
      int x,
      int y,
      int z,
      int currentDepth,
      Set<Long> visited,
      ArrayDeque<SearchNode> queue
  ) {
    long neighborKey = coordHash(x, y, z);
    if (visited.contains(neighborKey)) {
      return;
    }

    Block neighbor = world.getBlockAt(x, y, z);
    if (neighbor.getType() != originType) {
      return;
    }

    visited.add(neighborKey);
    queue.add(new SearchNode(neighbor, currentDepth + 1));
  }

  private static long coordHash(Block block) {
    return coordHash(block.getX(), block.getY(), block.getZ());
  }

  private static long coordHash(int x, int y, int z) {
    // Mirrors Mojang's BlockPos-style bit packing to avoid collisions in normal world ranges.
    long lx = ((long) x & 0x3FFFFFFL) << 38;
    long lz = ((long) z & 0x3FFFFFFL) << 12;
    long ly = (long) y & 0xFFFL;
    return lx | lz | ly;
  }

  private record SearchNode(Block block, int depth) {
  }
}

