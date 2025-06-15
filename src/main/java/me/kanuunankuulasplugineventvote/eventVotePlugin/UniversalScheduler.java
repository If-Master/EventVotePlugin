package me.kanuunankuulasplugineventvote.eventVotePlugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class UniversalScheduler {
    private final Plugin plugin;
    private final boolean isFolia;

    public UniversalScheduler(Plugin plugin, boolean isFolia) {
        this.plugin = plugin;
        this.isFolia = isFolia;
    }

    public void runSync(Runnable task) {
        if (isFolia) {
            try {
                Class<?> foliaGlobalRegionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                foliaGlobalRegionScheduler.getMethod("execute", Plugin.class, Runnable.class).invoke(scheduler, plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runAsync(Runnable task) {
        if (isFolia) {
            try {
                Class<?> foliaAsyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                Object scheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
                foliaAsyncScheduler.getMethod("runNow", Plugin.class, Runnable.class).invoke(scheduler, plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public BukkitTask runTimer(Runnable task, long delay, long period) {
        if (isFolia) {
            try {
                Class<?> foliaGlobalRegionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                return (BukkitTask) foliaGlobalRegionScheduler.getMethod("runAtFixedRate", Plugin.class, Runnable.class, long.class, long.class)
                        .invoke(scheduler, plugin, task, delay, period);
            } catch (Exception e) {
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }
}
