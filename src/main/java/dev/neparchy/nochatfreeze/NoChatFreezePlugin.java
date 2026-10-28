package dev.neparchy.nochatfreeze;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class NoChatFreezePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new SystemChatListener(), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (String label : WhisperCommand.LABELS) {
                event.registrar().register(WhisperCommand.node(label), "Sends a private message to one or more players");
            }
        });
        getLogger().info("Public chat and /msg, /tell, /w are delivered to players as system messages.");
    }
}
