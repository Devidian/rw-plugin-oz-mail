package de.omegazirkel.risingworld.mail;

import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

public class MailSettingsTest {
    @Test
    public void eventSwitchesLoadAndReloadIndependently() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("mail-event-settings");
        java.nio.file.Path defaultsFile = directory.resolve("settings.default.json");
        java.nio.file.Files.writeString(defaultsFile, "{}");
        MailSettings settings = MailSettings.load(new TestPlugin(directory));
        var received = new java.util.ArrayList<de.omegazirkel.risingworld.tools.bridge.AuditEvent>();
        try (var registration = de.omegazirkel.risingworld.tools.bridge.EventBridge.register(received::add)) {
            var entry = settings.adminSettingsEntries().stream()
                    .filter(value -> value.getKey().equals("eventLogs.mail.discord.audit.send.held.enabled"))
                    .findFirst().orElseThrow();
            assertTrue(entry.write("false"));
            settings.reload();
            java.util.function.Supplier<de.omegazirkel.risingworld.tools.bridge.AuditEvent> event = () ->
                    new de.omegazirkel.risingworld.tools.bridge.AuditEvent(null, null,
                            de.omegazirkel.risingworld.tools.bridge.EventMessage.literal("test"),
                            java.time.Instant.now(), "test", "mail", "default");
            org.junit.Assert.assertFalse(settings.eventLogs.publish("mail.discord.audit.send.held", event));
            assertTrue(settings.eventLogs.publish("mail.discord.audit.send.returned", event));
            assertTrue(entry.write("true"));
            settings.reload();
            assertTrue(settings.eventLogs.publish("mail.discord.audit.send.held", event));
        }
    }

    private static final class TestPlugin extends net.risingworld.api.Plugin {
        private final String path;
        private TestPlugin(java.nio.file.Path path) { this.path = path.toString(); }
        @Override public String getPath() { return path; }
        @Override public void onEnable() { }
        @Override public void onDisable() { }
    }

    @Test
    public void emptyTrustedPluginSendersUseTheBundledDefaults() {
        Properties values = new Properties();
        values.setProperty("trustedPluginSenders", "");
        Properties defaults = new Properties();
        defaults.setProperty("trustedPluginSenders", "OZ - Bosses,OZ - Service NPC,OZ - Shop");

        assertTrue(MailSettings.trustedPluginSenders(values, defaults).contains("oz - bosses"));
        assertTrue(MailSettings.trustedPluginSenders(values, defaults).contains("oz - service npc"));
        assertTrue(MailSettings.trustedPluginSenders(values, defaults).contains("oz - shop"));
    }
}
