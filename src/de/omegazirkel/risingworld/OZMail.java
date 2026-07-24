package de.omegazirkel.risingworld;

import de.omegazirkel.risingworld.tools.OZLogger;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;

/** Rising World entry point; mail workflows live in {@link OZMailRuntime}. */
public final class OZMail extends OZMailRuntime implements Listener {
    public static final String COMMAND = OZMailRuntime.COMMAND;

    public record Recipient(int dbId, String name, long lastSeenEpochSeconds, boolean favorite) { }

    public static OZLogger logger() {
        return OZMailRuntime.logger();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        registerEventListener(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) { super.onPlayerCommand(event); }

    @Override @EventMethod
    public void onPlayerSpawn(PlayerSpawnEvent event) { super.onPlayerSpawn(event); }
}
