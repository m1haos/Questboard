package com.mdevstudio.questboard;

import com.mdevstudio.questboard.command.QuestCommand;
import com.mdevstudio.questboard.config.Messages;
import com.mdevstudio.questboard.config.PluginConfig;
import com.mdevstudio.questboard.config.QuestCatalog;
import com.mdevstudio.questboard.listener.GoalListener;
import com.mdevstudio.questboard.listener.SessionListener;
import com.mdevstudio.questboard.listener.TravelTracker;
import com.mdevstudio.questboard.menu.MenuListener;
import com.mdevstudio.questboard.quest.Progress;
import com.mdevstudio.questboard.quest.QuestBook;
import com.mdevstudio.questboard.quest.Rewards;
import com.mdevstudio.questboard.quest.Rotation;
import com.mdevstudio.questboard.quest.Tracker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public class Questboard extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20;
    private static final long TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND;

    private PluginConfig settings;
    private Messages messages;
    private QuestCatalog catalog;
    private Rotation rotation;
    private Tasks tasks;
    private QuestBook book;
    private Progress progress;
    private Rewards rewards;
    private Tracker tracker;
    private TravelTracker travel;

    @Override
    public void onEnable() {
        loadFiles();
        tasks = new Tasks(this);
        book = new QuestBook(this);
        progress = new Progress(this);
        rewards = new Rewards(this);
        tracker = new Tracker(this);
        travel = new TravelTracker(this);

        getServer().getPluginManager().registerEvents(new GoalListener(this), this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new QuestCommand(this).register(event.registrar()));

        getServer().getOnlinePlayers().forEach(book::join);
        tasks.every(TICKS_PER_SECOND, travel::sample);
        tasks.every(TICKS_PER_MINUTE, book::upkeep);
        getSLF4JLogger().info("{} quests in the pool.", catalog.quests().size());
    }

    @Override
    public void onDisable() {
        if (book == null) {
            return;
        }
        tasks.cancelAll();
        book.saveAll();
        tracker.hideAll();
    }

    public void reload() {
        loadFiles();
        book.refreshOnline();
    }

    private void loadFiles() {
        settings = PluginConfig.load(this);
        messages = Messages.load(this, settings.language());
        catalog = QuestCatalog.load(this, settings.language());
        rotation = new Rotation(settings.schedule());
    }

    public PluginConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public QuestCatalog catalog() {
        return catalog;
    }

    public Rotation rotation() {
        return rotation;
    }

    public Tasks tasks() {
        return tasks;
    }

    public QuestBook book() {
        return book;
    }

    public Progress progress() {
        return progress;
    }

    public Rewards rewards() {
        return rewards;
    }

    public Tracker tracker() {
        return tracker;
    }

    public TravelTracker travel() {
        return travel;
    }
}
