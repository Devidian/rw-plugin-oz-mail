package de.omegazirkel.risingworld.mail;

import de.omegazirkel.risingworld.tools.bridge.DiscordBridge;
import net.risingworld.api.Plugin;

/** Optional reflection-only integration; OZMail stays loadable without Discord Connect. */
public final class MailDiscordAuditBridge {
    private MailDiscordAuditBridge() {
    }

    public static boolean send(Plugin owner, long channelId, String message) {
        return new DiscordBridge(owner).sendTextMessage(message, channelId);
    }
}
