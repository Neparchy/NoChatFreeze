package dev.neparchy.nochatfreeze;

import com.destroystokyo.paper.ClientOption;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhisperCommandTest {
    private static final Map<String, String> ENGLISH = Map.of(
            "commands.message.display.incoming", "%s whispers to you: %s",
            "commands.message.display.outgoing", "You whisper to %s: %s");

    /** Renders text the way an English client shows it. */
    private static final PlainTextComponentSerializer CLIENT_TEXT = PlainTextComponentSerializer.builder()
            .flattener(ComponentFlattener.basic().toBuilder()
                    .complexMapper(TranslatableComponent.class, (component, out) -> {
                        String[] parts = ENGLISH.get(component.key()).split("%s", -1);
                        for (int i = 0; i < parts.length; i++) {
                            out.accept(Component.text(parts[i]));
                            if (i < component.arguments().size()) {
                                out.accept(component.arguments().get(i).asComponent());
                            }
                        }
                    })
                    .build())
            .build();

    private static Player player(String name, ClientOption.ChatVisibility visibility) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.displayName()).thenReturn(Component.text(name));
        when(player.getClientOption(ClientOption.CHAT_VISIBILITY)).thenReturn(visibility);
        return player;
    }

    private static List<String> received(CommandSender viewer, int times) {
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(viewer, times(times)).sendMessage(sent.capture());
        return sent.getAllValues().stream().map(CLIENT_TEXT::serialize).toList();
    }

    /** The name the client checks against its block list: the text between the first '<' and '>'. */
    private static String clientGuessedName(String text) {
        int open = text.indexOf('<');
        int close = open < 0 ? -1 : text.indexOf('>', open + 1);
        return close < 0 ? null : text.substring(open + 1, close);
    }

    private static String visible(String text) {
        return text.replace(SystemChatListener.INVISIBLE, "");
    }

    @Test
    void whispersAreSystemMessagesInVanillaWording() {
        Player alice = player("Alice", ClientOption.ChatVisibility.FULL);
        Player bob = player("Bob", ClientOption.ChatVisibility.FULL);

        WhisperCommand.whisper(alice, Component.text("Alice"), List.of(bob), "hi <Bob>");

        String incoming = received(bob, 1).getFirst();
        String outgoing = received(alice, 1).getFirst();
        assertEquals("Alice whispers to you: hi <Bob>", visible(incoming));
        assertEquals("You whisper to Bob: hi <Bob>", visible(outgoing));
        assertNotEquals("Bob", clientGuessedName(incoming), "the client would look Bob up in its block list");
        assertNotEquals("Bob", clientGuessedName(outgoing));
    }

    @Test
    void everyTargetGetsTheWhisperAndTheSenderAnEchoPerTarget() {
        CommandSender console = mock(CommandSender.class);
        Player bob = player("Bob", ClientOption.ChatVisibility.FULL);
        Player carol = player("Carol", ClientOption.ChatVisibility.FULL);

        WhisperCommand.whisper(console, Component.text("Server"), List.of(bob, carol), "restart soon");

        assertEquals(List.of("Server whispers to you: restart soon"), received(bob, 1));
        assertEquals(List.of("Server whispers to you: restart soon"), received(carol, 1));
        assertEquals(List.of("You whisper to Bob: restart soon", "You whisper to Carol: restart soon"),
                received(console, 2));
    }

    @Test
    void playersWithChatOffDoNotReceiveWhispers() {
        Player alice = player("Alice", ClientOption.ChatVisibility.FULL);
        Player commandsOnly = player("Bob", ClientOption.ChatVisibility.SYSTEM);
        Player hidden = player("Carol", ClientOption.ChatVisibility.HIDDEN);

        WhisperCommand.whisper(alice, Component.text("Alice"), List.of(commandsOnly, hidden), "hi");

        verify(commandsOnly, never()).sendMessage(any(Component.class));
        verify(hidden, never()).sendMessage(any(Component.class));
        assertEquals(2, received(alice, 2).size(), "vanilla still echoes to the sender");
    }
}
