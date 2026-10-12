package com.mdevstudio.questboard.config;

import com.mdevstudio.questboard.model.Period;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

public record PluginConfig(
        String language,
        Modules modules,
        List<String> commandAliases,
        Map<String, List<String>> subcommandAliases,
        Schedule schedule,
        boolean autoClaim,
        Tracker tracker,
        Protection protection,
        boolean completionParticles,
        Sounds sounds) {

    public record Modules(boolean daily, boolean weekly, boolean streak, boolean tracker) {

        public boolean has(Period period) {
            return period == Period.DAILY ? daily : weekly;
        }
    }

    public record Tracker(boolean autoPick, BossBar.Color color) {
    }

    public record Protection(
            boolean ignoreCreative,
            boolean ignoreSpawnerMobs,
            boolean ignorePlacedBlocks,
            double travelMaxStep) {
    }

    public record Sounds(Key complete, Key claim, Key streak, Key click) {
    }

    private static final int VERSION = 1;
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final int MAX_QUESTS_PER_PERIOD = 5;

    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration yaml = plugin.getConfig();
        Logger logger = plugin.getSLF4JLogger();

        if (yaml.getInt("config-version") < VERSION) {
            // Admins keep their values; only keys added in newer versions are written in.
            yaml.options().copyDefaults(true);
            yaml.set("config-version", VERSION);
            plugin.saveConfig();
        }

        Reader reader = new Reader(yaml, logger);
        return new PluginConfig(
                reader.language(),
                new Modules(
                        yaml.getBoolean("modules.daily", true),
                        yaml.getBoolean("modules.weekly", true),
                        yaml.getBoolean("modules.streak", true),
                        yaml.getBoolean("modules.tracker", true)),
                yaml.getStringList("commands.aliases"),
                reader.subcommandAliases(),
                reader.schedule(),
                yaml.getBoolean("claiming.auto-claim", false),
                new Tracker(yaml.getBoolean("tracker.auto-pick", true), reader.barColor()),
                new Protection(
                        yaml.getBoolean("protection.ignore-creative", true),
                        yaml.getBoolean("protection.ignore-spawner-mobs", true),
                        yaml.getBoolean("protection.ignore-placed-blocks", true),
                        Math.max(1, yaml.getDouble("protection.travel-max-step", 64))),
                yaml.getBoolean("effects.completion-particles", true),
                new Sounds(
                        reader.sound("complete", "ui.toast.challenge_complete"),
                        reader.sound("claim", "entity.player.levelup"),
                        reader.sound("streak", "block.beacon.activate"),
                        reader.sound("click", "ui.button.click")));
    }

    public List<String> aliasesOf(String subcommand) {
        return subcommandAliases.getOrDefault(subcommand, List.of());
    }

    private record Reader(FileConfiguration yaml, Logger logger) {

        String language() {
            String language = yaml.getString("language", DEFAULT_LANGUAGE);
            if (!Messages.LANGUAGES.contains(language)) {
                logger.warn("config.yml: language '{}' is not available, expected one of {}. Using '{}'.",
                        language, Messages.LANGUAGES, DEFAULT_LANGUAGE);
                return DEFAULT_LANGUAGE;
            }
            return language;
        }

        Map<String, List<String>> subcommandAliases() {
            Map<String, List<String>> aliases = new LinkedHashMap<>();
            ConfigurationSection section = yaml.getConfigurationSection("commands.subcommands");
            if (section != null) {
                for (String name : section.getKeys(false)) {
                    aliases.put(name, section.getStringList(name));
                }
            }
            return aliases;
        }

        Schedule schedule() {
            return new Schedule(
                    zone(),
                    Math.max(0, Math.min(23, yaml.getInt("rotation.reset-hour", 4))),
                    weekStart(),
                    count("rotation.daily-count", 3),
                    count("rotation.weekly-count", 2));
        }

        BossBar.Color barColor() {
            String name = yaml.getString("tracker.color", "GREEN");
            try {
                return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warn("config.yml: tracker.color '{}' is not a boss bar colour. Using GREEN.", name);
                return BossBar.Color.GREEN;
            }
        }

        Key sound(String name, String fallback) {
            String path = "sounds." + name;
            String id = yaml.getString(path);
            if (id == null || !Key.parseable(id)) {
                logger.warn("config.yml: {} '{}' is not a valid sound id. Using {}.", path, id, fallback);
                return Key.key(fallback);
            }
            return Key.key(id);
        }

        private ZoneId zone() {
            String name = yaml.getString("rotation.timezone", "system");
            if (name.equalsIgnoreCase("system")) {
                return ZoneId.systemDefault();
            }
            try {
                return ZoneId.of(name);
            } catch (DateTimeException e) {
                logger.warn("config.yml: rotation.timezone '{}' is not a time zone id like Europe/Moscow. "
                        + "Using the server zone.", name);
                return ZoneId.systemDefault();
            }
        }

        private DayOfWeek weekStart() {
            String name = yaml.getString("rotation.week-starts", "MONDAY");
            try {
                return DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warn("config.yml: rotation.week-starts '{}' is not a day of the week. Using MONDAY.", name);
                return DayOfWeek.MONDAY;
            }
        }

        private int count(String path, int fallback) {
            int value = yaml.getInt(path, fallback);
            if (value < 0 || value > MAX_QUESTS_PER_PERIOD) {
                logger.warn("config.yml: {} must be from 0 to {}, got {}. Using {}.",
                        path, MAX_QUESTS_PER_PERIOD, value, fallback);
                return fallback;
            }
            return value;
        }
    }
}
