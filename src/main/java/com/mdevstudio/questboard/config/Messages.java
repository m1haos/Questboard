package com.mdevstudio.questboard.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {

    public static final String PLAYERS_ONLY = "players-only";
    public static final String RELOADED = "reloaded";
    public static final String HELP_HEADER = "help-header";
    public static final String HELP_LINE = "help-line";
    public static final String HELP_PREFIX = "help-";
    public static final String LIST_HEADER = "list-header";
    public static final String LIST_SECTION_DAILY = "list-section-daily";
    public static final String LIST_SECTION_WEEKLY = "list-section-weekly";
    public static final String LIST_LINE = "list-line";
    public static final String LIST_EMPTY = "list-empty";
    public static final String STATUS_PROGRESS = "status-progress";
    public static final String STATUS_READY = "status-ready";
    public static final String STATUS_CLAIMED = "status-claimed";
    public static final String QUESTS_NEW = "quests-new";
    public static final String QUEST_COMPLETE = "quest-complete";
    public static final String QUEST_COMPLETE_TITLE = "quest-complete-title";
    public static final String QUEST_COMPLETE_SUBTITLE = "quest-complete-subtitle";
    public static final String CLAIM_DONE = "claim-done";
    public static final String CLAIM_ALL = "claim-all";
    public static final String CLAIM_NONE = "claim-none";
    public static final String TRACK_SET = "track-set";
    public static final String TRACK_OFF = "track-off";
    public static final String TRACK_AUTO = "track-auto";
    public static final String TRACK_UNKNOWN = "track-unknown";
    public static final String TRACK_DISABLED = "track-disabled";
    public static final String TRACKER_TITLE = "tracker-title";
    public static final String STREAK_INFO = "streak-info";
    public static final String STREAK_NONE = "streak-none";
    public static final String STREAK_BONUS = "streak-bonus";
    public static final String STREAK_DISABLED = "streak-disabled";
    public static final String RESET_DONE = "reset-done";
    public static final String COMPLETE_DONE = "complete-done";
    public static final String COMPLETE_UNKNOWN = "complete-unknown";
    public static final String MENU_TITLE = "menu-title";
    public static final String MENU_HEADER_DAILY = "menu-header-daily";
    public static final String MENU_HEADER_WEEKLY = "menu-header-weekly";
    public static final String MENU_RESET_IN = "menu-reset-in";
    public static final String MENU_STREAK = "menu-streak";
    public static final String MENU_STREAK_NEXT = "menu-streak-next";
    public static final String MENU_REWARDS = "menu-rewards";
    public static final String MENU_PROGRESS = "menu-progress";
    public static final String MENU_HINT_CLAIM = "menu-hint-claim";
    public static final String MENU_HINT_TRACK = "menu-hint-track";
    public static final String MENU_HINT_TRACKED = "menu-hint-tracked";
    public static final String MENU_CLAIMED = "menu-claimed";
    public static final String REWARD_ITEM = "reward-item";
    public static final String REWARD_XP = "reward-xp";
    public static final String REWARD_COMMAND = "reward-command";
    public static final String QUEST_NAME_READY = "quest-name-ready";
    public static final String QUEST_NAME_CLAIMED = "quest-name-claimed";
    public static final String QUEST_NAME_ACTIVE = "quest-name-active";

    static final Set<String> LANGUAGES = Set.of("ru", "en");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final long MINUTES_PER_HOUR = 60;
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;
    private static final String DURATION_DAYS = "duration-days";
    private static final String DURATION_HOURS = "duration-hours";
    private static final String DURATION_MINUTES = "duration-minutes";

    private final YamlConfiguration lines;
    private final TagResolver prefix;

    private Messages(YamlConfiguration lines) {
        this.lines = lines;
        this.prefix = Placeholder.parsed("prefix", line("prefix"));
    }

    public static Messages load(JavaPlugin plugin, String language) {
        for (String available : LANGUAGES) {
            String path = path(available);
            if (!new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }

        File file = new File(plugin.getDataFolder(), path(language));
        YamlConfiguration lines = YamlConfiguration.loadConfiguration(file);
        // Keys an admin deleted, or that appeared in an update, fall back to the bundled text.
        try (Reader bundled = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource(path(language))), StandardCharsets.UTF_8)) {
            lines.setDefaults(YamlConfiguration.loadConfiguration(bundled));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Messages(lines);
    }

    public void send(CommandSender recipient, String key, TagResolver... placeholders) {
        recipient.sendMessage(component(key, placeholders));
    }

    public Component component(String key, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(line(key), prefix, TagResolver.resolver(placeholders));
    }

    public String raw(String key) {
        return line(key);
    }

    public String duration(Duration duration) {
        long total = Math.max(1, duration.toMinutes());
        long days = total / MINUTES_PER_DAY;
        long hours = total % MINUTES_PER_DAY / MINUTES_PER_HOUR;
        long minutes = total % MINUTES_PER_HOUR;
        if (days > 0) {
            return line(DURATION_DAYS).replace("<d>", String.valueOf(days)).replace("<h>", String.valueOf(hours));
        }
        if (hours > 0) {
            return line(DURATION_HOURS).replace("<h>", String.valueOf(hours)).replace("<m>", String.valueOf(minutes));
        }
        return line(DURATION_MINUTES).replace("<m>", String.valueOf(minutes));
    }

    // getString(key, fallback) would skip the bundled defaults, so they are checked before falling back to the key.
    private String line(String key) {
        String line = lines.getString(key);
        return line != null ? line : key;
    }

    private static String path(String language) {
        return "lang/" + language + ".yml";
    }
}
