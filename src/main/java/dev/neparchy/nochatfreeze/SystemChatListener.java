package dev.neparchy.nochatfreeze;

import com.destroystokyo.paper.ClientOption;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Delivers public chat to players as system messages instead of player chat messages.
 *
 * <p>On offline-mode servers, a Java client that receives chat from a known player checks its Mojang
 * block list, and fetching that list can freeze the receiving client's game until it fails. Player chat
 * always triggers the check. System messages trigger it too when "Hide Matched Names" is on (the
 * default): the client takes the text between the first {@code <} and {@code >}, and if it is the name
 * of a player in the tab list, it checks that player. So the name inside the brackets is broken up with
 * an invisible zero-width character before sending.
 *
 * <p>Runs at MONITOR so every other plugin has already formatted the message or cancelled it (AuthMe
 * login checks, private team chat); cancelled messages are never delivered here. Running earlier could
 * leak a message another plugin cancels later. The console is also served here, because Paper tags
 * every unsigned message it logs with "[Not Secure]"; other audiences stay in the event.
 */
final class SystemChatListener implements Listener {
    /** Zero-width non-joiner: the vanilla font draws it with zero width. */
    static final String INVISIBLE = "‌";

    private static final TextReplacementConfig BREAK_FIRST_BRACKET = TextReplacementConfig.builder()
            .matchLiteral("<")
            .replacement("<" + INVISIBLE)
            .once()
            .build();

    /** The console log needs no protection, so it stays free of the invisible character. */
    private static final TextReplacementConfig REMOVE_INVISIBLE = TextReplacementConfig.builder()
            .matchLiteral(INVISIBLE)
            .replacement("")
            .build();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Set<Audience> viewers = event.viewers();
        List<Player> players = new ArrayList<>();
        List<ConsoleCommandSender> consoles = new ArrayList<>();
        for (Audience viewer : viewers) {
            if (viewer instanceof Player player) {
                players.add(player);
            } else if (viewer instanceof ConsoleCommandSender console) {
                consoles.add(console);
            }
        }
        if (players.isEmpty() && consoles.isEmpty()) {
            return;
        }
        // Paper must not also send these viewers the original player chat message.
        viewers.removeAll(players);
        viewers.removeAll(consoles);

        Player source = event.getPlayer();
        // Every viewer gets the same name: Paper's default renderer renders the first call's arguments
        // once and returns that line to every later viewer.
        Component unmatchableName = Component.text(INVISIBLE).append(source.displayName());
        Component message = event.message();
        ChatRenderer renderer = event.renderer();
        for (ConsoleCommandSender console : consoles) {
            console.sendMessage(renderer.render(source, unmatchableName, message, console).replaceText(REMOVE_INVISIBLE));
        }
        for (Player player : players) {
            if (acceptsChat(player)) {
                player.sendMessage(breakNameMatch(renderer.render(source, unmatchableName, message, player)));
            }
        }
    }

    /**
     * Covers renderers that write the name themselves instead of using the display name they are given
     * (Paper's legacy-format renderer, chat plugins built on the old chat event). Breaking the first
     * literal {@code <} is enough, because the client only looks at the first bracket pair.
     */
    static Component breakNameMatch(Component rendered) {
        return rendered.replaceText(BREAK_FIRST_BRACKET);
    }

    /**
     * Vanilla only delivers player chat to clients with chat set to "Shown". System messages would also
     * reach "Commands Only", so those players are skipped to keep their setting meaningful.
     */
    static boolean acceptsChat(Player player) {
        ClientOption.ChatVisibility visibility = player.getClientOption(ClientOption.CHAT_VISIBILITY);
        return visibility != ClientOption.ChatVisibility.SYSTEM && visibility != ClientOption.ChatVisibility.HIDDEN;
    }
}
