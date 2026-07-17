package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import net.risingworld.api.objects.Player;

public final class MailPlayerPluginSettings extends PlayerPluginSettings {
    private final OZMail plugin;

    public MailPlayerPluginSettings(OZMail plugin) {
        this.plugin = plugin;
        pluginLabel = plugin.getDescription("name");
        pluginVersion = plugin.getDescription("version");
    }

    @Override
    public BasePlayerPluginSettingsPanel createPlayerPluginSettingsUIElement(Player player) {
        return new BasePlayerPluginSettingsPanel(player, pluginLabel) {
            @Override protected void redrawContent() {
                flexWrapper.removeAllChilds();
                OZUIElement announcements = defaultSettingsContainer();
                announcements.addChild(defaultSettingsLabel(plugin.text("MAIL_SETTINGS_ANNOUNCEMENTS", player)));
                announcements.addChild(switchButtons(player, MailPlayerPreferences.announcementsEnabled(player), event -> {
                    MailPlayerPreferences.setAnnouncementsEnabled(player, !MailPlayerPreferences.announcementsEnabled(player));
                    redrawContent();
                }));
                flexWrapper.addChild(announcements);
            }
        };
    }
}
