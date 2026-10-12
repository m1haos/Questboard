package com.mdevstudio.questboard.config;

import com.mdevstudio.questboard.model.Goal;
import com.mdevstudio.questboard.model.Period;
import com.mdevstudio.questboard.model.QuestDef;
import com.mdevstudio.questboard.model.Reward;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record QuestCatalog(List<QuestDef> quests, Map<Integer, List<Reward>> streakRewards, int streakCycle) {

    private static final String FILE = "quests.yml";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String TAG_PREFIX = "#";
    private static final String MINECRAFT_PREFIX = "minecraft:";

    public static QuestCatalog load(JavaPlugin plugin, String language) {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Parser parser = new Parser(plugin.getSLF4JLogger(), language);

        List<QuestDef> quests = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection quest = section.getConfigurationSection(id);
                QuestDef parsed = quest == null ? null : parser.quest(id, quest);
                if (parsed != null) {
                    quests.add(parsed);
                }
            }
        }
        if (quests.isEmpty()) {
            plugin.getSLF4JLogger().warn("{}: no usable quests, the board will stay empty.", FILE);
        }

        Map<Integer, List<Reward>> streakRewards = new LinkedHashMap<>();
        ConfigurationSection streak = yaml.getConfigurationSection("streak.rewards");
        if (streak != null) {
            for (String day : streak.getKeys(false)) {
                try {
                    List<Reward> rewards = parser.rewards("streak.rewards." + day, streak.getList(day));
                    streakRewards.put(Integer.parseInt(day), rewards);
                } catch (NumberFormatException e) {
                    plugin.getSLF4JLogger().warn("{}: streak.rewards.{} is not a day number. Skipped.", FILE, day);
                }
            }
        }
        return new QuestCatalog(List.copyOf(quests), streakRewards, Math.max(1, yaml.getInt("streak.cycle", 7)));
    }

    public @Nullable QuestDef find(String id) {
        for (QuestDef quest : quests) {
            if (quest.id().equals(id)) {
                return quest;
            }
        }
        return null;
    }

    public List<QuestDef> pool(Period period) {
        return quests.stream().filter(quest -> quest.period() == period).toList();
    }

    public int milestone(int streak) {
        return (streak - 1) % streakCycle + 1;
    }

    public List<Reward> streakReward(int streak) {
        return streakRewards.getOrDefault(milestone(streak), List.of());
    }

    private record Parser(Logger logger, String language) {

        @Nullable QuestDef quest(String id, ConfigurationSection section) {
            String path = FILE + ": quests." + id;
            Period period = enumValue(Period.class, section.getString("period", "daily"));
            Goal goal = Goal.parse(section.getString("goal"));
            if (period == null || goal == null) {
                logger.warn("{}: period must be daily or weekly and goal one of break, kill, fish, craft, travel. "
                        + "Quest skipped.", path);
                return null;
            }
            int amount = section.getInt("amount", 0);
            if (amount < 1) {
                logger.warn("{}.amount must be at least 1. Quest skipped.", path);
                return null;
            }
            Material icon = Material.matchMaterial(section.getString("icon", ""));
            if (icon == null || !icon.isItem()) {
                logger.warn("{}.icon is not an item, using PAPER.", path);
                icon = Material.PAPER;
            }
            List<Reward> rewards = rewards(path + ".rewards", section.getList("rewards"));
            if (rewards.isEmpty()) {
                logger.warn("{} has no usable rewards.", path);
            }
            return new QuestDef(id, period, goal, targets(path, goal, section.getStringList("targets")), amount, icon,
                    Math.max(1, section.getInt("weight", 1)),
                    text(section, "title", id), text(section, "description", ""), rewards);
        }

        List<Reward> rewards(String path, @Nullable List<?> entries) {
            List<Reward> rewards = new ArrayList<>();
            if (entries == null) {
                return rewards;
            }
            for (Object entry : entries) {
                Reward reward = entry instanceof Map<?, ?> map ? reward(path, map) : null;
                if (reward == null) {
                    logger.warn("{}: an entry is not item, xp or command. Skipped.", path);
                } else {
                    rewards.add(reward);
                }
            }
            return rewards;
        }

        private @Nullable Reward reward(String path, Map<?, ?> map) {
            if (map.get("item") != null) {
                Material material = Material.matchMaterial(String.valueOf(map.get("item")));
                if (material == null || !material.isItem()) {
                    logger.warn("{}: '{}' is not an item.", path, map.get("item"));
                    return null;
                }
                return new Reward.Item(material, number(map.get("amount"), 1));
            }
            if (map.get("xp") != null) {
                return new Reward.Experience(number(map.get("xp"), 0));
            }
            if (map.get("command") != null) {
                String line = String.valueOf(map.get("command")).replaceFirst("^/", "");
                Object label = map.get("label");
                return new Reward.Command(line, label == null ? line : String.valueOf(label));
            }
            return null;
        }

        private Set<String> targets(String path, Goal goal, List<String> raw) {
            Set<String> keys = new LinkedHashSet<>();
            for (String entry : raw) {
                String name = entry.toLowerCase(Locale.ROOT).replace(MINECRAFT_PREFIX, "");
                if (name.startsWith(TAG_PREFIX)) {
                    Set<String> members = tagMembers(goal, name.substring(TAG_PREFIX.length()));
                    if (members.isEmpty()) {
                        logger.warn("{}.targets: tag '{}' is unknown or empty.", path, entry);
                    }
                    keys.addAll(members);
                } else if (exists(goal, name)) {
                    keys.add(name);
                } else {
                    logger.warn("{}.targets: '{}' does not exist.", path, entry);
                }
            }
            return keys;
        }

        private static boolean exists(Goal goal, String name) {
            NamespacedKey key = NamespacedKey.minecraft(name);
            return goal == Goal.KILL ? Registry.ENTITY_TYPE.get(key) != null : Registry.MATERIAL.get(key) != null;
        }

        private static Set<String> tagMembers(Goal goal, String name) {
            NamespacedKey key = NamespacedKey.minecraft(name);
            Set<String> members = new LinkedHashSet<>();
            if (goal == Goal.KILL) {
                Tag<EntityType> tag = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
                if (tag != null) {
                    tag.getValues().forEach(type -> members.add(type.getKey().getKey()));
                }
                return members;
            }
            String registry = goal == Goal.BREAK ? Tag.REGISTRY_BLOCKS : Tag.REGISTRY_ITEMS;
            Tag<Material> tag = Bukkit.getTag(registry, key, Material.class);
            if (tag != null) {
                tag.getValues().forEach(material -> members.add(material.getKey().getKey()));
            }
            return members;
        }

        // A text is either one string or a map of language to string, so one file can serve several servers.
        private String text(ConfigurationSection section, String key, String fallback) {
            Object value = section.get(key);
            if (value instanceof String plain) {
                return plain;
            }
            if (value instanceof ConfigurationSection translations) {
                String text = translations.getString(language);
                if (text == null) {
                    text = translations.getString(DEFAULT_LANGUAGE);
                }
                if (text == null) {
                    text = translations.getKeys(false).stream().findFirst().map(translations::getString).orElse(null);
                }
                return text != null ? text : fallback;
            }
            return fallback;
        }

        private static int number(@Nullable Object value, int fallback) {
            return value instanceof Number number ? Math.max(1, number.intValue()) : fallback;
        }

        private static <E extends Enum<E>> @Nullable E enumValue(Class<E> type, String name) {
            try {
                return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
