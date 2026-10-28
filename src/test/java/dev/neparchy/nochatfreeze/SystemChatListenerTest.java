package dev.neparchy.nochatfreeze;

import com.destroystokyo.paper.ClientOption;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemChatListenerTest {
    /** Renders text the way an English client shows it, including vanilla's "<%s> %s" chat format. */
    private static final PlainTextComponentSerializer CLIENT_TEXT = PlainTextComponentSerializer.builder()
            .flattener(ComponentFlattener.basic().toBuilder()
                    .complexMapper(TranslatableComponent.class, (component, out) -> {
                        assertEquals("chat.type.text", component.key());
                        out.accept(Component.text("<"));
                        out.accept(component.arguments().get(0).asComponent());
                        out.accept(Component.text("> "));
                        out.accept(component.arguments().get(1).asComponent());
                    })
                    .build())
            .build();

    private final SystemChatListener listener = new SystemChatListener();

    private Player player(ClientOption.ChatVisibility visibility) {
        Player player = mock(Player.class);
        when(player.getClientOption(ClientOption.CHAT_VISIBILITY)).thenReturn(visibility);
        return player;
    }

    private AsyncChatEvent event(Player source, ChatRenderer renderer, Audience... viewers) {
        when(source.displayName()).thenReturn(Component.text("Alice"));
        Component message = Component.text("hello");
        return new AsyncChatEvent(true, source, new HashSet<>(List.of(viewers)), renderer, message, message,
                mock(SignedMessage.class));
    }

    private static String received(Audience viewer) {
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(viewer).sendMessage(sent.capture());
        return CLIENT_TEXT.serialize(sent.getValue());
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
    void defaultFormatLooksUnchangedButNoLongerMatchesThePlayerName() {
        Player source = player(ClientOption.ChatVisibility.FULL);
        Player other = player(ClientOption.ChatVisibility.FULL);
        AsyncChatEvent event = event(source, ChatRenderer.defaultRenderer(), source, other);

        listener.onChat(event);

        for (Player viewer : List.of(source, other)) {
            String text = received(viewer);
            assertEquals("<Alice> hello", visible(text));
            assertNotEquals("Alice", clientGuessedName(text), "the client would look Alice up in its block list");
        }
    }

    @Test
    void defaultFormatWithTheConsoleListening() {
        // Paper's default renderer renders once and hands every viewer that same line, so the console's
        // copy must not be rendered with a different name than the players' copies.
        Player source = player(ClientOption.ChatVisibility.FULL);
        Player other = player(ClientOption.ChatVisibility.FULL);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        AsyncChatEvent event = event(source, ChatRenderer.defaultRenderer(), console, source, other);

        listener.onChat(event);

        for (Player viewer : List.of(source, other)) {
            String text = received(viewer);
            assertEquals("<Alice> hello", visible(text));
            assertNotEquals("Alice", clientGuessedName(text), "the client would look Alice up in its block list");
        }
        assertEquals("<Alice> hello", received(console), "the console log must not contain the invisible character");
    }

    @Test
    void formatsThatWriteTheNameThemselvesAreCoveredToo() {
        Player source = player(ClientOption.ChatVisibility.FULL);
        // Like Paper's legacy-format renderer, which ignores the display name it is given.
        ChatRenderer legacyStyle = (sender, name, message, viewer) ->
                Component.text("[Owner] <Alice> ").append(message);
        AsyncChatEvent event = event(source, legacyStyle, source);

        listener.onChat(event);

        String text = received(source);
        assertEquals("[Owner] <Alice> hello", visible(text));
        assertNotEquals("Alice", clientGuessedName(text));
    }

    @Test
    void playersGetTheirOwnRenderingAndTheConsoleLogsWithoutTheNotSecureTag() {
        Player source = player(ClientOption.ChatVisibility.FULL);
        Player other = player(ClientOption.ChatVisibility.FULL);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        Audience otherAudience = mock(Audience.class);
        ChatRenderer renderer = (sender, name, message, viewer) -> Component.text()
                .append(name).append(Component.text(viewer == other ? " to other: " : " to self: ")).append(message).build();
        AsyncChatEvent event = event(source, renderer, source, other, console, otherAudience);

        listener.onChat(event);

        assertEquals(Set.of(otherAudience), event.viewers(), "Paper would tag console chat with [Not Secure]");
        assertEquals("Alice to self: hello", visible(received(source)));
        assertEquals("Alice to other: hello", visible(received(other)));
        // The console gets the plain name, without the invisible character.
        assertEquals("Alice to self: hello", received(console));
    }

    @Test
    void commandsOnlyAndHiddenChatSettingsAreRespected() {
        Player source = player(ClientOption.ChatVisibility.FULL);
        Player commandsOnly = player(ClientOption.ChatVisibility.SYSTEM);
        Player hidden = player(ClientOption.ChatVisibility.HIDDEN);
        AsyncChatEvent event = event(source, ChatRenderer.defaultRenderer(), source, commandsOnly, hidden);

        listener.onChat(event);

        assertTrue(event.viewers().isEmpty(), "Players who opted out must not get the original message either");
        verify(commandsOnly, never()).sendMessage(any(Component.class));
        verify(hidden, never()).sendMessage(any(Component.class));
        verify(source).sendMessage(any(Component.class));
    }

    @Test
    void runsAfterAllOtherListenersAndSkipsCancelledChat() throws NoSuchMethodException {
        // Running earlier could deliver a message that AuthMe or a private team chat cancels later.
        EventHandler handler = SystemChatListener.class.getMethod("onChat", AsyncChatEvent.class)
                .getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }
}
