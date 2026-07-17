package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import net.risingworld.api.objects.Player;

/** Player-owned mail preferences live in the shared OZTools settings store. */
public final class MailPlayerPreferences {
    private static final String ANNOUNCEMENTS_KEY = "ozmail.announcements.enabled";

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
}
