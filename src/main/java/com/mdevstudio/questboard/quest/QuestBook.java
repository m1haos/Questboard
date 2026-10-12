package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.config.Messages;
import com.mdevstudio.questboard.model.Period;
import com.mdevstudio.questboard.model.QuestDef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class QuestBook {

    private static final int SAVE_EVERY_MINUTES = 5;

    private final Questboard plugin;
    private final NamespacedKey stateKey;
    private final Map<UUID, PlayerQuests> cache = new HashMap<>();
    private int minutes;

    public QuestBook(Questboard plugin) {
        this.plugin = plugin;
        this.stateKey = new NamespacedKey(plugin, "state");
    }

    public PlayerQuests of(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> read(player));
    }

    public void join(Player player) {
        if (refill(of(player), Instant.now())) {
            plugin.messages().send(player, Messages.QUESTS_NEW);
        }
        plugin.tracker().refresh(player);
    }

    public void leave(Player player) {
        PlayerQuests state = cache.remove(player.getUniqueId());
        if (state != null) {
            write(player, state);
        }
        plugin.tracker().forget(player);
    }

    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerQuests state = cache.get(player.getUniqueId());
            if (state != null) {
                write(player, state);
            }
        }
    }

    // Runs once a minute: moves boards over at the reset hour and saves online players now and then.
    public void upkeep() {
        Instant now = Instant.now();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerQuests state = cache.get(player.getUniqueId());
            if (state != null && refill(state, now)) {
                plugin.messages().send(player, Messages.QUESTS_NEW);
                plugin.tracker().refresh(player);
            }
        }
        if (++minutes % SAVE_EVERY_MINUTES == 0) {
            saveAll();
        }
    }

    public void refreshOnline() {
        Instant now = Instant.now();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerQuests state = cache.get(player.getUniqueId());
            if (state != null) {
                refill(state, now);
                plugin.tracker().refresh(player);
            }
        }
    }

    public void reroll(Player player) {
        PlayerQuests state = of(player);
        state.setKey(Period.DAILY, "");
        state.setKey(Period.WEEKLY, "");
        state.track(null);
        refill(state, Instant.now());
        plugin.tracker().refresh(player);
    }

    public List<Active> active(PlayerQuests state) {
        List<Active> active = new ArrayList<>();
        for (Period period : Period.values()) {
            if (plugin.settings().modules().has(period)) {
                active.addAll(active(state, period));
            }
        }
        return active;
    }

    public List<Active> active(PlayerQuests state, Period period) {
        List<Active> active = new ArrayList<>();
        for (PlayerQuests.Entry entry : state.entries(period)) {
            QuestDef def = plugin.catalog().find(entry.id());
            if (def != null) {
                active.add(new Active(def, entry));
            }
        }
        return active;
    }

    // A streak that skipped yesterday is already broken, even though the counter is only reset on the next win.
    public int liveStreak(PlayerQuests state) {
        String today = plugin.rotation().day(Instant.now());
        String last = state.streakDay();
        boolean alive = last.equals(today) || last.equals(plugin.rotation().previousDay(today));
        return alive ? state.streak() : 0;
    }

    private boolean refill(PlayerQuests state, Instant now) {
        boolean renewed = false;
        for (Period period : Period.values()) {
            if (plugin.settings().modules().has(period)) {
                renewed |= refill(state, period, now);
            }
        }
        return renewed;
    }

    private boolean refill(PlayerQuests state, Period period, Instant now) {
        String key = period == Period.DAILY ? plugin.rotation().day(now) : plugin.rotation().week(now);
        List<PlayerQuests.Entry> entries = state.entries(period);
        boolean renewed = !key.equals(state.key(period));
        if (renewed) {
            entries.clear();
            state.setKey(period, key);
        } else {
            entries.removeIf(entry -> plugin.catalog().find(entry.id()) == null);
        }
        List<QuestDef> pool = plugin.catalog().pool(period);
        int missing = Math.min(plugin.settings().schedule().count(period), pool.size()) - entries.size();
        if (missing > 0) {
            Set<String> taken = new HashSet<>();
            entries.forEach(entry -> taken.add(entry.id()));
            pick(pool, taken, missing).forEach(def -> entries.add(new PlayerQuests.Entry(def.id(), 0, false)));
        }
        return renewed && !entries.isEmpty();
    }

    private static List<QuestDef> pick(List<QuestDef> pool, Set<String> taken, int count) {
        List<QuestDef> candidates = new ArrayList<>(pool.stream().filter(def -> !taken.contains(def.id())).toList());
        List<QuestDef> picked = new ArrayList<>();
        while (picked.size() < count && !candidates.isEmpty()) {
            int roll = ThreadLocalRandom.current().nextInt(candidates.stream().mapToInt(QuestDef::weight).sum());
            for (QuestDef candidate : candidates) {
                roll -= candidate.weight();
                if (roll < 0) {
                    picked.add(candidate);
                    candidates.remove(candidate);
                    break;
                }
            }
        }
        return picked;
    }

    private PlayerQuests read(Player player) {
        String raw = player.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
        if (raw == null) {
            return new PlayerQuests();
        }
        try {
            return PlayerQuests.parse(raw);
        } catch (InvalidConfigurationException | NumberFormatException e) {
            plugin.getSLF4JLogger().warn("Quest data of {} could not be read and starts fresh: {}",
                    player.getName(), e.getMessage());
            return new PlayerQuests();
        }
    }

    private void write(Player player, PlayerQuests state) {
        player.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, state.serialize());
    }
}
