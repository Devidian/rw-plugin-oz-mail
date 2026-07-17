package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProvider;
import net.risingworld.api.objects.Player;

public final class MailPluginInfoStatusProvider implements PluginInfoStatusProvider {
    private final OZMail plugin;

    public MailPluginInfoStatusProvider(OZMail plugin) { this.plugin = plugin; }
    @Override public String getPluginName() { return plugin.getDescription("name"); }
    @Override public String getInfo(Player player) {
        return plugin.text("MAIL_STATUS_INFO", player).replace("PH_VERSION", plugin.getDescription("version"));
    }
    @Override public String getStatus(Player player) {
        int used = plugin.mailboxUsage(player);
        return plugin.text("MAIL_STATUS_RUNTIME", player)
                .replace("PH_MAILBOX_USED", String.valueOf(used))
                .replace("PH_MAILBOX_CAPACITY", String.valueOf(plugin.mailboxCapacity(player)))
                .replace("PH_EXTRA_MAILBOXES", String.valueOf(plugin.extraMailboxes(player)))
                .replace("PH_ATTACHMENTS", String.valueOf(plugin.settings().maxPlayerAttachments))
                .replace("PH_COD", String.valueOf(plugin.settings().enableCod));
    }
}
