package com.mdevstudio.questboard.listener;

import com.mdevstudio.questboard.Questboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SessionListener implements Listener {

    private final Questboard plugin;

    public SessionListener(Questboard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.book().join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.book().leave(event.getPlayer());
        plugin.travel().forget(event.getPlayer());
    }
}
