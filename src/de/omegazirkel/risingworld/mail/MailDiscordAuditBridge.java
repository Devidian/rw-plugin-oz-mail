package de.omegazirkel.risingworld.mail;

import java.lang.reflect.Method;

import net.risingworld.api.Plugin;

/** Optional reflection-only integration; OZMail stays loadable without Discord Connect. */
public final class MailDiscordAuditBridge {
    private MailDiscordAuditBridge() {
    }

    public static boolean send(Plugin owner, long channelId, String message) {
        if (owner == null || channelId <= 0L || message == null || message.isBlank()) return false;
        Plugin discord = owner.getPluginByName("OZ - Discord Connect");
        if (discord == null) return false;
        try {
            Method method = discord.getClass().getMethod("sendDiscordMessageToTextChannel", String.class,
                    long.class, byte[].class);
            method.invoke(discord, message, channelId, null);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
