package com.mdevstudio.questboard.listener;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.model.Goal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;

public final class GoalListener implements Listener {

    private static final int PLACED_MEMORY = 20_000;

    private record BlockPos(UUID world, int x, int y, int z) {

        static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    // Blocks a player placed are not counted when broken, so place-and-break loops earn nothing.
    private static final class RecentBlocks extends LinkedHashMap<BlockPos, Boolean> {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
            return size() > PLACED_MEMORY;
        }
    }

    private final Questboard plugin;
    private final NamespacedKey spawnerMark;
    private final Map<BlockPos, Boolean> placed = new RecentBlocks();
    private final Set<String> craftsInFlight = new HashSet<>();

    public GoalListener(Questboard plugin) {
        this.plugin = plugin;
        this.spawnerMark = new NamespacedKey(plugin, "from-spawner");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.settings().protection().ignorePlacedBlocks()) {
            placed.put(BlockPos.of(event.getBlockPlaced()), Boolean.TRUE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable crop) {
            if (crop.getAge() < crop.getMaximumAge()) {
                return;
            }
        } else if (placed.remove(BlockPos.of(block)) != null) {
            return;
        }
        plugin.progress().add(event.getPlayer(), Goal.BREAK, block.getType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().getPersistentDataContainer().set(spawnerMark, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || victim instanceof Player) {
            return;
        }
        if (plugin.settings().protection().ignoreSpawnerMobs()
                && victim.getPersistentDataContainer().has(spawnerMark, PersistentDataType.BYTE)) {
            return;
        }
        plugin.progress().add(killer, Goal.KILL, victim.getType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught) {
            plugin.progress().add(event.getPlayer(), Goal.FISH,
                    caught.getItemStack().getType().getKey().getKey(), caught.getItemStack().getAmount());
        }
    }

    // Shift-click crafts many items in one event, so the statistic is compared on the next tick instead of guessing.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        Material crafted = event.getRecipe().getResult().getType();
        if (!(event.getWhoClicked() instanceof Player player) || !crafted.isItem()) {
            return;
        }
        String pending = player.getUniqueId() + crafted.name();
        if (!craftsInFlight.add(pending)) {
            return;
        }
        int before = player.getStatistic(Statistic.CRAFT_ITEM, crafted);
        plugin.tasks().next(() -> {
            craftsInFlight.remove(pending);
            if (player.isOnline()) {
                plugin.progress().add(player, Goal.CRAFT, crafted.getKey().getKey(),
                        player.getStatistic(Statistic.CRAFT_ITEM, crafted) - before);
            }
        });
    }
}
