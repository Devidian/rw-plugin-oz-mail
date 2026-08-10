package de.omegazirkel.risingworld.mail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class MailDatabaseTest {
    @Test
    public void constructionColorRoundTripsThroughAttachmentPersistence() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailAttachment attachment = new MailAttachment("block", 7, 3, "block:7:0:0::1223476",
                    0, (short) 0, "", 0x12AB34);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(attachment), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));

            MailAttachment persisted = database.findInboxMail(22, prepared.mailId()).orElseThrow()
                    .attachments().get(0);
            assertEquals(0x12AB34, persisted.color());
        }
    }

    @Test
    public void legacyAttachmentConstructorDefaultsToUncolored() {
        assertEquals(0, new MailAttachment("block", 7, 3, "block:7:3", 0, (short) 0, "").color());
    }

    @Test
    public void schemaV2AttachmentRowsMigrateToDefaultColor() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE mail_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        mail_id TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_variant INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        checksum TEXT NOT NULL,
                        durability INTEGER NOT NULL DEFAULT 0,
                        item_status INTEGER NOT NULL DEFAULT 0,
                        item_modifier TEXT NOT NULL DEFAULT '',
                        custody_state TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO mail_attachments(
                        mail_id, item_name, item_variant, amount, checksum, custody_state)
                    VALUES ('legacy-mail', 'block', 7, 3, 'block:7:3', 'HELD_IN_MAIL')
                    """);

            new MailDatabase(connection);

            try (var result = statement.executeQuery(
                    "SELECT item_color FROM mail_attachments WHERE mail_id = 'legacy-mail'")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt("item_color"));
            }
            try (var result = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(4, result.getInt(1));
            }
        }
    }

    @Test
    public void completedSendCreatesInboxAndCompletedOperation() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(), 0L, "", "");

            assertTrue(database.completeSend(prepared, 11));
            assertEquals(1, database.listInbox(22, 20).size());
            MailDatabase.OperationOutcome outcome = database.operationOutcome(prepared.correlationId()).orElseThrow();
            assertEquals(MailOperationState.COMPLETED.name(), outcome.operationState());
            assertEquals(MailMessageState.DELIVERED.name(), outcome.mailState());
            assertEquals(prepared.mailId(), database.findMailIdByReference(prepared.mailId()).orElseThrow());
            assertEquals(prepared.mailId(), database.findMailIdByReference(prepared.correlationId()).orElseThrow());
        }
    }

    @Test
    public void quotaExemptReportRemainsVisibleWithoutUsingMailboxCapacity() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail normal = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Normal", "Body", List.of(), 0L, "", "");
            MailDatabase.PreparedMail report = database.prepareOutgoingMail(0, "OZ - Shop", 22, "Recipient",
                    "Report", "Body", List.of(), 0L, "", "OZ - Shop", "report:1", true);

            assertTrue(database.completeSend(normal, 11));
            assertTrue(database.completeSend(report, 0));
            assertEquals(1, database.activeMailboxCount(22));
            assertEquals(2, database.listInbox(22, 20).size());
        }
    }

    @Test
    public void restartQuarantinesUnfinishedSendInsteadOfDeliveringIt() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(), 0L, "", "");

            assertEquals(1, database.quarantineUnfinishedOperations());
            MailDatabase.OperationOutcome outcome = database.operationOutcome(prepared.correlationId()).orElseThrow();
            assertEquals(MailOperationState.NEEDS_RECONCILIATION.name(), outcome.operationState());
            assertEquals(MailMessageState.QUARANTINED.name(), outcome.mailState());
            assertFalse(database.findInboxMail(22, prepared.mailId()).isEmpty());
            assertEquals(prepared.correlationId(), database.findReconciliationEntryByReference(prepared.mailId())
                    .orElseThrow().correlationId());
        }
    }

    @Test
    public void attachmentFreeOutboxDeletePreservesAuditButHidesMail() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));

            assertTrue(database.deleteOutboxMail(11, prepared.mailId()));
            assertTrue(database.findOutboxMail(11, prepared.mailId()).isEmpty());
            assertTrue(database.listAuditEvents(prepared.mailId(), 20).stream()
                    .anyMatch(event -> "OUTBOX_DELETED".equals(event.eventType())));
        }
    }

    @Test
    public void attachmentBearingMailCannotBeDeletedFromEitherMailbox() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));

            assertFalse(database.deleteOutboxMail(11, prepared.mailId()));
            assertFalse(database.deleteInboxMail(22, prepared.mailId()));
            MailDatabase.MailDetail detail = database.findInboxMail(22, prepared.mailId()).orElseThrow();
            assertEquals(1, detail.attachments().size());
            assertEquals("wood", detail.attachments().get(0).itemName());
            assertEquals(2, detail.attachments().get(0).amount());
        }
    }

    @Test
    public void attachmentBearingMailCannotBeArchived() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));

            assertFalse(database.archive(22, prepared.mailId()));
            assertEquals(MailMessageState.DELIVERED.name(), database.findInboxMail(22, prepared.mailId())
                    .orElseThrow().state());
        }
    }

    @Test
    public void claimedAttachmentMailBecomesDeletableReadMail() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));

            MailDatabase.ClaimPreparation claim = database.prepareClaim(22, prepared.mailId());
            assertTrue(database.completeClaim(claim, 22));
            assertEquals(MailMessageState.READ.name(), database.findInboxMail(22, prepared.mailId())
                    .orElseThrow().state());
            assertFalse(database.findInboxMail(22, prepared.mailId()).orElseThrow().hasAttachments());
            assertTrue(database.deleteInboxMail(22, prepared.mailId()));
        }
    }

    @Test
    public void claimedAttachmentMailCanBeArchived() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));

            MailDatabase.ClaimPreparation claim = database.prepareClaim(22, prepared.mailId());
            assertTrue(database.completeClaim(claim, 22));
            assertTrue(database.archive(22, prepared.mailId()));
            assertEquals(MailMessageState.ARCHIVED.name(), database.findInboxMail(22, prepared.mailId())
                    .orElseThrow().state());
        }
    }

    @Test
    public void legacyClaimedMailCanBeArchivedAndDeleted() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));
            try (var statement = connection.prepareStatement("UPDATE mail_messages SET state = 'CLAIMED' WHERE id = ?")) {
                statement.setString(1, prepared.mailId());
                assertEquals(1, statement.executeUpdate());
            }

            assertTrue(database.archive(22, prepared.mailId()));
            assertTrue(database.deleteInboxMail(22, prepared.mailId()));
        }
    }

    @Test
    public void archivedMailIsOnlyListedInArchive() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(), 0L, "", "");
            assertTrue(database.completeSend(prepared, 11));
            assertTrue(database.archive(22, prepared.mailId()));

            assertTrue(database.listInbox(22, 20).isEmpty());
            assertEquals(1, database.listArchive(22, 20).size());
        }
    }

    @Test
    public void attachmentReturnCreatesMailForOfflineSenderAndMovesCustodyAtomically() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail original = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.completeSend(original, 11));

            MailDatabase.ReturnMailResult returned = database.returnAsMail(22, original.mailId(), 20);

            assertTrue(returned.success());
            assertEquals(MailMessageState.RETURNED.name(), database.findInboxMail(22, original.mailId())
                    .orElseThrow().state());
            MailDatabase.MailDetail returnedMail = database.findInboxMail(11, returned.returnedMailId()).orElseThrow();
            assertEquals("returned: Subject", returnedMail.subject());
            assertEquals(1, returnedMail.attachments().size());
            assertEquals(2, returnedMail.attachments().get(0).amount());
            assertFalse(database.findInboxMail(22, original.mailId()).orElseThrow().hasAttachments());
        }
    }

    @Test
    public void fullSenderMailboxLeavesOriginalAttachmentsUntouchedOnReturn() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail existing = database.prepareOutgoingMail(99, "Other", 11, "Sender",
                    "Existing", "Body", List.of(), 0L, "", "");
            assertTrue(database.completeSend(existing, 99));
            MailDatabase.PreparedMail original = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.completeSend(original, 11));

            MailDatabase.ReturnMailResult returned = database.returnAsMail(22, original.mailId(), 1);

            assertFalse(returned.success());
            assertTrue(returned.mailboxFull());
            assertEquals(MailMessageState.DELIVERED.name(), database.findInboxMail(22, original.mailId())
                    .orElseThrow().state());
            assertTrue(database.findInboxMail(22, original.mailId()).orElseThrow().hasAttachments());
        }
    }

    @Test
    public void verifiedQuarantinedSendNeedsReasonAndNeverTouchesExternalCustody() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 2, "wood:0:2")), 0L, "", "");
            assertTrue(database.quarantineSend(prepared, 11, "Inventory removal outcome unknown"));

            try {
                database.resolveQuarantinedSend(prepared.correlationId(), 99,
                        MailDatabase.VerifiedSendOutcome.HELD_IN_MAIL, " ");
                throw new AssertionError("A verification reason must be required");
            } catch (IllegalArgumentException expected) {
                // Expected: the resolver records an explicit administrative verification.
            }

            assertTrue(database.resolveQuarantinedSend(prepared.correlationId(), 99,
                    MailDatabase.VerifiedSendOutcome.HELD_IN_MAIL, "Verified in mail custody"));
            MailDatabase.OperationOutcome outcome = database.operationOutcome(prepared.correlationId()).orElseThrow();
            assertEquals(MailOperationState.COMPLETED.name(), outcome.operationState());
            assertEquals(MailMessageState.DELIVERED.name(), outcome.mailState());
            assertTrue(database.findInboxMail(22, prepared.mailId()).orElseThrow().hasAttachments());
            assertTrue(database.listAuditEvents(prepared.mailId(), 20).stream()
                    .anyMatch(event -> "ADMIN_SEND_VERIFIED_HELD".equals(event.eventType())));
        }
    }

    @Test
    public void verifiedReturnedSendIsFinalAndCannotBeResolvedTwice() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("stone", 0, 1, "stone:0:1")), 0L, "", "");
            assertTrue(database.quarantineSend(prepared, 11, "Inventory removal outcome unknown"));

            assertTrue(database.resolveQuarantinedSend(prepared.correlationId(), 99,
                    MailDatabase.VerifiedSendOutcome.RETURNED_TO_SENDER, "Verified return in sender inventory"));
            MailDatabase.OperationOutcome outcome = database.operationOutcome(prepared.correlationId()).orElseThrow();
            assertEquals(MailOperationState.COMPLETED.name(), outcome.operationState());
            assertEquals(MailMessageState.RETURNED.name(), outcome.mailState());
            assertFalse(database.resolveQuarantinedSend(prepared.correlationId(), 99,
                    MailDatabase.VerifiedSendOutcome.HELD_IN_MAIL, "Attempted second resolution"));
            assertTrue(database.listAuditEvents(prepared.mailId(), 20).stream()
                    .anyMatch(event -> "ADMIN_SEND_VERIFIED_RETURNED".equals(event.eventType())));
        }
    }

    @Test
    public void reconciliationExportContainsOnlyOperationalMetadata() throws Exception {
        Path pluginDirectory = Files.createTempDirectory("oz-mail-export-");
        try {
            Path export = MailReconciliationExporter.export(pluginDirectory, List.of(
                    new MailDatabase.ReconciliationEntry("correlation", "mail", "SEND", "diagnostic", 11, 22,
                            "QUARANTINED", 100L, 200L)));
            String content = Files.readString(export);
            assertTrue(content.contains("correlation_id,mail_id,operation_type,mail_state,created_at,updated_at"));
            assertTrue(content.contains("\"correlation\",\"mail\",\"SEND\",\"QUARANTINED\",100,200"));
            assertFalse(content.contains("diagnostic"));
            assertFalse(content.contains("11,22"));
        } finally {
            Path exports = pluginDirectory.resolve("exports");
            if (Files.exists(exports)) {
                try (var paths = Files.list(exports)) {
                    for (Path path : paths.toList()) Files.deleteIfExists(path);
                }
                Files.deleteIfExists(exports);
            }
            Files.deleteIfExists(pluginDirectory);
        }
    }

    @Test
    public void claimPreparationPreservesCodTermsForTheWalletSaga() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 1, "wood:0:1")), 25L, "OZC", "");
            assertTrue(database.completeSend(prepared, 11));

            MailDatabase.ClaimPreparation claim = database.prepareClaim(22, prepared.mailId());
            assertEquals(11, claim.senderDbId());
            assertEquals(25L, claim.codAmount());
            assertEquals("OZC", claim.codCurrency());
            assertTrue(database.cancelClaimPayment(claim, 22, "Insufficient Wallet funds"));
            assertEquals(MailMessageState.DELIVERED.name(), database.operationOutcome(prepared.correlationId())
                    .orElseThrow().mailState());
        }
    }

    @Test
    public void quarantinedCodClaimCanBeRestoredAfterAnIdempotentRefund() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MailDatabase database = new MailDatabase(connection);
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(11, "Sender", 22, "Recipient",
                    "Subject", "Body", List.of(new MailAttachment("wood", 0, 1, "wood:0:1")), 25L, "OZC", "");
            assertTrue(database.completeSend(prepared, 11));
            MailDatabase.ClaimPreparation claim = database.prepareClaim(22, prepared.mailId());
            assertTrue(database.quarantineClaim(claim, 22, "Inventory outcome unknown after COD payment"));

            assertTrue(database.resolveQuarantinedClaimRefunded(claim.correlationId(), 99,
                    "Verified inventory grant did not happen; Wallet refund completed"));
            MailDatabase.OperationOutcome outcome = database.operationOutcome(claim.correlationId()).orElseThrow();
            assertEquals(MailOperationState.COMPLETED.name(), outcome.operationState());
            assertEquals(MailMessageState.DELIVERED.name(), outcome.mailState());
            assertTrue(database.findInboxMail(22, prepared.mailId()).orElseThrow().hasAttachments());
        }
    }
}
