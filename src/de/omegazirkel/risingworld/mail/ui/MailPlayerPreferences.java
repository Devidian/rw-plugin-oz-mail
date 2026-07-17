package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import net.risingworld.api.objects.Player;

/** Player-owned mail preferences live in the shared OZTools settings store. */
public final class MailPlayerPreferences {
    private static final String ANNOUNCEMENTS_KEY = "ozmail.announcements.enabled";
    private static final String RECIPIENT_WINDOW_DAYS_KEY = "ozmail.recipients.window-days";

    private MailPlayerPreferences() { }

    public static boolean announcementsEnabled(Player player) {
        PlayerSettings store = OZTools.playerSettings();
        if (player == null || store == null) return true;
        try {
            return store.getBoolean(player.getDbID(), ANNOUNCEMENTS_KEY).orElse(true);
        } catch (RuntimeException ex) {
            OZMail.logger().warn("Could not read mail announcement preference: " + ex.getMessage());
            return true;
        }
    }

    public static void setAnnouncementsEnabled(Player player, boolean enabled) {
        PlayerSettings store = OZTools.playerSettings();
        if (player != null && store != null) store.setBoolean(player.getDbID(), ANNOUNCEMENTS_KEY, enabled);
    }

    public static int recipientWindowDays(Player player, int defaultDays) {
        int fallback = Math.max(1, Math.min(3650, defaultDays));
        PlayerSettings store = OZTools.playerSettings();
        if (player == null || store == null) return fallback;
        try {
            return store.getInt(player.getDbID(), RECIPIENT_WINDOW_DAYS_KEY)
                    .map(days -> Math.max(1, Math.min(3650, days)))
                    .orElse(fallback);
        } catch (RuntimeException ex) {
            OZMail.logger().warn("Could not read mail recipient-window preference: " + ex.getMessage());
            return fallback;
        }
    }

    public static void setRecipientWindowDays(Player player, int days) {
        PlayerSettings store = OZTools.playerSettings();
        if (player != null && store != null) {
            store.setInt(player.getDbID(), RECIPIENT_WINDOW_DAYS_KEY, Math.max(1, Math.min(3650, days)));
        }
    }
}
