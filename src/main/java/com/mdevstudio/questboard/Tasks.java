package com.mdevstudio.questboard;

public final class Tasks {

    private final Questboard plugin;

    Tasks(Questboard plugin) {
        this.plugin = plugin;
    }

    public void every(long periodTicks, Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> task.run(),
                periodTicks, periodTicks);
    }

    public void next(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    void cancelAll() {
        plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
