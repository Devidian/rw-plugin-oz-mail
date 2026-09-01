package de.omegazirkel.risingworld.mail.ui;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UITarget;

public final class MailGui {
    private static final String OVERLAY_ATTRIBUTE = "oz.mail.ui.overlay";
    private final OZMail plugin;

    public MailGui(OZMail plugin) {
        this.plugin = plugin;
        AssetManager.loadIconFromPlugin(plugin, "oz-mail");
        AssetManager.loadIconFromPlugin(plugin, "extra-mailbox");
    }

    public void openMainMenu(Player player) {
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem(plugin.getDescription("name"), "oz-mail", plugin.text("mail.menu.open", player), p -> {
            p.hideRadialMenu(true);
            open(p);
        }));
        menuItems.add(PluginInfoStatusProviders.menuItem(plugin.text("mail.menu.status", player),
                plugin.getDescription("name")));
        menuItems.add(MenuItem.closeMenu(player));
        PluginMenuManager.showMenu(player, menuItems);
    }

    public void open(Player player) {
        UIElement existing = (UIElement) player.getAttribute(OVERLAY_ATTRIBUTE);
        if (existing != null) {
            player.deleteAttribute(OVERLAY_ATTRIBUTE);
        }
        MailOverlay overlay = new MailOverlay(plugin, player);
        player.setAttribute(OVERLAY_ATTRIBUTE, overlay);
        player.addUIElement(overlay, UITarget.Modal);
    }
}
