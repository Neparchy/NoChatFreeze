# Contributing to NoChatFreeze

Bug reports and pull requests are welcome. Please describe the server version, client versions, and other chat-related plugins involved.

## How the client freeze works

From the vanilla client code (`ChatListener`, authlib `YggdrasilUserApiService`):

- **Player chat:** always calls `Minecraft#isBlocked(sender)`.
- **System chat:** with "Hide Matched Names" on (the default), `guessChatUUID` takes the text between the first `<` and `>`, looks it up among tab-list names, and calls `isBlocked` on a match. With no match it gets `NIL_UUID`, which returns early without touching the block list.
- On offline-mode accounts the block list never loads, so `isBlocked` tries to fetch it again on the game thread, which causes the freeze.

## Rules to keep

`SystemChatListener` (public chat):

- Handle `AsyncChatEvent` at `EventPriority.MONITOR` with `ignoreCancelled = true`. Running earlier could deliver a message that another plugin cancels later, such as an auth plugin or a private team chat.
- Remove `Player` and `ConsoleCommandSender` viewers from `event.viewers()`, and leave other audiences in place. Send the console the rendered line directly, because Paper tags unsigned chat it logs with `[Not Secure]`.
- Render with `event.renderer()` and send with `Player#sendMessage(Component)`, which is a system message. Never re-send as player chat or as signed messages.
- Pass **the same** display name to every `render` call. Paper's default renderer renders once and returns that line to every later viewer, so the name must already contain the invisible character. Strip the character from the console's copy afterwards.
- Break the name match with U+200C, which the vanilla font draws at zero width. Prefix the display name with it, and insert it after the first literal `<` for renderers that write the name themselves.
- Skip players whose `ClientOption.CHAT_VISIBILITY` is `SYSTEM` or `HIDDEN`, as vanilla does for player chat.

`WhisperCommand` (`/msg`, `/tell`, `/w`, registered through Paper's Brigadier lifecycle API; vanilla stays at `minecraft:msg`):

- Keep the message argument a `StringArgumentType.greedyString()`. Never use `ArgumentTypes.signedMessage()`, because clients sign arguments of vanilla's message type.
- Send with `sendMessage(Component)` using vanilla's `commands.message.display.incoming` and `commands.message.display.outgoing` keys, in gray italic. Pass the result through `SystemChatListener.breakNameMatch`, and skip recipients that fail `SystemChatListener.acceptsChat`.

Vanilla `/me`, `/say`, `/teammsg` and the namespaced `minecraft:` whisper commands still send player chat. Covering them would need packet-level conversion (server internals or a packet library). Document any change to that scope in `README.md`.

## Building and testing

```bash
./build.sh
```

The build uses Gradle with a Java 21 target and Paper API `1.21.11-R0.1-SNAPSHOT`. Tests live in `src/test/java/dev/neparchy/nochatfreeze/`. They mock Bukkit to cover the listener and whisper logic, and check the text a client would read along with the name it would look up. They don't cover command registration or real clients, so test those on a server.

Don't commit build output (`build/`, or `*.jar` other than the Gradle wrapper jar).
