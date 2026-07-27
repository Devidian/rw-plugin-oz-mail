package de.omegazirkel.risingworld.mail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.Test;

import net.risingworld.api.Plugin;

public class MailServicePluginAttachmentTest {
    @Test
    public void trustedAttachmentMailIsDurableAndIdempotent() throws Exception {
        Path pluginDirectory = Files.createTempDirectory("oz-mail-plugin-test");
        Files.writeString(pluginDirectory.resolve("settings.properties"),
                "trustedPluginSenders=OZ - Marketplace\nmailboxLimit=20\nmaxPlayerAttachments=5\n");
        Files.writeString(pluginDirectory.resolve("settings.default.properties"),
                "trustedPluginSenders=\nmailboxLimit=20\nmaxPlayerAttachments=5\n");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailSettings settings = MailSettings.load(new TestPlugin(pluginDirectory));
            MailService service = new MailService(database, settings, null,
                    new MailboxCapacityService(connection));
            MailAttachment attachment = new MailAttachment(
                    "wood", 0, 2, "wood:0:2:100:0::0", 100, (short) 0, "", 0);

            assertTrue(service.canReceivePluginMail(42));
            MailService.MailSendResult first = service.sendPluginMail(
                    "OZ - Marketplace", 42, "Buyer", "Wanted listing", "Delivery",
                    List.of(attachment), "wanted-1-2");
            MailService.MailSendResult duplicate = service.sendPluginMail(
                    "OZ - Marketplace", 42, "Buyer", "Wanted listing", "Delivery",
                    List.of(attachment), "wanted-1-2");

            assertTrue(first.success());
            assertTrue(duplicate.success());
            assertEquals(first.mailId(), duplicate.mailId());
            MailDatabase.MailDetail delivered = database.findInboxMail(42, first.mailId()).orElseThrow();
            assertEquals(1, delivered.attachments().size());
            assertEquals(2, delivered.attachments().get(0).amount());
        }
    }

    private static final class TestPlugin extends Plugin {
        private final String path;

        private TestPlugin(Path path) {
            this.path = path.toString();
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }
}
