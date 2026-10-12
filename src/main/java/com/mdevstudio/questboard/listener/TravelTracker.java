package com.mdevstudio.questboard.listener;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.model.Goal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TravelTracker {

    private final Questboard plugin;
    private final Map<UUID, Location> lastSeen = new HashMap<>();
    private final Map<UUID, Double> carried = new HashMap<>();

    public TravelTracker(Questboard plugin) {
        this.plugin = plugin;
    }

    // Sampling once a second is cheaper than listening to every move packet; jumps above the step are teleports.
    public void sample() {
        double maxStep = plugin.settings().protection().travelMaxStep();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location now = player.getLocation();
            Location before = lastSeen.put(player.getUniqueId(), now);
            if (before == null || !now.getWorld().equals(before.getWorld())) {
                continue;
            }
            double step = Math.hypot(now.getX() - before.getX(), now.getZ() - before.getZ());
            if (step > maxStep) {
                continue;
            }
            double total = carried.merge(player.getUniqueId(), step, Double::sum);
            int whole = (int) total;
            if (whole > 0) {
                carried.put(player.getUniqueId(), total - whole);
                plugin.progress().add(player, Goal.TRAVEL, "", whole);
            }
        }
    }

    public void forget(Player player) {
        lastSeen.remove(player.getUniqueId());
        carried.remove(player.getUniqueId());
    }
}
