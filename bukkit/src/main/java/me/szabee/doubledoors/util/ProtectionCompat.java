package me.szabee.doubledoors.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import me.szabee.doubledoors.DoubleDoors;

/**
 * Optional protection-plugin compatibility hooks.
 */

/**
 * Optional protection-plugin compatibility hooks.
 */
public final class ProtectionCompat {
  private ProtectionCompat() {
  }

  /**
   * Checks whether a player may toggle a linked block according to protection rules.
   *
   * <p>Currently integrates with GriefPrevention when installed. If integration cannot
   * be resolved, the check fails open to avoid breaking door interactions.</p>
   *
   * @param plugin the plugin instance
   * @param player the interacting player
   * @param linkedBlock the linked block to toggle
   * @return true if the linked interaction is allowed
   */
  public static boolean canOpenLinkedDoor(DoubleDoors plugin, Player player, Block linkedBlock) {
    Plugin griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention");
    if (griefPrevention == null || !griefPrevention.isEnabled()) {
      return true;
    }

    try {
      Object dataStore = getDataStore(griefPrevention);
      if (dataStore == null) {
        return true;
      }

      Object claim = findClaimAt(dataStore, linkedBlock);
      if (claim == null) {
        return true;
      }

      Boolean allowBuildResult = tryAllowBuild(claim, player, linkedBlock.getType());
      if (allowBuildResult != null) {
        return allowBuildResult;
      }

      Boolean checkPermissionResult = tryCheckPermission(claim, player);
      if (checkPermissionResult != null) {
        return checkPermissionResult;
      }
    } catch (ReflectiveOperationException ex) {
      plugin.getLogger().fine("GriefPrevention compatibility check skipped: " + ex.getMessage());
      return true;
    }

    return true;
  }

  private static Object getDataStore(Plugin griefPrevention) throws ReflectiveOperationException {
    Class<?> gpClass = griefPrevention.getClass();

    try {
      Field dataStoreField = gpClass.getField("dataStore");
      return dataStoreField.get(griefPrevention);
    } catch (NoSuchFieldException ignored) {
      Field dataStoreField = gpClass.getDeclaredField("dataStore");
      dataStoreField.setAccessible(true);
      return dataStoreField.get(griefPrevention);
    }
  }

  private static Object findClaimAt(Object dataStore, Block linkedBlock) throws ReflectiveOperationException {
    Method getClaimAt = null;
    for (Method method : dataStore.getClass().getMethods()) {
      if (!method.getName().equals("getClaimAt")) {
        continue;
      }
      Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length >= 1
          && "org.bukkit.Location".equals(parameterTypes[0].getName())) {
        getClaimAt = method;
        break;
      }
    }

    if (getClaimAt == null) {
      return null;
    }

    Object[] args = new Object[getClaimAt.getParameterCount()];
    Class<?>[] parameterTypes = getClaimAt.getParameterTypes();
    args[0] = linkedBlock.getLocation();
    for (int i = 1; i < parameterTypes.length; i++) {
      if (parameterTypes[i] == boolean.class || parameterTypes[i] == Boolean.class) {
        args[i] = Boolean.TRUE;
      } else {
        args[i] = null;
      }
    }

    return getClaimAt.invoke(dataStore, args);
  }

  private static Boolean tryAllowBuild(Object claim, Player player, Material material)
      throws ReflectiveOperationException {
    for (Method method : claim.getClass().getMethods()) {
      if (!method.getName().equals("allowBuild")) {
        continue;
      }

      Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length != 2) {
        continue;
      }
      if (!Player.class.isAssignableFrom(parameterTypes[0])) {
        continue;
      }
      if (!Material.class.isAssignableFrom(parameterTypes[1])) {
        continue;
      }

      Object result = method.invoke(claim, player, material);
      if (result == null) {
        return true;
      }
      if (result instanceof String message) {
        return message.isEmpty();
      }
      return false;
    }

    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Boolean tryCheckPermission(Object claim, Player player) throws ReflectiveOperationException {
    Class<?> claimPermissionClass;
    try {
      claimPermissionClass = Class.forName("me.ryanhamshire.GriefPrevention.ClaimPermission");
    } catch (ClassNotFoundException ex) {
      return null;
    }

    Object buildPermission = Enum.valueOf((Class<Enum>) claimPermissionClass, "Build");

    for (Method method : claim.getClass().getMethods()) {
      if (!method.getName().equals("checkPermission")) {
        continue;
      }

      Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length < 2) {
        continue;
      }
      if (!Player.class.isAssignableFrom(parameterTypes[0])) {
        continue;
      }
      if (!parameterTypes[1].isAssignableFrom(claimPermissionClass)
          && !claimPermissionClass.isAssignableFrom(parameterTypes[1])) {
        continue;
      }

      Object[] args = new Object[parameterTypes.length];
      args[0] = player;
      args[1] = buildPermission;
      for (int i = 2; i < parameterTypes.length; i++) {
        args[i] = null;
      }

      Object result = method.invoke(claim, args);
      if (result == null) {
        return true;
      }
      if (result instanceof Boolean boolResult) {
        return boolResult;
      }
      if (result instanceof String message) {
        return message.isEmpty();
      }
      if (result instanceof Supplier<?> supplier) {
        Object supplied = supplier.get();
        return supplied == null || supplied.toString().isEmpty();
      }

      return false;
    }

    return null;
  }

  /**
   * Returns the GriefPrevention claim ID at the given block's location.
   *
   * <p>Returns {@code -1L} when GriefPrevention is not installed, not enabled,
   * the block is in the wilderness, or any reflective lookup fails.</p>
   *
   * @param plugin the plugin instance
   * @param block  the block whose location is checked
   * @return the claim ID, or {@code -1L} if unavailable
   */
  public static long getClaimIdAt(DoubleDoors plugin, Block block) {
    Plugin griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention");
    if (griefPrevention == null || !griefPrevention.isEnabled()) {
      return -1L;
    }
    try {
      Object dataStore = getDataStore(griefPrevention);
      if (dataStore == null) {
        return -1L;
      }
      Object claim = findClaimAt(dataStore, block);
      if (claim == null) {
        return -1L;
      }
      return extractClaimId(claim);
    } catch (ReflectiveOperationException ex) {
      plugin.getLogger().fine("GriefPrevention claim-ID lookup failed: " + ex.getMessage());
      return -1L;
    }
  }

  /**
   * Returns whether the given player has claim-management rights at the block's location.
   *
   * <p>A player is considered a manager if they have build trust (or higher) in the claim,
   * or if GriefPrevention is not installed. Fails open on any reflective error.</p>
   *
   * @param plugin the plugin instance
   * @param player the player to check
   * @param block  the block whose claim is evaluated
   * @return true if the player may manage the claim
   */
  public static boolean isClaimManagerAt(DoubleDoors plugin, Player player, Block block) {
    Plugin griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention");
    if (griefPrevention == null || !griefPrevention.isEnabled()) {
      return true;
    }
    try {
      Object dataStore = getDataStore(griefPrevention);
      if (dataStore == null) {
        return true;
      }
      Object claim = findClaimAt(dataStore, block);
      if (claim == null) {
        return true; // wilderness — no restrictions
      }
      Boolean allowBuild = tryAllowBuild(claim, player, block.getType());
      if (allowBuild != null) {
        return allowBuild;
      }
      Boolean checkPerm = tryCheckPermission(claim, player);
      if (checkPerm != null) {
        return checkPerm;
      }
    } catch (ReflectiveOperationException ex) {
      plugin.getLogger().fine("GriefPrevention claim-manager check failed: " + ex.getMessage());
    }
    return true;
  }

  private static long extractClaimId(Object claim) throws ReflectiveOperationException {
    for (Method method : claim.getClass().getMethods()) {
      if (method.getName().equals("getID") && method.getParameterCount() == 0) {
        Object result = method.invoke(claim);
        if (result instanceof Long l) {
          return l;
        }
        if (result instanceof Number n) {
          return n.longValue();
        }
      }
    }
    try {
      Field idField = claim.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      Object val = idField.get(claim);
      if (val instanceof Long l) {
        return l;
      }
      if (val instanceof Number n) {
        return n.longValue();
      }
    } catch (NoSuchFieldException ignored) {
      // fall through
    }
    return -1L;
  }
}

