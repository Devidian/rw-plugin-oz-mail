package de.omegazirkel.risingworld;

import java.sql.Connection;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;

import de.omegazirkel.risingworld.mail.MailDatabase;
import de.omegazirkel.risingworld.mail.MailAttachment;
import de.omegazirkel.risingworld.mail.MailResultCode;
import de.omegazirkel.risingworld.mail.MailService;
import de.omegazirkel.risingworld.mail.MailReconciliationExporter;
import de.omegazirkel.risingworld.mail.MailDiscordAuditBridge;
import de.omegazirkel.risingworld.mail.MailSettings;
import de.omegazirkel.risingworld.mail.MailboxCapacityService;
import de.omegazirkel.risingworld.mail.MailboxShopIntegration;
import de.omegazirkel.risingworld.tools.bridge.WalletBridge;
import de.omegazirkel.risingworld.mail.ui.MailGui;
import de.omegazirkel.risingworld.mail.ui.MailPluginInfoStatusProvider;
import de.omegazirkel.risingworld.mail.ui.MailPlayerPluginData;
import de.omegazirkel.risingworld.mail.ui.MailPlayerPreferences;
import de.omegazirkel.risingworld.mail.ui.MailPlayerPluginSettings;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.InventoryOverlayButtons;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import de.omegazirkel.risingworld.tools.ui.SharedIndicatorProvider;
import de.omegazirkel.risingworld.tools.ui.SharedIndicators;
import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Player;

/** Entry point for the OZMail plugin. Durable mail workflows are added in later slices. */
public final class OZMail extends Plugin implements Listener {
    public static final String COMMAND = "ozmail";
    private static final String LOGGER_NAME = "OZMail";
    private I18n translations;
    private Connection sqliteConnection;
    private MailService mailService;
    private MailSettings settings;
    private WalletBridge walletBridge;
    private MailboxCapacityService mailboxCapacity;
    private MailboxShopIntegration mailboxShop;
    private MailGui gui;

    public I18n i18n() { return translations; }
    public String text(String key, Player player) { return translations.get(key, player); }
    public MailSettings settings() { return settings; }
    public boolean hasMultipleWalletCurrencies() {
        return walletBridge != null && walletBridge.currencyIdentifiers().size() >= 2;
    }
    public List<String> walletCurrencyIdentifiers() {
        return walletBridge == null ? List.of() : walletBridge.currencyIdentifiers();
    }
    public String defaultWalletCurrencyIdentifier() {
        return walletBridge == null ? "" : walletBridge.defaultCurrencyIdentifier();
    }
    public int mailboxCapacity(Player player) {
        return mailboxCapacity == null ? settings.mailboxLimit : mailboxCapacity.mailboxCapacity(player, settings.mailboxLimit);
    }
    public int extraMailboxes(Player player) { return mailboxCapacity == null ? 0 : mailboxCapacity.extraMailboxes(player); }

    public List<MailDatabase.MailSummary> inbox(Player player) {
        return mailService == null ? List.of() : mailService.inbox(player);
    }

    public int unreadMailCount(Player player) {
        return mailService == null ? 0 : mailService.unreadCount(player);
    }

    public int mailboxUsage(Player player) {
        return mailService == null ? 0 : mailService.mailboxUsage(player);
    }

    public List<Recipient> recentRecipients(Player player) {
        if (player == null || settings == null) return List.of();
        long cutoff = (System.currentTimeMillis() / 1000L) - (recipientWindowDays(player) * 86_400L);
        Set<Integer> ids = new HashSet<>(PlayerDatabaseHelper.findPlayersSeenSince(this, cutoff));
        ids.remove(player.getDbID());
        Map<Integer, PlayerDatabaseHelper.PlayerRecord> records = PlayerDatabaseHelper.findPlayersByDbIds(this, ids);
        Set<Integer> favorites = new HashSet<>(recipientFavoriteIds(player));
        return records.values().stream().filter(record -> record.name != null && !record.name.isBlank())
                .map(record -> new Recipient(record.dbId, record.name, record.lastSeenEpochSeconds, favorites.contains(record.dbId)))
                .sorted(Comparator.comparing(Recipient::favorite).reversed()
                        .thenComparing(Recipient::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public int recipientWindowDays(Player player) {
        return MailPlayerPreferences.recipientWindowDays(player, settings == null ? 30 : settings.recentPlayerDays);
    }

    public List<Integer> recipientFavoriteIds(Player player) {
        return mailService == null ? List.of() : mailService.recipientFavoriteIds(player);
    }

    public boolean toggleRecipientFavorite(Player player, int recipientDbId) {
        return mailService != null && mailService.toggleRecipientFavorite(player, recipientDbId);
    }

    public record Recipient(int dbId, String name, long lastSeenEpochSeconds, boolean favorite) { }

    public List<MailDatabase.MailSummary> outbox(Player player) {
        return mailService == null ? List.of() : mailService.outbox(player);
    }

    public List<MailDatabase.MailSummary> archive(Player player) {
        return mailService == null ? List.of() : mailService.archive(player);
    }

    public Optional<MailDatabase.MailDetail> inboxMail(Player player, String mailId) {
        return mailService == null ? Optional.empty() : mailService.inboxMail(player, mailId);
    }

    public Optional<MailDatabase.MailDetail> outboxMail(Player player, String mailId) {
        return mailService == null ? Optional.empty() : mailService.outboxMail(player, mailId);
    }

    public boolean archiveMail(Player player, String mailId) {
        return mailService != null && mailService.archive(player, mailId);
    }

    public boolean deleteOutboxMail(Player player, String mailId) {
        return mailService != null && mailService.deleteOutboxMail(player, mailId);
    }

    public boolean deleteInboxMail(Player player, String mailId) {
        return mailService != null && mailService.deleteInboxMail(player, mailId);
    }

    public MailService.MailSendResult claimMail(Player player, String mailId) {
        return mailService == null
                ? MailService.MailSendResult.failed(MailResultCode.DATABASE_UNAVAILABLE, "OZMail unavailable")
                : mailService.claim(player, mailId);
    }

    public MailService.MailSendResult returnMailToSender(Player player, String mailId) {
        return mailService == null
                ? MailService.MailSendResult.failed(MailResultCode.DATABASE_UNAVAILABLE, "OZMail unavailable")
                : mailService.returnToSender(player, mailId);
    }

    public List<MailDatabase.ReconciliationEntry> reconciliationEntries(Player admin) {
        return mailService == null ? List.of() : mailService.reconciliationEntries(admin);
    }

    public MailDatabase.OperationalMetrics operationalMetrics(Player admin) {
        return mailService == null ? new MailDatabase.OperationalMetrics(0, 0, 0, 0, 0)
                : mailService.operationalMetrics(admin);
    }

    public List<MailDatabase.AuditEvent> auditEvents(Player admin, String mailId) {
        return mailService == null ? List.of() : mailService.auditEvents(admin, mailId);
    }

    public Optional<String> findMailIdByReference(Player admin, String reference) {
        return mailService == null ? Optional.empty() : mailService.findMailIdByReference(admin, reference);
    }

    public Optional<MailDatabase.ReconciliationEntry> findReconciliationEntryByReference(Player admin,
            String reference) {
        return mailService == null ? Optional.empty() : mailService.findReconciliationEntryByReference(admin, reference);
    }

    public boolean resolveQuarantinedSend(Player admin, String correlationId,
            MailDatabase.VerifiedSendOutcome outcome, String reason) {
        boolean resolved = mailService != null && mailService.resolveQuarantinedSend(admin, correlationId, outcome, reason);
        if (resolved && settings != null && settings.discordAuditChannelId > 0L) {
            String key = outcome == MailDatabase.VerifiedSendOutcome.HELD_IN_MAIL
                    ? "MAIL_DISCORD_AUDIT_SEND_HELD" : "MAIL_DISCORD_AUDIT_SEND_RETURNED";
            MailDiscordAuditBridge.send(this, settings.discordAuditChannelId,
                    translations.get(key, admin).replace("PH_MAIL_ID", findMailIdByReference(admin, correlationId)
                            .orElse(correlationId)));
        }
        return resolved;
    }

    public boolean resolveQuarantinedClaimed(Player admin, String correlationId, String reason) {
        return mailService != null && mailService.resolveQuarantinedClaimed(admin, correlationId, reason);
    }

    public boolean refundQuarantinedCodClaim(Player admin, String correlationId, String reason) {
        return mailService != null && mailService.refundQuarantinedCodClaim(admin, correlationId, reason);
    }

    /** Exports only operational queue metadata; mail contents and diagnostics remain private. */
    public Optional<String> exportReconciliationQueue(Player admin) {
        if (admin == null || !admin.isAdmin() || mailService == null) return Optional.empty();
        try {
            Path directory = Paths.get(getPath() == null ? "." : getPath());
            return Optional.of(MailReconciliationExporter.export(directory, mailService.reconciliationEntries(admin))
                    .getFileName().toString());
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public MailService.MailSendResult sendPlayerMail(Player sender, String recipientName, String subject, String body) {
        return sendPlayerMail(sender, recipientName, subject, body, List.of());
    }

    public MailService.MailSendResult sendPlayerMail(Player sender, String recipientName, String subject, String body,
            List<MailAttachment> attachments) {
        return sendPlayerMail(sender, recipientName, subject, body, attachments, 0L, "");
    }

    public MailService.MailSendResult sendPlayerMail(Player sender, String recipientName, String subject, String body,
            List<MailAttachment> attachments, long codAmount, String codCurrency) {
        if (mailService == null) {
            return MailService.MailSendResult.failed(MailResultCode.DATABASE_UNAVAILABLE, "OZMail unavailable");
        }
        return PlayerDatabaseHelper.findPlayerByExactName(this, recipientName)
                .map(recipient -> {
                    MailService.MailSendResult result = mailService.sendPlayerMail(sender, recipient.dbId, recipient.name,
                            subject, body, attachments, codAmount, codCurrency);
                    notifyOnlineRecipient(recipient.dbId, result);
                    return result;
                })
                .orElseGet(() -> MailService.MailSendResult.failed(MailResultCode.INVALID_REQUEST, "recipient not found"));
    }

    private void notifyOnlineRecipient(int recipientDbId, MailService.MailSendResult result) {
        if (!result.success() || settings == null || !settings.enableAnnouncements) return;
        Player recipient = Server.getPlayerByDbID(recipientDbId);
        if (recipient != null && MailPlayerPreferences.announcementsEnabled(recipient)) {
            recipient.sendTextMessage(translations.get("MAIL_DELIVERY_ANNOUNCEMENT", recipient));
        }
    }

    public static OZLogger logger() {
        return OZLogger.getInstance(LOGGER_NAME);
    }

    @Override
    public void onEnable() {
        translations = I18n.getInstance(this);
        try {
            sqliteConnection = SQLiteConnectionFactory.open(this);
            MailDatabase database = new MailDatabase(sqliteConnection);
            settings = MailSettings.load(this);
            mailboxCapacity = new MailboxCapacityService(sqliteConnection);
            walletBridge = new WalletBridge(this);
            mailService = new MailService(database, settings, walletBridge, mailboxCapacity);
            int quarantined = database.quarantineUnfinishedOperations();
            if (quarantined > 0) {
                logger().warn("Quarantined " + quarantined + " unfinished OZMail operation(s) for reconciliation");
            }
        } catch (SQLException | java.io.IOException ex) {
            logger().error("Could not initialize the OZMail database: " + ex.getMessage());
            return;
        }
        registerEventListener(this);
        gui = new MailGui(this);
        mailboxShop = new MailboxShopIntegration(this, mailboxCapacity);
        mailboxShop.register(settings);
        String pluginName = getDescription("name");
        PluginMenuManager.registerPluginMenu(new MenuItem(pluginName, "oz-mail", "OZMail", p -> gui.openMainMenu(p)));
        PluginShortcutVisibility.register(pluginName, player -> true);
        InventoryOverlayButtons.registerButton(pluginName, "OZMail", "oz-mail", event -> gui.openMainMenu(event.getPlayer()));
        SharedIndicators.registerProvider(pluginName, new SharedIndicatorProvider() {
            @Override public boolean showIndicator(Player player) { return false; }
            @Override public String getIcon(Player player) { return "oz-mail"; }
        });
        PluginInfoStatusProviders.registerProvider(new MailPluginInfoStatusProvider(this));
        PlayerPluginSettingsOverlay.registerPlayerPluginSettings(new MailPlayerPluginSettings(this));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new MailPlayerPluginData(this));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(new PlayerPluginAdminSettings(pluginName,
                getDescription("version"), settings::adminSettingsEntries, () -> {
                    try {
                        settings.reload();
                        if (mailboxShop != null) mailboxShop.register(settings);
                    } catch (java.io.IOException ex) { logger().warn("Could not reload OZMail settings: " + ex.getMessage()); }
                }));
        logger().info("OZMail enabled, version " + getDescription("version"));
    }

    /** Public runtime API for trusted sibling plugins; wrapped by MailBridge consumers. */
    public MailService.MailSendResult sendPluginMail(String senderPlugin, int recipientDbId, String recipientName,
            String subject, String body, String callerCorrelationId) {
        if (mailService == null) {
            return MailService.MailSendResult.failed(MailResultCode.DATABASE_UNAVAILABLE,
                    "OZMail database is unavailable");
        }
        return mailService.sendPluginMail(senderPlugin, recipientDbId, recipientName, subject, body,
                callerCorrelationId);
    }

    @Override
    public void onDisable() {
        String pluginName = getDescription("name");
        InventoryOverlayButtons.unregisterButtons(pluginName);
        PluginShortcutVisibility.unregister(pluginName);
        SharedIndicators.unregisterProvider(pluginName);
        PluginInfoStatusProviders.unregisterProvider(pluginName);
        if (sqliteConnection != null) {
            try {
                sqliteConnection.close();
            } catch (SQLException ex) {
                logger().error("Could not close the OZMail database: " + ex.getMessage());
            }
        }
        logger().info("OZMail disabled");
    }

    @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) {
        String[] parts = event.getCommand().split("\\s+", 2);
        if (parts.length == 0 || !("/" + COMMAND).equals(parts[0])) {
            return;
        }
        Player player = event.getPlayer();
        if (parts.length == 1) {
            gui.open(player);
            return;
        }
        if ("info".equals(parts[1].trim())) {
            player.sendTextMessage(translations.get("MAIL_INFO", player)
                    .replace("PH_VERSION", getDescription("version")));
            return;
        }
        player.sendTextMessage(translations.get("MAIL_COMMAND_UNKNOWN", player));
    }

    @EventMethod
    public void onPlayerSpawn(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        if (settings != null && settings.enableWelcomeMessage) {
            player.sendTextMessage(translations.get("MAIL_WELCOME", player));
        }
        if (settings != null && settings.enableAnnouncements && MailPlayerPreferences.announcementsEnabled(player)) {
            int unread = unreadMailCount(player);
            if (unread > 0) {
                player.sendTextMessage(translations.get("MAIL_UNREAD_ANNOUNCEMENT", player)
                        .replace("PH_UNREAD", String.valueOf(unread)));
            }
        }
        if (settings != null) {
            int expiredReturns = mailService == null ? 0 : mailService.expireAttachmentsForSender(player);
            if (expiredReturns > 0) {
                player.sendTextMessage(translations.get("MAIL_EXPIRY_RETURNED", player)
                        .replace("PH_COUNT", String.valueOf(expiredReturns)));
            }
        }
    }
}
