package me.szabee.doubledoors.util;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Routes scheduled work through Folia-aware schedulers when available.
 */
public final class SchedulerBridge {
  private SchedulerBridge() {
  }

  /**
   * Runs a task on the next server tick.
   *
   * @param plugin the owning plugin
   * @param task the task to run
   */
  public static void runNextTick(Plugin plugin, Runnable task) {
    if (runFoliaGlobal(plugin, task, 1L)) {
      return;
    }
    plugin.getServer().getScheduler().runTask(plugin, task);
  }

  /**
   * Runs a task later at the region that owns the given location.
   *
   * @param plugin the owning plugin
   * @param location the target location
   * @param delayTicks the delay in ticks
   * @param task the task to run
   */
  public static void runLaterAtLocation(Plugin plugin, Location location, long delayTicks, Runnable task) {
    if (runFoliaRegion(plugin, location, delayTicks, task)) {
      return;
    }
    plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
  }

  /**
   * Runs a task asynchronously.
   *
   * @param plugin the owning plugin
   * @param task the task to run
   */
  public static void runAsync(Plugin plugin, Runnable task) {
    if (runFoliaAsync(plugin, task)) {
      return;
    }
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
  }

  private static boolean runFoliaGlobal(Plugin plugin, Runnable task, long delayTicks) {
    try {
      Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
      Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
      runDelayed.invoke(scheduler, plugin, consumer(task), delayTicks);
      return true;
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  private static boolean runFoliaRegion(Plugin plugin, Location location, long delayTicks, Runnable task) {
    try {
      Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
      Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
      runDelayed.invoke(scheduler, plugin, location, consumer(task), delayTicks);
      return true;
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  private static boolean runFoliaAsync(Plugin plugin, Runnable task) {
    try {
      Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
      Method runNow = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
      runNow.invoke(scheduler, plugin, consumer(task));
      return true;
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  private static Consumer<Object> consumer(Runnable task) {
    return ignored -> task.run();
  }
}