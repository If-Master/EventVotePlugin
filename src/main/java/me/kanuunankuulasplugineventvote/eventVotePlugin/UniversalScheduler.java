package me.kanuunankuulasplugineventvote.eventVotePlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class UniversalScheduler {
    private final JavaPlugin plugin;
    private final boolean isFolia;

    public UniversalScheduler(JavaPlugin plugin, boolean isFolia) {
        this.plugin = plugin;
        this.isFolia = isFolia;
    }

    public void runTimer(Runnable task, long delay, long period) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (scheduledTask) -> task.run(), delay, period);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.run();
                }
            }.runTaskTimer(plugin, delay, period);
        }
    }

    public void runAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, (scheduledTask) -> task.run());
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.run();
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    public void runSync(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, (scheduledTask) -> task.run());
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.run();
                }
            }.runTask(plugin);
        }
    }

    public void runPlayerSync(Player player, Runnable task) {
        if (isFolia) {
            player.getScheduler().run(plugin, (scheduledTask) -> task.run(), null);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.run();
                }
            }.runTask(plugin);
        }
    }
}
