package me.kanuunankuulasplugineventvote.eventVotePlugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
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
                plugin.getLogger().warning("Failed to use Folia sync scheduler: " + e.getMessage());
                task.run();
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runAsync(Runnable task) {
        if (isFolia) {
            new Thread(task, "EventVotePlugin-Async-" + System.currentTimeMillis()).start();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public Object runTimer(Runnable task, long delay, long period) {
        if (isFolia) {
            try {
                Class<?> foliaGlobalRegionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());

                Class<?> consumerClass = Class.forName("java.util.function.Consumer");

                Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                        plugin.getClass().getClassLoader(),
                        new Class[]{consumerClass},
                        (proxy, method, args) -> {
                            if (method.getName().equals("accept")) {
                                task.run();
                            }
                            return null;
                        }
                );

                return foliaGlobalRegionScheduler.getMethod("runAtFixedRate", Plugin.class, consumerClass, long.class, long.class)
                        .invoke(scheduler, plugin, consumer, delay, period);

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to use Folia timer scheduler: " + e.getMessage());
                return null;
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    public void runDelayed(Runnable task, long delay) {
        if (isFolia) {
            try {
                Class<?> foliaGlobalRegionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());

                Class<?> consumerClass = Class.forName("java.util.function.Consumer");

                Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                        plugin.getClass().getClassLoader(),
                        new Class[]{consumerClass},
                        (proxy, method, args) -> {
                            if (method.getName().equals("accept")) {
                                task.run();
                            }
                            return null;
                        }
                );

                foliaGlobalRegionScheduler.getMethod("runDelayed", Plugin.class, consumerClass, long.class)
                        .invoke(scheduler, plugin, consumer, delay);

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to use Folia delayed scheduler: " + e.getMessage());
                new Thread(() -> {
                    try {
                        Thread.sleep(delay * 50); 
                        task.run();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }, "EventVotePlugin-Delayed").start();
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public void cancelTask(Object task) {
        if (task == null) return;

        if (isFolia) {
            try {
                Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                if (scheduledTaskClass.isInstance(task)) {
                    scheduledTaskClass.getMethod("cancel").invoke(task);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to cancel Folia task: " + e.getMessage());
            }
        } else {
            if (task instanceof BukkitTask) {
                ((BukkitTask) task).cancel();
            }
        }
    }
}
