package dev.neparchy.nochatfreeze;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * Replaces vanilla {@code /msg}, {@code /tell} and {@code /w}, which send whispers as player chat and
 * freeze offline-mode recipients the same way public chat did. Whispers are sent as system messages
 * with vanilla's wording and colours, and the message is a plain string argument instead of vanilla's
 * signed message argument, so the sender's client has nothing to sign.
 *
 * <p>Vanilla's commands stay reachable as {@code /minecraft:msg} and so on.
 */
final class WhisperCommand {
    static final List<String> LABELS = List.of("msg", "tell", "w");
    /** Same permission as the vanilla command, so servers that restricted /msg stay restricted. */
    private static final String PERMISSION = "minecraft.command.msg";
    private static final Style WHISPER_STYLE = Style.style(NamedTextColor.GRAY, TextDecoration.ITALIC);

    private WhisperCommand() {
    }

    static LiteralCommandNode<CommandSourceStack> node(String label) {
        return Commands.literal(label)
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    List<Player> targets = context.getArgument("targets", PlayerSelectorArgumentResolver.class)
                                            .resolve(source);
                                    whisper(source.getSender(), senderName(source), targets,
                                            StringArgumentType.getString(context, "message"));
                                    return targets.isEmpty() ? 0 : targets.size();
                                })))
                .build();
    }

    static void whisper(CommandSender sender, Component senderName, Collection<Player> targets, String text) {
        Component message = Component.text(text);
        for (Player target : targets) {
            if (SystemChatListener.acceptsChat(target)) {
                target.sendMessage(SystemChatListener.breakNameMatch(Component.translatable(
                        "commands.message.display.incoming", WHISPER_STYLE, senderName, message)));
            }
            sender.sendMessage(SystemChatListener.breakNameMatch(Component.translatable(
                    "commands.message.display.outgoing", WHISPER_STYLE, playerName(target), message)));
        }
    }

    /** Vanilla names the executing entity, so {@code /execute as} works, and the console "Server". */
    private static Component senderName(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (executor instanceof Player player) {
            return playerName(player);
        }
        if (executor != null) {
            return executor.name();
        }
        CommandSender sender = source.getSender();
        return sender instanceof ConsoleCommandSender ? Component.text("Server") : sender.name();
    }

    /** Like vanilla: hovering shows the player, clicking starts a reply. */
    private static Component playerName(Player player) {
        return player.displayName()
                .hoverEvent(player)
                .clickEvent(ClickEvent.suggestCommand("/tell " + player.getName() + " "));
    }
}
