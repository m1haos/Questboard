package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.config.Messages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public final class Tracker {

    private final Questboard plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public Tracker(Questboard plugin) {
        this.plugin = plugin;
    }

    public void refresh(Player player) {
        PlayerQuests state = plugin.book().of(player);
        Active shown = plugin.settings().modules().tracker() && !state.trackerOff() ? choose(state) : null;
        if (shown == null) {
            hide(player);
            return;
        }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 0f, plugin.settings().tracker().color(),
                        BossBar.Overlay.NOTCHED_10));
        bar.name(plugin.messages().component(Messages.TRACKER_TITLE,
                Placeholder.parsed("title", shown.def().title()),
                Placeholder.unparsed("progress", String.valueOf(shown.entry().progress())),
                Placeholder.unparsed("amount", String.valueOf(shown.def().amount()))));
        bar.progress(shown.ratio());
        bar.color(plugin.settings().tracker().color());
        player.showBossBar(bar);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void forget(Player player) {
        bars.remove(player.getUniqueId());
    }

    public void hideAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::hide);
    }

    // The pinned quest wins; otherwise the unfinished one that is closest to done.
    private @Nullable Active choose(PlayerQuests state) {
        Active best = null;
        for (Active active : plugin.book().active(state)) {
            if (active.done()) {
                continue;
            }
            if (active.def().id().equals(state.tracked())) {
                return active;
            }
            if (plugin.settings().tracker().autoPick() && (best == null || active.ratio() > best.ratio())) {
                best = active;
            }
        }
        return best;
    }
}
