package com.mdevstudio.questboard.command;

import com.mdevstudio.questboard.Permissions;
import com.mdevstudio.questboard.Questboard;
import com.mdevstudio.questboard.config.Messages;
import com.mdevstudio.questboard.menu.QuestMenu;
import com.mdevstudio.questboard.model.Period;
import com.mdevstudio.questboard.quest.Active;
import com.mdevstudio.questboard.quest.PlayerQuests;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class QuestCommand {

    public static final String NAME = "quests";

    private static final List<String> SUBCOMMANDS = List.of("list", "claim", "track", "streak", "help",
            "reload", "reset", "complete");
    private static final String TRACK_OFF = "off";
    private static final String TRACK_AUTO = "auto";

    private final Questboard plugin;

    public QuestCommand(Questboard plugin) {
        this.plugin = plugin;
    }

    public void register(Commands registrar) {
        var root = Commands.literal(NAME)
                .requires(source -> source.getSender().hasPermission(Permissions.COMMAND + "open"))
                .executes(context -> withPlayer(context, this::open));
        for (String subcommand : SUBCOMMANDS) {
            for (String name : names(subcommand)) {
                root.then(subcommand(subcommand, name));
            }
        }
        registrar.register(root.build(), "Daily and weekly quests", plugin.settings().commandAliases());
    }

    private LiteralArgumentBuilder<CommandSourceStack> subcommand(String subcommand, String name) {
        var node = Commands.literal(name)
                .requires(source -> source.getSender().hasPermission(Permissions.COMMAND + subcommand));
        return switch (subcommand) {
            case "list" -> node.executes(context -> withPlayer(context, this::list));
            case "claim" -> node.executes(context -> withPlayer(context, this::claim));
            case "track" -> node.then(Commands.argument("quest", StringArgumentType.word())
                    .suggests(suggest(this::trackTargets))
                    .executes(context -> withPlayer(context,
                            player -> track(player, StringArgumentType.getString(context, "quest")))));
            case "streak" -> node.executes(context -> withPlayer(context, this::streak));
            case "reload" -> node.executes(context -> reload(context.getSource().getSender()));
            case "reset" -> node.then(Commands.argument("player", ArgumentTypes.player()).executes(this::reset));
            case "complete" -> node.then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.argument("quest", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                plugin.catalog().quests().forEach(quest -> builder.suggest(quest.id()));
                                return builder.buildFuture();
                            })
                            .executes(this::complete)));
            default -> node.executes(context -> help(context.getSource().getSender()));
        };
    }

    private int open(Player player) {
        new QuestMenu(plugin).open(player);
        return Command.SINGLE_SUCCESS;
    }

    private int list(Player player) {
        Messages messages = plugin.messages();
        PlayerQuests state = plugin.book().of(player);
        List<Active> all = plugin.book().active(state);
        if (all.isEmpty()) {
            messages.send(player, Messages.LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        messages.send(player, Messages.LIST_HEADER);
        Instant now = Instant.now();
        for (Period period : Period.values()) {
            List<Active> quests = plugin.book().active(state, period);
            if (quests.isEmpty() || !plugin.settings().modules().has(period)) {
                continue;
            }
            boolean daily = period == Period.DAILY;
            messages.send(player, daily ? Messages.LIST_SECTION_DAILY : Messages.LIST_SECTION_WEEKLY,
                    Placeholder.unparsed("time", messages.duration(daily
                            ? plugin.rotation().untilDaily(now) : plugin.rotation().untilWeekly(now))));
            quests.forEach(active -> messages.send(player, Messages.LIST_LINE, line(active)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private TagResolver line(Active active) {
        String status = active.entry().claimed() ? Messages.STATUS_CLAIMED
                : active.ready() ? Messages.STATUS_READY : Messages.STATUS_PROGRESS;
        Component click = plugin.messages().component(status);
        return TagResolver.resolver(
                Placeholder.component("status", active.ready()
                        ? click.clickEvent(ClickEvent.runCommand("/" + NAME + " claim")) : click),
                Placeholder.parsed("title", active.def().title()),
                Placeholder.unparsed("progress", String.valueOf(active.entry().progress())),
                Placeholder.unparsed("amount", String.valueOf(active.def().amount())));
    }

    private int claim(Player player) {
        int claimed = plugin.progress().claimAll(player);
        if (claimed == 0) {
            plugin.messages().send(player, Messages.CLAIM_NONE);
            return 0;
        }
        plugin.messages().send(player, Messages.CLAIM_ALL, Placeholder.unparsed("count", String.valueOf(claimed)));
        return Command.SINGLE_SUCCESS;
    }

    private int track(Player player, String target) {
        Messages messages = plugin.messages();
        if (!plugin.settings().modules().tracker()) {
            messages.send(player, Messages.TRACK_DISABLED);
            return 0;
        }
        PlayerQuests state = plugin.book().of(player);
        if (target.equalsIgnoreCase(TRACK_OFF)) {
            state.stopTracking();
            messages.send(player, Messages.TRACK_OFF);
        } else if (target.equalsIgnoreCase(TRACK_AUTO)) {
            state.trackAutomatically();
            messages.send(player, Messages.TRACK_AUTO);
        } else {
            Active active = plugin.book().active(state).stream()
                    .filter(candidate -> candidate.def().id().equalsIgnoreCase(target)).findFirst().orElse(null);
            if (active == null) {
                messages.send(player, Messages.TRACK_UNKNOWN, Placeholder.unparsed("quest", target));
                return 0;
            }
            state.track(active.def().id());
            messages.send(player, Messages.TRACK_SET, Placeholder.parsed("title", active.def().title()));
        }
        plugin.tracker().refresh(player);
        return Command.SINGLE_SUCCESS;
    }

    private int streak(Player player) {
        if (!plugin.settings().modules().streak() || !plugin.settings().modules().daily()) {
            plugin.messages().send(player, Messages.STREAK_DISABLED);
            return 0;
        }
        int streak = plugin.book().liveStreak(plugin.book().of(player));
        plugin.messages().send(player, streak == 0 ? Messages.STREAK_NONE : Messages.STREAK_INFO,
                Placeholder.unparsed("streak", String.valueOf(streak)));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender) {
        plugin.reload();
        plugin.messages().send(sender, Messages.RELOADED,
                Placeholder.unparsed("count", String.valueOf(plugin.catalog().quests().size())));
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player target = target(context);
        plugin.book().reroll(target);
        plugin.messages().send(context.getSource().getSender(), Messages.RESET_DONE,
                Placeholder.unparsed("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player target = target(context);
        String id = StringArgumentType.getString(context, "quest");
        Active active = plugin.book().active(plugin.book().of(target)).stream()
                .filter(candidate -> candidate.def().id().equalsIgnoreCase(id)).findFirst().orElse(null);
        CommandSender sender = context.getSource().getSender();
        if (active == null) {
            plugin.messages().send(sender, Messages.COMPLETE_UNKNOWN,
                    Placeholder.unparsed("player", target.getName()), Placeholder.unparsed("quest", id));
            return 0;
        }
        plugin.progress().forceComplete(target, active);
        plugin.messages().send(sender, Messages.COMPLETE_DONE, Placeholder.unparsed("player", target.getName()),
                Placeholder.parsed("title", active.def().title()));
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandSender sender) {
        Messages messages = plugin.messages();
        messages.send(sender, Messages.HELP_HEADER);
        for (String subcommand : SUBCOMMANDS) {
            if (!sender.hasPermission(Permissions.COMMAND + subcommand)) {
                continue;
            }
            List<String> aliases = plugin.settings().aliasesOf(subcommand);
            messages.send(sender, Messages.HELP_LINE,
                    Placeholder.unparsed("command", subcommand),
                    Placeholder.unparsed("aliases", aliases.isEmpty() ? "-" : String.join(", ", aliases)),
                    Placeholder.unparsed("description", messages.raw(Messages.HELP_PREFIX + subcommand)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private List<String> trackTargets(Player player) {
        List<String> targets = new ArrayList<>(List.of(TRACK_AUTO, TRACK_OFF));
        plugin.book().active(plugin.book().of(player)).forEach(active -> targets.add(active.def().id()));
        return targets;
    }

    private List<String> names(String subcommand) {
        List<String> names = new ArrayList<>();
        names.add(subcommand);
        for (String alias : plugin.settings().aliasesOf(subcommand)) {
            String clean = alias.toLowerCase(Locale.ROOT);
            if (!clean.isBlank() && !names.contains(clean) && !SUBCOMMANDS.contains(clean)) {
                names.add(clean);
            }
        }
        return names;
    }

    private static Player target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource()).getFirst();
    }

    private int withPlayer(CommandContext<CommandSourceStack> context, Function<Player, Integer> action) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, Messages.PLAYERS_ONLY);
            return 0;
        }
        return action.apply(player);
    }

    private static SuggestionProvider<CommandSourceStack> suggest(Function<Player, List<String>> values) {
        return (context, builder) -> {
            if (context.getSource().getSender() instanceof Player player) {
                String typed = builder.getRemainingLowerCase();
                values.apply(player).stream().filter(value -> value.startsWith(typed)).forEach(builder::suggest);
            }
            return builder.buildFuture();
        };
    }
}
