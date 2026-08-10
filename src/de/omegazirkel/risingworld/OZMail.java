package de.omegazirkel.risingworld;

import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.mail.MailService;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;

/** Rising World entry point; mail workflows live in {@link OZMailRuntime}. */
public final class OZMail extends OZMailRuntime implements Listener {
    public static final String COMMAND = OZMailRuntime.COMMAND;

    public record Recipient(int dbId, String name, long lastSeenEpochSeconds, boolean favorite, boolean admin) {
        public Recipient(int dbId, String name, long lastSeenEpochSeconds, boolean favorite) {
            this(dbId, name, lastSeenEpochSeconds, favorite, false);
        }
    }

    public static OZLogger logger() {
        return OZMailRuntime.logger();
    }

    /** Thin public compatibility facade used by the reflection-only Tools bridge. */
    @Override
    public MailService.MailSendResult sendPluginMail(String senderPlugin, int recipientDbId, String recipientName,
            String subject, String body, String callerCorrelationId) {
        return super.sendPluginMail(senderPlugin, recipientDbId, recipientName, subject, body, callerCorrelationId);
    }

    /** Thin public compatibility facade used by the reflection-only Tools bridge. */
    @Override
    public MailService.MailSendResult sendPluginSystemReport(String senderPlugin, int recipientDbId,
            String recipientName, String subject, String body, String callerCorrelationId) {
        return super.sendPluginSystemReport(senderPlugin, recipientDbId, recipientName, subject, body,
                callerCorrelationId);
    }

    /** Thin public compatibility facade used by the reflection-only Tools bridge. */
    @Override
    public boolean canReceivePluginMail(int recipientDbId) {
        return super.canReceivePluginMail(recipientDbId);
    }

    /** Thin public compatibility facade used by the reflection-only Tools bridge. */
    @Override
    public MailService.MailSendResult sendPluginMailWithAttachments(String senderPlugin, int recipientDbId,
            String recipientName, String subject, String body, String callerCorrelationId, String[] itemNames,
            int[] variants, int[] amounts, int[] durabilities, short[] statuses, String[] modifiers, int[] colors) {
        return super.sendPluginMailWithAttachments(senderPlugin, recipientDbId, recipientName, subject, body,
                callerCorrelationId, itemNames, variants, amounts, durabilities, statuses, modifiers, colors);
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
