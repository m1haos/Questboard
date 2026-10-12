package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.model.Period;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

public final class PlayerQuests {

    public static final class Entry {

        private final String id;
        private int progress;
        private boolean claimed;

        Entry(String id, int progress, boolean claimed) {
            this.id = id;
            this.progress = progress;
            this.claimed = claimed;
        }

        public String id() {
            return id;
        }

        public int progress() {
            return progress;
        }

        public boolean claimed() {
            return claimed;
        }

        void setProgress(int progress) {
            this.progress = progress;
        }

        void markClaimed() {
            claimed = true;
        }
    }

    private static final String SEPARATOR = "|";

    private final List<Entry> daily = new ArrayList<>();
    private final List<Entry> weekly = new ArrayList<>();
    private String dayKey = "";
    private String weekKey = "";
    private String streakDay = "";
    private int streak;
    private @Nullable String tracked;
    private boolean trackerOff;

    public List<Entry> entries(Period period) {
        return period == Period.DAILY ? daily : weekly;
    }

    public @Nullable Entry find(String id) {
        for (Entry entry : daily) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        for (Entry entry : weekly) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public String key(Period period) {
        return period == Period.DAILY ? dayKey : weekKey;
    }

    void setKey(Period period, String key) {
        if (period == Period.DAILY) {
            dayKey = key;
        } else {
            weekKey = key;
        }
    }

    public int streak() {
        return streak;
    }

    public String streakDay() {
        return streakDay;
    }

    void setStreak(int streak, String day) {
        this.streak = streak;
        this.streakDay = day;
    }

    public @Nullable String tracked() {
        return tracked;
    }

    public boolean trackerOff() {
        return trackerOff;
    }

    public void track(@Nullable String id) {
        tracked = id;
        trackerOff = false;
    }

    public void stopTracking() {
        tracked = null;
        trackerOff = true;
    }

    public void trackAutomatically() {
        tracked = null;
        trackerOff = false;
    }

    String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("day", dayKey);
        yaml.set("week", weekKey);
        yaml.set("streak", streak);
        yaml.set("streak-day", streakDay);
        yaml.set("tracked", tracked);
        yaml.set("tracker-off", trackerOff);
        yaml.set("daily", encode(daily));
        yaml.set("weekly", encode(weekly));
        return yaml.saveToString();
    }

    static PlayerQuests parse(String raw) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(raw);
        PlayerQuests state = new PlayerQuests();
        state.dayKey = yaml.getString("day", "");
        state.weekKey = yaml.getString("week", "");
        state.streak = yaml.getInt("streak");
        state.streakDay = yaml.getString("streak-day", "");
        state.tracked = yaml.getString("tracked");
        state.trackerOff = yaml.getBoolean("tracker-off");
        decode(yaml.getStringList("daily"), state.daily);
        decode(yaml.getStringList("weekly"), state.weekly);
        return state;
    }

    private static List<String> encode(List<Entry> entries) {
        return entries.stream()
                .map(entry -> entry.id + SEPARATOR + entry.progress + SEPARATOR + entry.claimed)
                .toList();
    }

    private static void decode(List<String> lines, List<Entry> into) {
        for (String line : lines) {
            String[] parts = line.split("\\" + SEPARATOR);
            if (parts.length == 3) {
                into.add(new Entry(parts[0], Integer.parseInt(parts[1]), Boolean.parseBoolean(parts[2])));
            }
        }
    }
}
