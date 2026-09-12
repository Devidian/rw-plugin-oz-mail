package de.omegazirkel.risingworld.mail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

import de.omegazirkel.risingworld.tools.settings.AdminSettingsEntry;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsType;
import de.omegazirkel.risingworld.tools.settings.JsonSettingsFile;
import de.omegazirkel.risingworld.tools.settings.SettingsFileEditor;
import net.risingworld.api.Plugin;

/** Validated server-owned limits. Player-specific limits are stored separately later. */
public final class MailSettings {
    public int mailboxLimit;
    public int maxSubjectLength;
    public int maxBodyLength;
    public int maxPlayerAttachments;
    public int sendCooldownSeconds;
    public int attachmentExpiryDays;
    public int recentPlayerDays;
    public boolean enableAnnouncements;
    public boolean enableCod;
    public boolean enableWelcomeMessage;
    public long discordAuditChannelId;
    public boolean enableExtraMailboxShopOffer;
    public long extraMailboxBasePrice;
    public double extraMailboxPriceIncreaseFactor;
    public Set<String> trustedPluginSenders = Set.of();
    private final Path settingsFile;
    private final Path defaultSettingsFile;
    private Properties currentSettings;
    private Properties defaultSettings;

    private MailSettings(Path settingsFile, Path defaultSettingsFile, Properties values, Properties defaults) {
        this.settingsFile = settingsFile;
        this.defaultSettingsFile = defaultSettingsFile;
        apply(values, defaults);
    }

    private void apply(Properties values, Properties defaults) {
        mailboxLimit = integer(values, defaults, "mailboxLimit", 20, 1, 10000);
        maxSubjectLength = integer(values, defaults, "maxSubjectLength", 25, 1, 500);
        maxBodyLength = integer(values, defaults, "maxBodyLength", 2500, 1, 20000);
        maxPlayerAttachments = integer(values, defaults, "maxPlayerAttachments", 5, 0, 100);
        sendCooldownSeconds = integer(values, defaults, "sendCooldownSeconds", 30, 0, 86400);
        attachmentExpiryDays = integer(values, defaults, "attachmentExpiryDays", 30, 1, 3650);
        recentPlayerDays = integer(values, defaults, "recentPlayerDays", 30, 1, 3650);
        enableAnnouncements = bool(values, defaults, "enableAnnouncements", true);
        enableCod = bool(values, defaults, "enableCod", false);
        enableWelcomeMessage = bool(values, defaults, "enableWelcomeMessage", false);
        discordAuditChannelId = longValue(values, defaults, "discordAuditChannelId", 0L, 0L, Long.MAX_VALUE);
        enableExtraMailboxShopOffer = bool(values, defaults, "enableExtraMailboxShopOffer", true);
        extraMailboxBasePrice = longValue(values, defaults, "extraMailboxBasePrice", 1000L, 0L, Long.MAX_VALUE);
        extraMailboxPriceIncreaseFactor = decimal(values, defaults, "extraMailboxPriceIncreaseFactor", 1d, 1d, 100d);
        trustedPluginSenders = trustedPluginSenders(values, defaults);
        currentSettings = values;
        defaultSettings = defaults;
    }

    public static MailSettings load(Plugin plugin) throws IOException {
        Path settings = JsonSettingsFile.worldSettingsFile(plugin.getPath() == null ? "." : plugin.getPath());
        Path defaults = settings.resolveSibling("settings.default.json");
        Path legacy = settings.resolveSibling("settings.properties");
        JsonSettingsFile.migrateLegacyProperties(legacy, settings);
        if (Files.notExists(settings) && Files.exists(defaults))
            JsonSettingsFile.copyAtomically(defaults, settings);
        JsonSettingsFile.normalizePaths(settings);
        JsonSettingsFile.migrateCsvToArray(settings, "general.trustedPluginSenders");
        return new MailSettings(settings, defaults, read(settings), read(defaults));
    }

    public synchronized void reload() throws IOException {
        apply(read(settingsFile), read(defaultSettingsFile));
    }

    public synchronized List<AdminSettingsEntry> adminSettingsEntries() {
        return Arrays.asList(
                AdminSettingsEntry.group("mailbox", "Postfach / Mailbox",
                        "Serverseitige Begrenzungen fuer Postfaecher. / Server-owned mailbox limits."),
                entry("mailboxLimit", "Postfachlimit / Mailbox limit",
                        "Maximale aktive Mails pro Spieler. / Maximum active mail per player.", AdminSettingsType.INTEGER),
                entry("maxSubjectLength", "Betrefflaenge / Subject length",
                        "Maximale Zeichen im Betreff. / Maximum subject characters.", AdminSettingsType.INTEGER),
                entry("maxBodyLength", "Nachrichtenlaenge / Message length",
                        "Maximale Zeichen pro Nachricht. / Maximum message characters.", AdminSettingsType.INTEGER),
                entry("maxPlayerAttachments", "Anhangslimit / Attachment limit",
                        "Maximale Anhaenge pro Spieler-Mail. / Maximum attachments per player mail.", AdminSettingsType.INTEGER),
                AdminSettingsEntry.group("delivery", "Zustellung / Delivery",
                        "Laufzeitoptionen fuer Zustellung und Hinweise. / Delivery and notification runtime options."),
                entry("sendCooldownSeconds", "Sende-Cooldown / Send cooldown",
                        "Sekunden zwischen Spieler-Mails. / Seconds between player mails.", AdminSettingsType.INTEGER),
                entry("attachmentExpiryDays", "Anhang-Ablauf / Attachment expiry",
                        "Tage bis zur Rueckgabe abgelaufener Anhaenge. / Days before expired attachments are returned.", AdminSettingsType.INTEGER),
                entry("recentPlayerDays", "Spielerliste Zeitraum / Player list window",
                        "Standardwert fuer die persoenliche Empfaengerliste in Tagen. / Default for each player's recipient-list period in days.", AdminSettingsType.INTEGER),
                entry("enableAnnouncements", "Ankuendigungen / Announcements",
                        "Globale Zustellhinweise erlauben. / Allow global delivery notifications.", AdminSettingsType.BOOLEAN),
                entry("enableCod", "Nachnahme / Cash on delivery",
                        "Nachnahme mit idempotenter Wallet-Zahlung aktivieren. / Enable COD with idempotent Wallet payment.", AdminSettingsType.BOOLEAN),
                entry("enableWelcomeMessage", "Willkommenshinweis / Welcome notice",
                        "Hinweis beim Betreten senden. / Send a notice when a player joins.", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("discord", "Discord", "Optionale Auditweiterleitung ohne Mailinhalte; Kanal 0 deaktiviert sie. / Optional audit forwarding without mail contents; channel 0 disables it."),
                entry("discordAuditChannelId", "Discord-Audit-Kanal / Discord audit channel",
                        "Discord-Kanal-ID; 0 deaktiviert die Weiterleitung. / Discord channel ID; 0 disables forwarding.", AdminSettingsType.STRING),
                AdminSettingsEntry.group("extraMailbox", "Extra-Postfächer / Extra mailboxes",
                        "Shop-Angebot für zusätzliche Postfachkapazität. / Shop offer for additional mailbox capacity."),
                entry("enableExtraMailboxShopOffer", "Extra-Postfach-Angebot / Extra mailbox offer",
                        "Registriert das Extra-Postfach-Angebot in OZ Shop. / Registers the extra mailbox offer in OZ Shop.", AdminSettingsType.BOOLEAN),
                entry("extraMailboxBasePrice", "Extra-Postfach-Grundpreis / Extra mailbox base price",
                        "Preis für das erste Extra-Postfach in der Wallet-Standardwährung. / First extra mailbox price in the Wallet default currency.", AdminSettingsType.INTEGER),
                entry("extraMailboxPriceIncreaseFactor", "Preisfaktor / Price increase factor",
                        "Multiplikator je bereits gekauftem Extra-Postfach; 1 bedeutet kein Anstieg. / Multiplier per purchased extra mailbox; 1 means no increase.", AdminSettingsType.STRING),
                AdminSettingsEntry.group("bridge", "Plugin-Bridge / Plugin bridge",
                        "Vertrauensgrenze für Plugin-Mails und Anhänge. / Trust boundary for plugin mail and attachments."),
                entry("trustedPluginSenders", "Vertrauenswürdige Sender / Trusted senders",
                        "Kommagetrennte Plugin-Namen. / Comma-separated plugin names.", AdminSettingsType.TEXT));
    }

    private AdminSettingsEntry entry(String key, String label, String description, AdminSettingsType type) {
        return new AdminSettingsEntry(key, label, description,
                currentSettings.getProperty(key, defaultSettings.getProperty(key, "")),
                defaultSettings.getProperty(key, ""), type, false, value -> {
                    if (!SettingsFileEditor.writeValue(settingsFile, JsonSettingsFile.canonicalPath(key), value)) return false;
                    try {
                        reload();
                        return true;
                    } catch (IOException ex) {
                        return false;
                    }
                });
    }

    private static Properties read(Path path) throws IOException {
        if (!path.getFileName().toString().endsWith(".properties")) {
            Properties properties = JsonSettingsFile.loadProperties(path);
            JsonSettingsFile.addCompatibilityAliases(properties);
            return properties;
        }
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (FileInputStream input = new FileInputStream(path.toFile())) {
                properties.load(new InputStreamReader(input, "UTF8"));
            }
        }
        return properties;
    }

    private static int integer(Properties values, Properties defaults, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(values.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean bool(Properties values, Properties defaults, String key, boolean fallback) {
        return values.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))).equalsIgnoreCase("true");
    }

    private static long longValue(Properties values, Properties defaults, String key, long fallback, long min, long max) {
        try {
            long value = Long.parseLong(values.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double decimal(Properties values, Properties defaults, String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(values.getProperty(key, defaults.getProperty(key, String.valueOf(fallback))));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean isTrustedPluginSender(String pluginName) {
        return pluginName != null && trustedPluginSenders.contains(pluginName.trim().toLowerCase(Locale.ROOT));
    }

    static Set<String> trustedPluginSenders(Properties values, Properties defaults) {
        String configured = values.getProperty("trustedPluginSenders", "");
        return pluginNames(configured.isBlank() ? defaults.getProperty("trustedPluginSenders", "") : configured);
    }

    private static Set<String> pluginNames(String raw) {
        Set<String> names = new HashSet<>();
        for (String part : raw == null ? new String[0] : raw.split(",")) {
            if (!part.isBlank()) names.add(part.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(names);
    }
}
