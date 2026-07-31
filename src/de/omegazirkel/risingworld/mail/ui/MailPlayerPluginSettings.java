package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.style.Pivot;

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
                OZUIElement sendConfirmation = defaultSettingsContainer();
                sendConfirmation.addChild(defaultSettingsLabel(plugin.text("MAIL_SETTINGS_SEND_CONFIRMATION", player)));
                sendConfirmation.addChild(switchButtons(player, MailPlayerPreferences.sendConfirmationEnabled(player), event -> {
                    MailPlayerPreferences.setSendConfirmationEnabled(player,
                            !MailPlayerPreferences.sendConfirmationEnabled(player));
                    redrawContent();
                }));
                flexWrapper.addChild(sendConfirmation);
                flexWrapper.addChild(recipientWindowSetting(player));
            }

            private OZUIElement recipientWindowSetting(Player player) {
                OZUIElement setting = defaultSettingsContainer();
                setting.addChild(defaultSettingsLabel(plugin.text("MAIL_SETTINGS_RECIPIENT_WINDOW", player)
                        .replace("PH_DAYS", String.valueOf(plugin.recipientWindowDays(player)))));

                UITextField days = new UITextField(String.valueOf(plugin.recipientWindowDays(player)));
                days.setPivot(Pivot.UpperLeft);
                days.setPosition(10, 58, false);
                days.setSize(145, 28, false);
                days.setMaxCharacters(4);
                setting.addChild(days);

                AdvancedButton save = AdvancedButtonFactory.defaultButton(plugin.text("MAIL_SETTINGS_SAVE", player), event ->
                        days.getCurrentText(player, value -> {
                            try {
                                if (value != null) {
                                    MailPlayerPreferences.setRecipientWindowDays(player, Integer.parseInt(value.trim()));
                                }
                            } catch (NumberFormatException ignored) {
                                // Keep the existing value when the player entered no valid day count.
                            }
                            redrawContent();
                        }));
                save.setPivot(Pivot.UpperLeft);
                save.setPosition(163, 58, false);
                save.setSize(95, 28, false);
                setting.addChild(save);
                return setting;
            }
        };
    }
}
