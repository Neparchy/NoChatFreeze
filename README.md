<div align="center">

# NoChatFreeze

**Stop your players' games from freezing every time someone chats on an offline-mode server.**

[![Paper 1.21.11](https://img.shields.io/badge/Paper-1.21.11-blue)](https://papermc.io)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net)
[![License: MIT](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

---

## Why you need it

On offline-mode (cracked) servers, **every player's game hangs for a moment whenever anyone sends a chat message or a whisper.** Your TPS looks fine because the freeze happens inside each player's game, not on the server.

NoChatFreeze fixes this without any setup: no config, no commands to learn, no permissions to set.

## Quick start

1. Download `NoChatFreeze.jar` from [**Releases**](https://github.com/Neparchy/NoChatFreeze/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.

To uninstall, delete the jar and restart. The plugin stores no data.

## What it fixes

| | Without NoChatFreeze | With NoChatFreeze |
|---|---|---|
| Public chat | Everyone's game freezes | Smooth |
| `/msg`, `/tell`, `/w` | The recipient's game freezes | Smooth |
| Console | Every chat line tagged `[Not Secure]` | Clean chat log |
| How chat looks | `<Alice> hi` | `<Alice> hi`, unchanged |

## How it works

When a Java client shows a message from another player, it checks that player against the account's Mojang block list. **Offline-mode accounts can't download that list**, so the client keeps retrying on the main game thread and freezes until the attempt fails. It runs this check:

- on every **player chat** message, and
- on **system messages** whose text between the first `<` and `>` is an online player's name. This happens with "Hide Matched Names", which is on by default.

NoChatFreeze avoids both cases:

1. **Public chat and whispers are sent as system messages**, so the player-chat check never runs.
2. **An invisible zero-width character goes after `<`** in names. Chat looks identical, but the name no longer matches a player, so the system-message check is skipped.
3. **`/msg`, `/tell` and `/w` take plain text**, so the sender's game has nothing to sign either.

## Features

- **Keeps your chat formatting.** It runs after every other plugin, so your chat plugin's format stays.
- **Respects other plugins.** Messages that other plugins cancel (for example AuthMe blocking players who haven't logged in, or a private team chat) are never sent.
- **Vanilla-style whispers.**
  - Same wording and gray italic style.
  - Player names and selectors as targets.
  - Click a name to reply.
  - Same `minecraft.command.msg` permission.
- **Respects chat settings.** Players with chat set to "Commands Only" or "Hidden" still don't receive chat or whispers.
- **Clean console.** No more `[Not Secure]` on every line.

## Compatibility

| Software | Status |
|---|---|
| Paper 1.21.11 | ✅ Supported |
| Paper forks (Purpur, Pufferfish, ...) | Should work, untested |
| ViaVersion, ViaBackwards, ViaRewind | ✅ Tested with clients from 1.8 to 26.1 |
| Chat formatting plugins (Paper or Bukkit chat event) | ✅ Supported |
| Geyser (Bedrock players) | Should work, untested |
| Online-mode servers | Not needed; online-mode players don't freeze |

> [!IMPORTANT]
> ViaVersion and ViaBackwards must be **matching versions**. If they don't match, older clients can't join at all, with or without this plugin.

## Good to know

**Trade-offs:**
- Chat can't be reported to Mojang.
- The "Not Secure" markers next to chat messages disappear.
- The in-game "block player" and "hide player" options no longer hide that player's messages.
- Text copied from chat contains the invisible character after `<`.
- Very old clients may draw the invisible character as a small box. Clients from 1.21.1 onward were checked and draw it with zero width.

**Not covered:**
- `/me`, `/say`, `/teammsg`, and the namespaced `/minecraft:msg`, `/minecraft:tell` and `/minecraft:w`, still use vanilla chat, so they can still freeze their recipients.
- Selectors inside whisper text (`/msg Bob hi @a`) aren't expanded, unlike vanilla for operators.
- A short hitch for the *sender* when pressing Enter comes from their own game. No server plugin can fix that; client mods such as Anti Chat Lag can.

## FAQ

<details>
<summary><b>Does this lower my server's TPS?</b></summary>

No. It only changes how chat is sent. It adds no scheduled tasks and no data storage.
</details>

<details>
<summary><b>Will my players notice anything different?</b></summary>

Only that the freezing stops. Chat and whispers look the same as vanilla.
</details>

<details>
<summary><b>Can I still use vanilla's /msg?</b></summary>

Yes, as `/minecraft:msg`. It sends normal player chat, though, so its recipients can still freeze.
</details>

## Building from source

Requires Java 21 or newer. The Gradle wrapper is included.

```bash
./build.sh
```

This runs the tests and writes `NoChatFreeze.jar` to the project folder. To learn how it works internally or to contribute, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Released under the [MIT License](LICENSE).
