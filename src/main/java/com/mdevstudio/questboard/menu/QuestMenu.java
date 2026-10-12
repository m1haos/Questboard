package com.mdevstudio.questboard.menu;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.config.Messages;
import com.mdevstudio.questboard.model.Period;
import com.mdevstudio.questboard.model.Reward;
import com.mdevstudio.questboard.quest.Active;
import com.mdevstudio.questboard.quest.PlayerQuests;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

public final class QuestMenu implements InventoryHolder {

    private static final int SIZE = 45;
    private static final int ROW = 9;
    private static final int BAR_SEGMENTS = 10;
    private static final int[][] OFFSETS = {{}, {4}, {3, 5}, {2, 4, 6}, {1, 3, 5, 7}, {0, 2, 4, 6, 8}};
    private static final int DAILY_HEADER = 4;
    private static final int WEEKLY_HEADER = 22;
    private static final int STREAK_SLOT = 40;
    private static final TextColor ACCENT = TextColor.color(0x7FB3FF);
    private static final TextColor SUCCESS = TextColor.color(0x8BD17C);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Questboard plugin;
    private final Inventory inventory;
    private final Map<Integer, String> questSlots = new HashMap<>();

    public QuestMenu(Questboard plugin) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE,
                plugin.messages().component(Messages.MENU_TITLE));
    }

    public void open(Player player) {
        render(player);
        player.openInventory(inventory);
    }

    public void click(Player player, int slot) {
        String id = questSlots.get(slot);
        PlayerQuests state = plugin.book().of(player);
        Active active = id == null ? null : plugin.book().active(state).stream()
                .filter(candidate -> candidate.def().id().equals(id)).findFirst().orElse(null);
        if (active == null) {
            return;
        }
        if (active.ready()) {
            plugin.progress().claim(player, active);
        } else if (!active.done() && plugin.settings().modules().tracker()) {
            state.track(id);
            plugin.tracker().refresh(player);
        }
        render(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render(Player player) {
        PlayerQuests state = plugin.book().of(player);
        Instant now = Instant.now();
        questSlots.clear();
        inventory.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (plugin.settings().modules().daily()) {
            inventory.setItem(DAILY_HEADER, header(Material.CLOCK, Messages.MENU_HEADER_DAILY,
                    plugin.messages().duration(plugin.rotation().untilDaily(now))));
            place(state, Period.DAILY, 1);
        }
        if (plugin.settings().modules().weekly()) {
            inventory.setItem(WEEKLY_HEADER, header(Material.COMPASS, Messages.MENU_HEADER_WEEKLY,
                    plugin.messages().duration(plugin.rotation().untilWeekly(now))));
            place(state, Period.WEEKLY, 3);
        }
        if (plugin.settings().modules().streak() && plugin.settings().modules().daily()) {
            inventory.setItem(STREAK_SLOT, streakItem(state));
        }
    }

    private void place(PlayerQuests state, Period period, int row) {
        List<Active> quests = plugin.book().active(state, period);
        int[] offsets = OFFSETS[Math.min(quests.size(), OFFSETS.length - 1)];
        for (int i = 0; i < offsets.length; i++) {
            int slot = row * ROW + offsets[i];
            questSlots.put(slot, quests.get(i).def().id());
            inventory.setItem(slot, questItem(quests.get(i), state));
        }
    }

    private ItemStack header(Material icon, String nameKey, String resetIn) {
        Messages messages = plugin.messages();
        return item(icon, messages.component(nameKey),
                List.of(messages.component(Messages.MENU_RESET_IN, Placeholder.unparsed("time", resetIn))));
    }

    private ItemStack streakItem(PlayerQuests state) {
        Messages messages = plugin.messages();
        int streak = plugin.book().liveStreak(state);
        List<Component> lore = new ArrayList<>();
        List<Reward> next = plugin.catalog().streakReward(streak + 1);
        if (!next.isEmpty()) {
            lore.add(messages.component(Messages.MENU_STREAK_NEXT,
                    Placeholder.unparsed("day", String.valueOf(streak + 1))));
            next.forEach(reward -> lore.add(plugin.rewards().describe(reward)));
        }
        return item(Material.CAMPFIRE,
                messages.component(Messages.MENU_STREAK, Placeholder.unparsed("streak", String.valueOf(streak))), lore);
    }

    private ItemStack questItem(Active active, PlayerQuests state) {
        Messages messages = plugin.messages();
        String nameKey = active.entry().claimed() ? Messages.QUEST_NAME_CLAIMED
                : active.ready() ? Messages.QUEST_NAME_READY : Messages.QUEST_NAME_ACTIVE;
        List<Component> lore = new ArrayList<>();
        for (String line : active.def().description().split("\n")) {
            lore.add(MINI_MESSAGE.deserialize("<gray>" + line));
        }
        lore.add(Component.empty());
        lore.add(messages.component(Messages.MENU_PROGRESS,
                Placeholder.component("bar", bar(active)),
                Placeholder.unparsed("progress", String.valueOf(active.entry().progress())),
                Placeholder.unparsed("amount", String.valueOf(active.def().amount()))));
        lore.add(Component.empty());
        lore.add(messages.component(Messages.MENU_REWARDS));
        active.def().rewards().forEach(reward -> lore.add(plugin.rewards().describe(reward)));
        String hint = hint(active, state);
        if (hint != null) {
            lore.add(Component.empty());
            lore.add(messages.component(hint));
        }
        ItemStack stack = item(active.def().icon(),
                messages.component(nameKey, Placeholder.parsed("title", active.def().title())), lore);
        if (active.ready()) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private @Nullable String hint(Active active, PlayerQuests state) {
        if (active.entry().claimed()) {
            return Messages.MENU_CLAIMED;
        }
        if (active.ready()) {
            return Messages.MENU_HINT_CLAIM;
        }
        if (!plugin.settings().modules().tracker()) {
            return null;
        }
        return active.def().id().equals(state.tracked()) ? Messages.MENU_HINT_TRACKED : Messages.MENU_HINT_TRACK;
    }

    private static Component bar(Active active) {
        int filled = Math.round(active.ratio() * BAR_SEGMENTS);
        TextColor color = active.done() ? SUCCESS : ACCENT;
        return Component.text("▰".repeat(filled), color)
                .append(Component.text("▱".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}
