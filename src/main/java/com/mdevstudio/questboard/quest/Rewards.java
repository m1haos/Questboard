package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.config.Messages;
import com.mdevstudio.questboard.model.Reward;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Rewards {

    private final Questboard plugin;

    public Rewards(Questboard plugin) {
        this.plugin = plugin;
    }

    public void give(Player player, List<Reward> rewards) {
        for (Reward reward : rewards) {
            switch (reward) {
                case Reward.Item item -> giveItem(player, item);
                case Reward.Experience experience -> player.giveExp(experience.points());
                case Reward.Command command -> plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), command.line().replace("<player>", player.getName()));
            }
        }
    }

    public Component describe(Reward reward) {
        Messages messages = plugin.messages();
        return switch (reward) {
            case Reward.Item item -> messages.component(Messages.REWARD_ITEM,
                    Placeholder.unparsed("amount", String.valueOf(item.amount())),
                    Placeholder.component("item", Component.translatable(item.material().translationKey())));
            case Reward.Experience experience -> messages.component(Messages.REWARD_XP,
                    Placeholder.unparsed("amount", String.valueOf(experience.points())));
            case Reward.Command command -> messages.component(Messages.REWARD_COMMAND,
                    Placeholder.parsed("label", command.label()));
        };
    }

    private void giveItem(Player player, Reward.Item reward) {
        int left = reward.amount();
        int stackSize = reward.material().getMaxStackSize();
        while (left > 0) {
            ItemStack stack = new ItemStack(reward.material(), Math.min(left, stackSize));
            left -= stack.getAmount();
            // Whatever does not fit goes to the floor so a full inventory never eats a reward.
            player.getInventory().addItem(stack).values().forEach(
                    rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }
}
