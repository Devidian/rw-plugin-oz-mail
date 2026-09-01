package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginDataPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginData;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

public final class MailPlayerPluginData extends PlayerPluginData {
    private final OZMail plugin;

    public MailPlayerPluginData(OZMail plugin) {
        this.plugin = plugin;
        pluginLabel = plugin.getDescription("name");
        pluginVersion = plugin.getDescription("version");
    }

    @Override
    public BasePlayerPluginDataPanel createPlayerPluginDataUIElement(Player player) {
        return new BasePlayerPluginDataPanel(player, pluginLabel) {
            @Override protected void redrawContent() {
                flexWrapper.removeAllChilds();
                OZUIElement overview = new OZUIElement();
                overview.setSize(270, 96, false);
                overview.setBackgroundColor(0x181713D8);
                overview.setBorder(1);
                overview.setBorderColor(0x7A5D2AFF);
                UILabel label = new UILabel(plugin.text("mail.data.overview", player)
                        .replace("PH_INBOX", String.valueOf(plugin.inbox(player).size()))
                        .replace("PH_OUTBOX", String.valueOf(plugin.outbox(player).size()))
                        .replace("PH_ARCHIVE", String.valueOf(plugin.archive(player).size()))
                        .replace("PH_CAPACITY", String.valueOf(plugin.mailboxCapacity(player)))
                        .replace("PH_EXTRA", String.valueOf(plugin.extraMailboxes(player))));
                label.setPivot(Pivot.UpperLeft);
                label.setPosition(10, 8, false);
                label.style.width.set(92, Unit.Percent);
                label.style.height.set(72, Unit.Pixel);
                label.setFont(Font.DefaultBold);
                label.setFontSize(14);
                label.setFontColor(0xF4F0E6FF);
                label.setTextAlign(TextAnchor.UpperLeft);
                label.setTextWrap(true);
                overview.addChild(label);
                flexWrapper.addChild(overview);
            }
        };
    }
}
