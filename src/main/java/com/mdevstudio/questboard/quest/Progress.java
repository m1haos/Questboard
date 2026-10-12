package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.config.Messages;
import com.mdevstudio.questboard.config.PluginConfig;
import com.mdevstudio.questboard.model.Goal;
import com.mdevstudio.questboard.model.Period;
import com.mdevstudio.questboard.model.QuestDef;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public final class Progress {

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(600));

    private final Questboard plugin;

    public Progress(Questboard plugin) {
        this.plugin = plugin;
    }

    public void add(Player player, Goal goal, String key, int amount) {
        if (amount <= 0 || ignored(player)) {
            return;
        }
        PlayerQuests state = plugin.book().of(player);
        boolean changed = false;
        for (Active active : plugin.book().active(state)) {
            QuestDef def = active.def();
            if (def.goal() != goal || !def.accepts(key) || active.done()) {
                continue;
            }
            active.entry().setProgress(Math.min(def.amount(), active.entry().progress() + amount));
            changed = true;
            if (active.done()) {
                finish(player, state, active);
            }
        }
        if (changed) {
            plugin.tracker().refresh(player);
        }
    }

    public void finish(Player player, PlayerQuests state, Active active) {
        Messages messages = plugin.messages();
        TagResolver title = Placeholder.parsed("title", active.def().title());
        player.showTitle(Title.title(messages.component(Messages.QUEST_COMPLETE_TITLE),
                messages.component(Messages.QUEST_COMPLETE_SUBTITLE, title), TITLE_TIMES));
        play(player, plugin.settings().sounds().complete());
        if (plugin.settings().completionParticles()) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                    30, 0.4, 0.7, 0.4, 0.3);
        }
        if (plugin.settings().autoClaim()) {
            claim(player, active);
        } else {
            messages.send(player, Messages.QUEST_COMPLETE, title);
        }
        extendStreak(player, state);
    }

    public boolean claim(Player player, Active active) {
        if (!active.ready()) {
            return false;
        }
        active.entry().markClaimed();
        plugin.rewards().give(player, active.def().rewards());
        plugin.messages().send(player, Messages.CLAIM_DONE, Placeholder.parsed("title", active.def().title()));
        play(player, plugin.settings().sounds().claim());
        plugin.tracker().refresh(player);
        return true;
    }

    public int claimAll(Player player) {
        int claimed = 0;
        for (Active active : plugin.book().active(plugin.book().of(player))) {
            if (claim(player, active)) {
                claimed++;
            }
        }
        return claimed;
    }

    public void forceComplete(Player player, Active active) {
        if (active.done()) {
            return;
        }
        active.entry().setProgress(active.def().amount());
        finish(player, plugin.book().of(player), active);
        plugin.tracker().refresh(player);
    }

    private void extendStreak(Player player, PlayerQuests state) {
        if (!plugin.settings().modules().streak() || !plugin.settings().modules().daily()) {
            return;
        }
        List<Active> dailies = plugin.book().active(state, Period.DAILY);
        String today = state.key(Period.DAILY);
        if (dailies.isEmpty() || dailies.stream().anyMatch(active -> !active.done())
                || today.equals(state.streakDay())) {
            return;
        }
        int streak = state.streakDay().equals(plugin.rotation().previousDay(today)) ? state.streak() + 1 : 1;
        state.setStreak(streak, today);
        plugin.rewards().give(player, plugin.catalog().streakReward(streak));
        plugin.messages().send(player, Messages.STREAK_BONUS,
                Placeholder.unparsed("streak", String.valueOf(streak)));
        play(player, plugin.settings().sounds().streak());
    }

    private boolean ignored(Player player) {
        PluginConfig.Protection protection = plugin.settings().protection();
        GameMode mode = player.getGameMode();
        return protection.ignoreCreative() && (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
    }

    private static void play(Player player, Key sound) {
        player.playSound(Sound.sound(sound, Sound.Source.PLAYER, 1f, 1f));
    }
}
