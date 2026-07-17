package de.omegazirkel.risingworld.mail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;

/**
 * OZMail-owned persistence. External inventory and Wallet calls are never made
 * while a SQLite transaction is open; their intent/outcome is journaled here.
 */
public final class MailDatabase {
    private static final int SCHEMA_VERSION = 1;
    private final Connection connection;

    public MailDatabase(Connection connection) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_messages (
                        id TEXT PRIMARY KEY,
                        sender_db_id INTEGER NOT NULL,
                        sender_name TEXT NOT NULL,
                        sender_plugin TEXT NOT NULL DEFAULT '',
                        recipient_db_id INTEGER NOT NULL,
                        recipient_name TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        body TEXT NOT NULL,
                        state TEXT NOT NULL,
                        state_version INTEGER NOT NULL DEFAULT 0,
                        cod_amount BIGINT NOT NULL DEFAULT 0,
                        cod_currency TEXT NOT NULL DEFAULT '',
                        created_at BIGINT NOT NULL,
                        delivered_at BIGINT NOT NULL DEFAULT 0,
                        read_at BIGINT NOT NULL DEFAULT 0,
                        archived_at BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        mail_id TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_variant INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        checksum TEXT NOT NULL,
                        custody_state TEXT NOT NULL,
                        FOREIGN KEY(mail_id) REFERENCES mail_messages(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_operations (
                        correlation_id TEXT PRIMARY KEY,
                        mail_id TEXT NOT NULL,
                        operation_type TEXT NOT NULL,
                        state TEXT NOT NULL,
                        requested_by_db_id INTEGER NOT NULL,
                        error_detail TEXT NOT NULL DEFAULT '',
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        FOREIGN KEY(mail_id) REFERENCES mail_messages(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_audit_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        mail_id TEXT NOT NULL,
                        correlation_id TEXT NOT NULL,
                        actor_type TEXT NOT NULL,
                        actor_db_id INTEGER NOT NULL,
                        event_type TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY(mail_id) REFERENCES mail_messages(id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_recipient_state ON mail_messages(recipient_db_id, state, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_operations_state ON mail_operations(state, updated_at)");
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    /**
     * Creates the durable send intent before any inventory is removed. The
     * returned correlation ID is also the future idempotency key for Wallet.
     */
    public PreparedMail prepareOutgoingMail(int senderDbId, String senderName, int recipientDbId, String recipientName,
            String subject, String body, List<MailAttachment> attachments, long codAmount, String codCurrency,
            String senderPlugin) throws SQLException {
        return prepareOutgoingMail(senderDbId, senderName, recipientDbId, recipientName, subject, body, attachments,
                codAmount, codCurrency, senderPlugin, "");
    }

    public PreparedMail prepareOutgoingMail(int senderDbId, String senderName, int recipientDbId, String recipientName,
            String subject, String body, List<MailAttachment> attachments, long codAmount, String codCurrency,
            String senderPlugin, String requestedCorrelationId) throws SQLException {
        if (senderDbId < 0 || recipientDbId <= 0 || subject == null || body == null || codAmount < 0) {
            throw new IllegalArgumentException("invalid outgoing mail");
        }
        String mailId = UUID.randomUUID().toString();
        String correlationId = requestedCorrelationId == null || requestedCorrelationId.isBlank()
                ? UUID.randomUUID().toString() : requestedCorrelationId.trim();
        long now = System.currentTimeMillis();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement message = connection.prepareStatement("""
                    INSERT INTO mail_messages(id, sender_db_id, sender_name, sender_plugin, recipient_db_id,
                        recipient_name, subject, body, state, cod_amount, cod_currency, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                message.setString(1, mailId);
                message.setInt(2, senderDbId);
                message.setString(3, safe(senderName));
                message.setString(4, safe(senderPlugin));
                message.setInt(5, recipientDbId);
                message.setString(6, safe(recipientName));
                message.setString(7, subject);
                message.setString(8, body);
                message.setString(9, MailMessageState.PENDING_SEND.name());
                message.setLong(10, codAmount);
                message.setString(11, safe(codCurrency));
                message.setLong(12, now);
                message.executeUpdate();
            }
            for (MailAttachment attachment : attachments == null ? List.<MailAttachment>of() : attachments) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO mail_attachments(mail_id, item_name, item_variant, amount, checksum, custody_state)
                        VALUES (?, ?, ?, ?, ?, 'RESERVED')
                        """)) {
                    insert.setString(1, mailId);
                    insert.setString(2, attachment.itemName());
                    insert.setInt(3, attachment.variant());
                    insert.setInt(4, attachment.amount());
                    insert.setString(5, attachment.checksum());
                    insert.executeUpdate();
                }
            }
            try (PreparedStatement operation = connection.prepareStatement("""
                    INSERT INTO mail_operations(correlation_id, mail_id, operation_type, state, requested_by_db_id,
                        created_at, updated_at) VALUES (?, ?, 'SEND', ?, ?, ?, ?)
                    """)) {
                operation.setString(1, correlationId);
                operation.setString(2, mailId);
                operation.setString(3, MailOperationState.PREPARED.name());
                operation.setInt(4, senderDbId);
                operation.setLong(5, now);
                operation.setLong(6, now);
                operation.executeUpdate();
            }
            audit(mailId, correlationId, senderDbId == 0 ? "PLUGIN" : "PLAYER", senderDbId,
                    "SEND_PREPARED", "Outgoing mail intent persisted");
            connection.commit();
            return new PreparedMail(mailId, correlationId);
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public java.util.Optional<OperationOutcome> operationOutcome(String correlationId) throws SQLException {
        if (correlationId == null || correlationId.isBlank()) return java.util.Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation.mail_id, operation.state AS operation_state, message.state AS mail_state
                FROM mail_operations operation JOIN mail_messages message ON message.id = operation.mail_id
                WHERE operation.correlation_id = ?
                """)) {
            statement.setString(1, correlationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? java.util.Optional.of(new OperationOutcome(result.getString("mail_id"),
                        result.getString("operation_state"), result.getString("mail_state"))) : java.util.Optional.empty();
            }
        }
    }

    public java.util.Optional<String> findMailIdByReference(String reference) throws SQLException {
        if (reference == null || reference.isBlank()) return java.util.Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM mail_messages WHERE id = ?
                UNION
                SELECT mail_id AS id FROM mail_operations WHERE correlation_id = ?
                LIMIT 1
                """)) {
            String normalized = reference.trim();
            statement.setString(1, normalized);
            statement.setString(2, normalized);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? java.util.Optional.of(result.getString("id")) : java.util.Optional.empty();
            }
        }
    }

    /** Finds a currently quarantined operation by its mail or correlation ID for an explicit admin review. */
    public Optional<ReconciliationEntry> findReconciliationEntryByReference(String reference) throws SQLException {
        if (reference == null || reference.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation.correlation_id, operation.mail_id, operation.operation_type, operation.error_detail,
                    message.sender_db_id, message.recipient_db_id, message.state, operation.created_at, operation.updated_at
                FROM mail_operations operation JOIN mail_messages message ON message.id = operation.mail_id
                WHERE operation.state = ? AND (operation.mail_id = ? OR operation.correlation_id = ?)
                LIMIT 1
                """)) {
            String normalized = reference.trim();
            statement.setString(1, MailOperationState.NEEDS_RECONCILIATION.name());
            statement.setString(2, normalized);
            statement.setString(3, normalized);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new ReconciliationEntry(result.getString("correlation_id"),
                        result.getString("mail_id"), result.getString("operation_type"),
                        result.getString("error_detail"), result.getInt("sender_db_id"),
                        result.getInt("recipient_db_id"), result.getString("state"), result.getLong("created_at"),
                        result.getLong("updated_at"))) : Optional.empty();
            }
        }
    }

    /** Finalizes a send only while the operation still has its expected state. */
    public boolean completeSend(PreparedMail prepared, int actorDbId) throws SQLException {
        return transitionSend(prepared, MailOperationState.PREPARED, MailOperationState.COMPLETED,
                MailMessageState.DELIVERED, "HELD_IN_MAIL", actorDbId, "SEND_COMPLETED",
                "Attachments transferred into mail custody");
    }

    /** Preserves every ambiguous external boundary for administrator recovery. */
    public boolean quarantineSend(PreparedMail prepared, int actorDbId, String detail) throws SQLException {
        return transitionSend(prepared, MailOperationState.PREPARED, MailOperationState.NEEDS_RECONCILIATION,
                MailMessageState.QUARANTINED, "QUARANTINED", actorDbId, "SEND_QUARANTINED", safe(detail));
    }

    /**
     * Startup recovery deliberately quarantines incomplete external workflows.
     * It never retries inventory mutation without a verified custody result.
     */
    public int quarantineUnfinishedOperations() throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            int changed;
            try (PreparedStatement operations = connection.prepareStatement("""
                    UPDATE mail_operations
                    SET state = ?, error_detail = 'Plugin restart before operation completion', updated_at = ?
                    WHERE state IN (?, ?, ?)
                    """)) {
                operations.setString(1, MailOperationState.NEEDS_RECONCILIATION.name());
                operations.setLong(2, now);
                operations.setString(3, MailOperationState.PREPARED.name());
                operations.setString(4, MailOperationState.APPLYING.name());
                operations.setString(5, MailOperationState.COMPENSATING.name());
                changed = operations.executeUpdate();
            }
            if (changed > 0) {
                try (PreparedStatement messages = connection.prepareStatement("""
                        UPDATE mail_messages SET state = ?, state_version = state_version + 1
                        WHERE id IN (SELECT mail_id FROM mail_operations WHERE state = ?)
                          AND state NOT IN (?, ?, ?)
                        """)) {
                    messages.setString(1, MailMessageState.QUARANTINED.name());
                    messages.setString(2, MailOperationState.NEEDS_RECONCILIATION.name());
                    messages.setString(3, MailMessageState.CLAIMED.name());
                    messages.setString(4, MailMessageState.RETURNED.name());
                    messages.setString(5, MailMessageState.DELETED.name());
                    messages.executeUpdate();
                }
            }
            connection.commit();
            return changed;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /** Read-only administrator queue for ambiguous cross-boundary operations. */
    public List<ReconciliationEntry> listReconciliationEntries(int limit) throws SQLException {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        List<ReconciliationEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation.correlation_id, operation.mail_id, operation.operation_type, operation.error_detail,
                       operation.created_at, operation.updated_at, message.sender_db_id, message.recipient_db_id,
                       message.state
                FROM mail_operations operation
                JOIN mail_messages message ON message.id = operation.mail_id
                WHERE operation.state = ?
                ORDER BY operation.updated_at ASC LIMIT ?
                """)) {
            statement.setString(1, MailOperationState.NEEDS_RECONCILIATION.name());
            statement.setInt(2, boundedLimit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new ReconciliationEntry(result.getString("correlation_id"), result.getString("mail_id"),
                            result.getString("operation_type"), result.getString("error_detail"),
                            result.getInt("sender_db_id"), result.getInt("recipient_db_id"), result.getString("state"),
                            result.getLong("created_at"), result.getLong("updated_at")));
                }
            }
        }
        return List.copyOf(entries);
    }

    /** Read-only operational timeline. Details stay in the database/log for controlled diagnosis. */
    public List<AuditEvent> listAuditEvents(String mailId, int limit) throws SQLException {
        if (mailId == null || mailId.isBlank()) return List.of();
        List<AuditEvent> events = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event_type, actor_type, created_at FROM mail_audit_events
                WHERE mail_id = ? ORDER BY created_at ASC LIMIT ?
                """)) {
            statement.setString(1, mailId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) events.add(new AuditEvent(result.getString("event_type"),
                        result.getString("actor_type"), result.getLong("created_at")));
            }
        }
        return List.copyOf(events);
    }

    /** Read-only operational overview; intentionally excludes message bodies and player identities. */
    public OperationalMetrics operationalMetrics() throws SQLException {
        return new OperationalMetrics(
                count("SELECT COUNT(*) FROM mail_operations WHERE state IN (?, ?, ?)",
                        MailOperationState.PREPARED.name(), MailOperationState.APPLYING.name(),
                        MailOperationState.COMPENSATING.name()),
                count("SELECT COUNT(*) FROM mail_operations WHERE state = ?", MailOperationState.NEEDS_RECONCILIATION.name()),
                count("SELECT COUNT(*) FROM mail_attachments WHERE custody_state = ?", "HELD_IN_MAIL"),
                count("SELECT COUNT(*) FROM mail_attachments WHERE custody_state = ?", "QUARANTINED"),
                oldestUnfinishedOperation());
    }

    private int count(String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private long oldestUnfinishedOperation() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(MIN(created_at), 0) FROM mail_operations
                WHERE state IN (?, ?, ?, ?)
                """)) {
            statement.setString(1, MailOperationState.PREPARED.name());
            statement.setString(2, MailOperationState.APPLYING.name());
            statement.setString(3, MailOperationState.COMPENSATING.name());
            statement.setString(4, MailOperationState.NEEDS_RECONCILIATION.name());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        }
    }

    public int activeMailboxCount(int recipientDbId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM mail_messages
                WHERE recipient_db_id = ? AND state NOT IN (?, ?)
                """)) {
            statement.setInt(1, recipientDbId);
            statement.setString(2, MailMessageState.DELETED.name());
            statement.setString(3, MailMessageState.RETURNED.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public int unreadMailboxCount(int recipientDbId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM mail_messages
                WHERE recipient_db_id = ? AND state = ?
                """)) {
            statement.setInt(1, recipientDbId);
            statement.setString(2, MailMessageState.DELIVERED.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Includes unfinished sends: a sender cannot bypass the cooldown by interrupting a mail operation. */
    public long mostRecentPlayerMailCreatedAt(int senderDbId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(MAX(created_at), 0) FROM mail_messages
                WHERE sender_db_id = ? AND sender_plugin = ''
                """)) {
            statement.setInt(1, senderDbId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    public List<MailSummary> listInbox(int recipientDbId, int limit) throws SQLException {
        List<MailSummary> messages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT message.id, message.subject, message.sender_name, message.created_at, message.state,
                       EXISTS(SELECT 1 FROM mail_attachments attachment
                              WHERE attachment.mail_id = message.id AND attachment.custody_state = 'HELD_IN_MAIL') AS has_attachments
                FROM mail_messages message
                WHERE message.recipient_db_id = ? AND message.state NOT IN (?, ?, ?)
                ORDER BY message.created_at DESC LIMIT ?
                """)) {
            statement.setInt(1, recipientDbId);
            statement.setString(2, MailMessageState.DELETED.name());
            statement.setString(3, MailMessageState.RETURNED.name());
            statement.setString(4, MailMessageState.ARCHIVED.name());
            statement.setInt(5, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new MailSummary(result.getString("id"), result.getString("subject"),
                            result.getString("sender_name"), result.getLong("created_at"), result.getString("state"),
                            result.getBoolean("has_attachments")));
                }
            }
        }
        return List.copyOf(messages);
    }

    public Optional<MailDetail> findInboxMail(int recipientDbId, String mailId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, subject, body, sender_name, state, created_at, cod_amount, cod_currency,
                       EXISTS(SELECT 1 FROM mail_attachments attachment
                              WHERE attachment.mail_id = mail_messages.id AND attachment.custody_state = 'HELD_IN_MAIL') AS has_attachments
                FROM mail_messages WHERE id = ? AND recipient_db_id = ?
                """)) {
            statement.setString(1, mailId);
            statement.setInt(2, recipientDbId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new MailDetail(result.getString("id"), result.getString("subject"),
                        result.getString("body"), result.getString("sender_name"), result.getString("state"),
                        result.getLong("created_at"), result.getBoolean("has_attachments"),
                        heldAttachments(result.getString("id")), result.getLong("cod_amount"), result.getString("cod_currency")));
            }
        }
    }

    public Optional<MailDetail> findOutboxMail(int senderDbId, String mailId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, subject, body, recipient_name, state, created_at, cod_amount, cod_currency,
                       EXISTS(SELECT 1 FROM mail_attachments attachment
                              WHERE attachment.mail_id = mail_messages.id AND attachment.custody_state = 'HELD_IN_MAIL') AS has_attachments
                FROM mail_messages WHERE id = ? AND sender_db_id = ? AND state <> ?
                """)) {
            statement.setString(1, mailId);
            statement.setInt(2, senderDbId);
            statement.setString(3, MailMessageState.DELETED.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new MailDetail(result.getString("id"), result.getString("subject"),
                        result.getString("body"), result.getString("recipient_name"), result.getString("state"),
                        result.getLong("created_at"), result.getBoolean("has_attachments"),
                        heldAttachments(result.getString("id")), result.getLong("cod_amount"), result.getString("cod_currency")));
            }
        }
    }

    /** Keeps audit evidence and mail rows while hiding an attachment-free outbox entry. */
    public boolean deleteOutboxMail(int senderDbId, String mailId) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND sender_db_id = ? AND state IN (?, ?, ?, ?)
                      AND NOT EXISTS(SELECT 1 FROM mail_attachments attachment
                                     WHERE attachment.mail_id = mail_messages.id AND attachment.custody_state = 'HELD_IN_MAIL')
                    """)) {
                statement.setString(1, MailMessageState.DELETED.name());
                statement.setString(2, mailId);
                statement.setInt(3, senderDbId);
                statement.setString(4, MailMessageState.DELIVERED.name());
                statement.setString(5, MailMessageState.READ.name());
                statement.setString(6, MailMessageState.ARCHIVED.name());
                statement.setString(7, MailMessageState.CLAIMED.name());
                if (statement.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            audit(mailId, "", "PLAYER", senderDbId, "OUTBOX_DELETED", "Attachment-free outbox entry hidden");
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /** Hides an attachment-free inbox entry without deleting operational evidence. */
    public boolean deleteInboxMail(int recipientDbId, String mailId) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND state IN (?, ?, ?, ?)
                      AND NOT EXISTS(SELECT 1 FROM mail_attachments attachment
                                     WHERE attachment.mail_id = mail_messages.id AND attachment.custody_state = 'HELD_IN_MAIL')
                    """)) {
                statement.setString(1, MailMessageState.DELETED.name());
                statement.setString(2, mailId);
                statement.setInt(3, recipientDbId);
                statement.setString(4, MailMessageState.DELIVERED.name());
                statement.setString(5, MailMessageState.READ.name());
                statement.setString(6, MailMessageState.ARCHIVED.name());
                statement.setString(7, MailMessageState.CLAIMED.name());
                if (statement.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            audit(mailId, "", "PLAYER", recipientDbId, "INBOX_DELETED", "Attachment-free inbox entry hidden");
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public boolean markRead(int recipientDbId, String mailId) throws SQLException {
        return updateRecipientState(recipientDbId, mailId, MailMessageState.DELIVERED, MailMessageState.READ, "read_at");
    }

    private List<MailAttachment> heldAttachments(String mailId) throws SQLException {
        List<MailAttachment> attachments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT item_name, item_variant, amount, checksum FROM mail_attachments
                WHERE mail_id = ? AND custody_state = 'HELD_IN_MAIL' ORDER BY id ASC
                """)) {
            statement.setString(1, mailId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    attachments.add(new MailAttachment(result.getString("item_name"), result.getInt("item_variant"),
                            result.getInt("amount"), result.getString("checksum")));
                }
            }
        }
        return List.copyOf(attachments);
    }

    public boolean archive(int recipientDbId, String mailId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mail_messages SET state = ?, state_version = state_version + 1, archived_at = ?
                WHERE id = ? AND recipient_db_id = ? AND state IN (?, ?, ?)
                  AND NOT EXISTS(SELECT 1 FROM mail_attachments attachment
                                 WHERE attachment.mail_id = mail_messages.id
                                   AND attachment.custody_state = 'HELD_IN_MAIL')
                """)) {
            statement.setString(1, MailMessageState.ARCHIVED.name());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, mailId);
            statement.setInt(4, recipientDbId);
            statement.setString(5, MailMessageState.DELIVERED.name());
            statement.setString(6, MailMessageState.READ.name());
            statement.setString(7, MailMessageState.CLAIMED.name());
            return statement.executeUpdate() == 1;
        }
    }

    /** Persists the claim boundary before any recipient inventory mutation. */
    public ClaimPreparation prepareClaim(int recipientDbId, String mailId) throws SQLException {
        if (recipientDbId <= 0 || mailId == null || mailId.isBlank()) throw new IllegalArgumentException("invalid claim");
        String correlationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<MailAttachment> attachments = attachments(mailId, "HELD_IN_MAIL");
            if (attachments.isEmpty()) {
                connection.rollback();
                return null;
            }
            MailMessageState sourceState;
            try (PreparedStatement source = connection.prepareStatement("""
                    SELECT state FROM mail_messages
                    WHERE id = ? AND recipient_db_id = ? AND state IN (?, ?, ?)
                    """)) {
                source.setString(1, mailId);
                source.setInt(2, recipientDbId);
                source.setString(3, MailMessageState.DELIVERED.name());
                source.setString(4, MailMessageState.READ.name());
                source.setString(5, MailMessageState.ARCHIVED.name());
                try (ResultSet result = source.executeQuery()) {
                    if (!result.next()) { connection.rollback(); return null; }
                    sourceState = MailMessageState.valueOf(result.getString("state"));
                }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND state IN (?, ?, ?)
                    """)) {
                message.setString(1, MailMessageState.CLAIMING.name());
                message.setString(2, mailId);
                message.setInt(3, recipientDbId);
                message.setString(4, MailMessageState.DELIVERED.name());
                message.setString(5, MailMessageState.READ.name());
                message.setString(6, MailMessageState.ARCHIVED.name());
                if (message.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }
            try (PreparedStatement attachmentState = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachmentState.setString(1, "CLAIMING");
                attachmentState.setString(2, mailId);
                attachmentState.setString(3, "HELD_IN_MAIL");
                if (attachmentState.executeUpdate() != attachments.size()) {
                    connection.rollback();
                    return null;
                }
            }
            try (PreparedStatement operation = connection.prepareStatement("""
                    INSERT INTO mail_operations(correlation_id, mail_id, operation_type, state, requested_by_db_id,
                        created_at, updated_at) VALUES (?, ?, 'CLAIM', ?, ?, ?, ?)
                    """)) {
                operation.setString(1, correlationId);
                operation.setString(2, mailId);
                operation.setString(3, MailOperationState.PREPARED.name());
                operation.setInt(4, recipientDbId);
                operation.setLong(5, now);
                operation.setLong(6, now);
                operation.executeUpdate();
            }
            audit(mailId, correlationId, "PLAYER", recipientDbId, "CLAIM_PREPARED", "Attachment claim intent persisted");
            try (PreparedStatement payment = connection.prepareStatement("""
                    SELECT sender_db_id, cod_amount, cod_currency FROM mail_messages WHERE id = ?
                    """)) {
                payment.setString(1, mailId);
                try (ResultSet result = payment.executeQuery()) {
                    if (!result.next()) { connection.rollback(); return null; }
                    connection.commit();
                    return new ClaimPreparation(mailId, correlationId, attachments, result.getInt("sender_db_id"),
                            result.getLong("cod_amount"), result.getString("cod_currency"), sourceState);
                }
            }
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public int senderForReturn(int recipientDbId, String mailId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sender_db_id FROM mail_messages
                WHERE id = ? AND recipient_db_id = ? AND sender_db_id > 0
                  AND state IN (?, ?, ?)
                  AND EXISTS(SELECT 1 FROM mail_attachments attachment
                             WHERE attachment.mail_id = mail_messages.id AND attachment.custody_state = 'HELD_IN_MAIL')
                """)) {
            statement.setString(1, mailId);
            statement.setInt(2, recipientDbId);
            statement.setString(3, MailMessageState.DELIVERED.name());
            statement.setString(4, MailMessageState.READ.name());
            statement.setString(5, MailMessageState.ARCHIVED.name());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    /**
     * Atomically turns held attachments into a new delivered return mail. No
     * inventory access or sender presence is required, so custody never leaves
     * the durable mail store during this workflow.
     */
    public ReturnMailResult returnAsMail(int recipientDbId, String mailId, int senderMailboxLimit) throws SQLException {
        if (recipientDbId <= 0 || mailId == null || mailId.isBlank() || senderMailboxLimit < 1) {
            throw new IllegalArgumentException("invalid return mail request");
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ReturnSource source = returnSource(recipientDbId, mailId);
            if (source == null || source.senderDbId() <= 0 || attachments(mailId, "HELD_IN_MAIL").isEmpty()) {
                connection.rollback();
                return ReturnMailResult.invalid();
            }
            if (activeMailboxCount(source.senderDbId()) >= senderMailboxLimit) {
                connection.rollback();
                return ReturnMailResult.senderMailboxFull();
            }
            String returnedMailId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            try (PreparedStatement message = connection.prepareStatement("""
                    INSERT INTO mail_messages(id, sender_db_id, sender_name, sender_plugin, recipient_db_id,
                        recipient_name, subject, body, state, cod_amount, cod_currency, created_at, delivered_at)
                    VALUES (?, ?, ?, '', ?, ?, ?, ?, ?, 0, '', ?, ?)
                    """)) {
                message.setString(1, returnedMailId);
                message.setInt(2, recipientDbId);
                message.setString(3, source.recipientName());
                message.setInt(4, source.senderDbId());
                message.setString(5, source.senderName());
                message.setString(6, "returned: " + source.subject());
                message.setString(7, source.body());
                message.setString(8, MailMessageState.DELIVERED.name());
                message.setLong(9, now);
                message.setLong(10, now);
                message.executeUpdate();
            }
            try (PreparedStatement operation = connection.prepareStatement("""
                    INSERT INTO mail_operations(correlation_id, mail_id, operation_type, state, requested_by_db_id,
                        created_at, updated_at) VALUES (?, ?, 'RETURN', ?, ?, ?, ?)
                    """)) {
                operation.setString(1, correlationId);
                operation.setString(2, returnedMailId);
                operation.setString(3, MailOperationState.COMPLETED.name());
                operation.setInt(4, recipientDbId);
                operation.setLong(5, now);
                operation.setLong(6, now);
                operation.executeUpdate();
            }
            try (PreparedStatement move = connection.prepareStatement("""
                    UPDATE mail_attachments SET mail_id = ?
                    WHERE mail_id = ? AND custody_state = 'HELD_IN_MAIL'
                    """)) {
                move.setString(1, returnedMailId);
                move.setString(2, mailId);
                if (move.executeUpdate() < 1) { connection.rollback(); return ReturnMailResult.invalid(); }
            }
            try (PreparedStatement original = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND state IN (?, ?, ?)
                    """)) {
                original.setString(1, MailMessageState.RETURNED.name());
                original.setString(2, mailId);
                original.setInt(3, recipientDbId);
                original.setString(4, MailMessageState.DELIVERED.name());
                original.setString(5, MailMessageState.READ.name());
                original.setString(6, MailMessageState.ARCHIVED.name());
                if (original.executeUpdate() != 1) { connection.rollback(); return ReturnMailResult.invalid(); }
            }
            audit(mailId, correlationId, "PLAYER", recipientDbId, "RETURN_MAIL_COMPLETED",
                    "Held attachments moved to returned mail " + returnedMailId);
            audit(returnedMailId, correlationId, "PLAYER", recipientDbId, "RETURN_MAIL_DELIVERED",
                    "Returned attachments delivered");
            connection.commit();
            return ReturnMailResult.completed(returnedMailId, correlationId);
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public ReturnSource returnSource(int recipientDbId, String mailId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sender_db_id, sender_name, recipient_name, subject, body FROM mail_messages
                WHERE id = ? AND recipient_db_id = ? AND state IN (?, ?, ?)
                """)) {
            statement.setString(1, mailId);
            statement.setInt(2, recipientDbId);
            statement.setString(3, MailMessageState.DELIVERED.name());
            statement.setString(4, MailMessageState.READ.name());
            statement.setString(5, MailMessageState.ARCHIVED.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new ReturnSource(result.getInt("sender_db_id"), result.getString("sender_name"),
                        result.getString("recipient_name"), result.getString("subject"), result.getString("body")) : null;
            }
        }
    }

    /** Starts a sender return only after the recipient initiated it and the sender is known. */
    public ReturnPreparation prepareReturn(int recipientDbId, String mailId, int senderDbId) throws SQLException {
        return prepareAttachmentReturn(recipientDbId, mailId, senderDbId, "PLAYER", recipientDbId, "RETURN");
    }

    public ReturnPreparation prepareExpiredReturn(int senderDbId, int recipientDbId, String mailId) throws SQLException {
        return prepareAttachmentReturn(recipientDbId, mailId, senderDbId, "SYSTEM", 0, "EXPIRY_RETURN");
    }

    private ReturnPreparation prepareAttachmentReturn(int recipientDbId, String mailId, int senderDbId,
            String actorType, int actorDbId, String eventPrefix) throws SQLException {
        if (recipientDbId <= 0 || senderDbId <= 0 || mailId == null || mailId.isBlank()) {
            throw new IllegalArgumentException("invalid return");
        }
        String correlationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<MailAttachment> attachments = attachments(mailId, "HELD_IN_MAIL");
            if (attachments.isEmpty()) { connection.rollback(); return null; }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND sender_db_id = ? AND state IN (?, ?, ?)
                    """)) {
                message.setString(1, MailMessageState.RETURNING.name());
                message.setString(2, mailId);
                message.setInt(3, recipientDbId);
                message.setInt(4, senderDbId);
                message.setString(5, MailMessageState.DELIVERED.name());
                message.setString(6, MailMessageState.READ.name());
                message.setString(7, MailMessageState.ARCHIVED.name());
                if (message.executeUpdate() != 1) { connection.rollback(); return null; }
            }
            try (PreparedStatement attachmentState = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachmentState.setString(1, "RETURNING");
                attachmentState.setString(2, mailId);
                attachmentState.setString(3, "HELD_IN_MAIL");
                if (attachmentState.executeUpdate() != attachments.size()) { connection.rollback(); return null; }
            }
            try (PreparedStatement operation = connection.prepareStatement("""
                    INSERT INTO mail_operations(correlation_id, mail_id, operation_type, state, requested_by_db_id,
                        created_at, updated_at) VALUES (?, ?, 'RETURN', ?, ?, ?, ?)
                    """)) {
                operation.setString(1, correlationId);
                operation.setString(2, mailId);
                operation.setString(3, MailOperationState.PREPARED.name());
                operation.setInt(4, recipientDbId);
                operation.setLong(5, now);
                operation.setLong(6, now);
                operation.executeUpdate();
            }
            audit(mailId, correlationId, actorType, actorDbId, eventPrefix + "_PREPARED",
                    "Attachment return intent persisted");
            connection.commit();
            return new ReturnPreparation(mailId, correlationId, senderDbId, recipientDbId, attachments, actorType,
                    actorDbId, eventPrefix);
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public boolean completeReturn(ReturnPreparation returned, int recipientDbId) throws SQLException {
        return transitionReturn(returned, recipientDbId, MailOperationState.COMPLETED, MailMessageState.RETURNED,
                "RETURNED", "COMPLETED", "Attachments returned to sender inventory");
    }

    public boolean quarantineReturn(ReturnPreparation returned, int recipientDbId, String detail) throws SQLException {
        return transitionReturn(returned, recipientDbId, MailOperationState.NEEDS_RECONCILIATION,
                MailMessageState.QUARANTINED, "QUARANTINED", "QUARANTINED", safe(detail));
    }

    private boolean transitionReturn(ReturnPreparation returned, int recipientDbId, MailOperationState operationState,
            MailMessageState mailState, String attachmentState, String eventSuffix, String detail) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            try (PreparedStatement operation = connection.prepareStatement("""
                    UPDATE mail_operations SET state = ?, error_detail = ?, updated_at = ?
                    WHERE correlation_id = ? AND mail_id = ? AND state = ?
                    """)) {
                operation.setString(1, operationState.name());
                operation.setString(2, operationState == MailOperationState.NEEDS_RECONCILIATION ? detail : "");
                operation.setLong(3, now);
                operation.setString(4, returned.correlationId());
                operation.setString(5, returned.mailId());
                operation.setString(6, MailOperationState.PREPARED.name());
                if (operation.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND state = ?
                    """)) {
                message.setString(1, mailState.name());
                message.setString(2, returned.mailId());
                message.setInt(3, recipientDbId);
                message.setString(4, MailMessageState.RETURNING.name());
                if (message.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement attachments = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachments.setString(1, attachmentState);
                attachments.setString(2, returned.mailId());
                attachments.setString(3, "RETURNING");
                if (attachments.executeUpdate() != returned.attachments().size()) { connection.rollback(); return false; }
            }
            audit(returned.mailId(), returned.correlationId(), returned.actorType(), returned.actorDbId(),
                    returned.eventPrefix() + "_" + eventSuffix, detail);
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public boolean completeClaim(ClaimPreparation claim, int recipientDbId) throws SQLException {
        MailMessageState nextState = claim.sourceState() == MailMessageState.ARCHIVED
                ? MailMessageState.ARCHIVED : MailMessageState.READ;
        return transitionClaim(claim, recipientDbId, MailOperationState.COMPLETED, nextState,
                "CLAIMED", "CLAIM_COMPLETED", "Attachments granted to recipient inventory");
    }

    public boolean quarantineClaim(ClaimPreparation claim, int recipientDbId, String detail) throws SQLException {
        return transitionClaim(claim, recipientDbId, MailOperationState.NEEDS_RECONCILIATION,
                MailMessageState.QUARANTINED, "QUARANTINED", "CLAIM_QUARANTINED", safe(detail));
    }

    /** Reverts a prepared claim before any item mutation when COD payment was rejected. */
    public boolean cancelClaimPayment(ClaimPreparation claim, int recipientDbId, String detail) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement operation = connection.prepareStatement("""
                    UPDATE mail_operations SET state = ?, error_detail = ?, updated_at = ?
                    WHERE correlation_id = ? AND mail_id = ? AND state = ?
                    """)) {
                operation.setString(1, MailOperationState.COMPLETED.name());
                operation.setString(2, safe(detail));
                operation.setLong(3, System.currentTimeMillis());
                operation.setString(4, claim.correlationId());
                operation.setString(5, claim.mailId());
                operation.setString(6, MailOperationState.PREPARED.name());
                if (operation.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND state = ?
                    """)) {
                message.setString(1, claim.sourceState().name());
                message.setString(2, claim.mailId());
                message.setInt(3, recipientDbId);
                message.setString(4, MailMessageState.CLAIMING.name());
                if (message.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement attachments = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachments.setString(1, "HELD_IN_MAIL");
                attachments.setString(2, claim.mailId());
                attachments.setString(3, "CLAIMING");
                if (attachments.executeUpdate() != claim.attachments().size()) { connection.rollback(); return false; }
            }
            audit(claim.mailId(), claim.correlationId(), "PLAYER", recipientDbId, "CLAIM_PAYMENT_REJECTED", safe(detail));
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private boolean transitionClaim(ClaimPreparation claim, int recipientDbId, MailOperationState operationState,
            MailMessageState mailState, String attachmentState, String auditEvent, String detail) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            try (PreparedStatement operation = connection.prepareStatement("""
                    UPDATE mail_operations SET state = ?, error_detail = ?, updated_at = ?
                    WHERE correlation_id = ? AND mail_id = ? AND state = ?
                    """)) {
                operation.setString(1, operationState.name());
                operation.setString(2, operationState == MailOperationState.NEEDS_RECONCILIATION ? detail : "");
                operation.setLong(3, now);
                operation.setString(4, claim.correlationId());
                operation.setString(5, claim.mailId());
                operation.setString(6, MailOperationState.PREPARED.name());
                if (operation.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND recipient_db_id = ? AND state = ?
                    """)) {
                message.setString(1, mailState.name());
                message.setString(2, claim.mailId());
                message.setInt(3, recipientDbId);
                message.setString(4, MailMessageState.CLAIMING.name());
                if (message.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement attachments = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachments.setString(1, attachmentState);
                attachments.setString(2, claim.mailId());
                attachments.setString(3, "CLAIMING");
                if (attachments.executeUpdate() != claim.attachments().size()) { connection.rollback(); return false; }
            }
            audit(claim.mailId(), claim.correlationId(), "PLAYER", recipientDbId, auditEvent, detail);
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private List<MailAttachment> attachments(String mailId, String custodyState) throws SQLException {
        List<MailAttachment> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT item_name, item_variant, amount, checksum FROM mail_attachments
                WHERE mail_id = ? AND custody_state = ? ORDER BY id
                """)) {
            statement.setString(1, mailId);
            statement.setString(2, custodyState);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) entries.add(new MailAttachment(result.getString("item_name"),
                        result.getInt("item_variant"), result.getInt("amount"), result.getString("checksum")));
            }
        }
        return List.copyOf(entries);
    }

    public List<ExpiredReturnCandidate> expiredReturnCandidates(int senderDbId, long createdBefore, int limit)
            throws SQLException {
        List<ExpiredReturnCandidate> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, recipient_db_id FROM mail_messages
                WHERE sender_db_id = ? AND created_at <= ? AND state IN (?, ?, ?)
                  AND EXISTS(SELECT 1 FROM mail_attachments attachment
                             WHERE attachment.mail_id = mail_messages.id AND attachment.custody_state = 'HELD_IN_MAIL')
                ORDER BY created_at ASC LIMIT ?
                """)) {
            statement.setInt(1, senderDbId);
            statement.setLong(2, createdBefore);
            statement.setString(3, MailMessageState.DELIVERED.name());
            statement.setString(4, MailMessageState.READ.name());
            statement.setString(5, MailMessageState.ARCHIVED.name());
            statement.setInt(6, Math.max(1, Math.min(limit, 25)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) candidates.add(new ExpiredReturnCandidate(result.getString("id"),
                        result.getInt("recipient_db_id")));
            }
        }
        return List.copyOf(candidates);
    }

    private boolean updateRecipientState(int recipientDbId, String mailId, MailMessageState from, MailMessageState to,
            String timestampColumn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE mail_messages SET state = ?, state_version = state_version + 1, "
                + timestampColumn + " = ? WHERE id = ? AND recipient_db_id = ? AND state = ?")) {
            statement.setString(1, to.name());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, mailId);
            statement.setInt(4, recipientDbId);
            statement.setString(5, from.name());
            return statement.executeUpdate() == 1;
        }
    }

    public List<MailSummary> listOutbox(int senderDbId, int limit) throws SQLException {
        return listSummaries("sender_db_id", senderDbId, "state <> ?", MailMessageState.DELETED.name(), limit, false);
    }

    public List<MailSummary> listArchive(int recipientDbId, int limit) throws SQLException {
        return listSummaries("recipient_db_id", recipientDbId, "state = ?", MailMessageState.ARCHIVED.name(), limit, true);
    }

    private List<MailSummary> listSummaries(String playerColumn, int playerDbId, String statePredicate, String state,
            int limit, boolean senderAsCounterparty) throws SQLException {
        List<MailSummary> messages = new ArrayList<>();
        String counterparty = senderAsCounterparty ? "sender_name" : "recipient_name";
        String sql = "SELECT id, subject, " + counterparty + " AS counterparty, created_at, state, "
                + "EXISTS(SELECT 1 FROM mail_attachments attachment WHERE attachment.mail_id = mail_messages.id "
                + "AND attachment.custody_state = 'HELD_IN_MAIL') AS has_attachments "
                + "FROM mail_messages WHERE " + playerColumn + " = ? AND " + statePredicate
                + " ORDER BY created_at DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerDbId);
            statement.setString(2, state);
            statement.setInt(3, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new MailSummary(result.getString("id"), result.getString("subject"),
                            result.getString("counterparty"), result.getLong("created_at"), result.getString("state"),
                            result.getBoolean("has_attachments")));
                }
            }
        }
        return List.copyOf(messages);
    }

    /**
     * Resolves only a quarantined SEND after an administrator independently
     * verified custody. This method never invokes an inventory or Wallet API.
     */
    public boolean resolveQuarantinedSend(String correlationId, int adminDbId, VerifiedSendOutcome outcome,
            String reason) throws SQLException {
        if (correlationId == null || correlationId.isBlank() || adminDbId <= 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("correlation id, administrator, and reason are required");
        }
        if (outcome == null) throw new IllegalArgumentException("verified outcome is required");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String mailId = null;
            try (PreparedStatement lookup = connection.prepareStatement("""
                    SELECT mail_id FROM mail_operations
                    WHERE correlation_id = ? AND operation_type = 'SEND' AND state = ?
                    """)) {
                lookup.setString(1, correlationId);
                lookup.setString(2, MailOperationState.NEEDS_RECONCILIATION.name());
                try (ResultSet result = lookup.executeQuery()) {
                    if (result.next()) mailId = result.getString(1);
                }
            }
            if (mailId == null) {
                connection.rollback();
                return false;
            }
            MailMessageState mailState = outcome == VerifiedSendOutcome.HELD_IN_MAIL
                    ? MailMessageState.DELIVERED : MailMessageState.RETURNED;
            String attachmentState = outcome == VerifiedSendOutcome.HELD_IN_MAIL ? "HELD_IN_MAIL" : "RETURNED";
            try (PreparedStatement operation = connection.prepareStatement("""
                    UPDATE mail_operations SET state = ?, error_detail = ?, updated_at = ?
                    WHERE correlation_id = ? AND operation_type = 'SEND' AND state = ?
                    """)) {
                operation.setString(1, MailOperationState.COMPLETED.name());
                operation.setString(2, "Verified by administrator: " + reason.trim());
                operation.setLong(3, System.currentTimeMillis());
                operation.setString(4, correlationId);
                operation.setString(5, MailOperationState.NEEDS_RECONCILIATION.name());
                if (operation.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1
                    WHERE id = ? AND state = ?
                    """)) {
                message.setString(1, mailState.name());
                message.setString(2, mailId);
                message.setString(3, MailMessageState.QUARANTINED.name());
                if (message.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement attachments = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachments.setString(1, attachmentState);
                attachments.setString(2, mailId);
                attachments.setString(3, "QUARANTINED");
                attachments.executeUpdate();
            }
            audit(mailId, correlationId, "ADMIN", adminDbId,
                    outcome == VerifiedSendOutcome.HELD_IN_MAIL ? "ADMIN_SEND_VERIFIED_HELD"
                            : "ADMIN_SEND_VERIFIED_RETURNED", reason.trim());
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public Optional<CodClaimReconciliation> codClaimReconciliation(String correlationId) throws SQLException {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation.mail_id, message.sender_db_id, message.recipient_db_id, message.cod_amount,
                    message.cod_currency FROM mail_operations operation JOIN mail_messages message ON message.id = operation.mail_id
                WHERE operation.correlation_id = ? AND operation.operation_type = 'CLAIM' AND operation.state = ?
                """)) {
            statement.setString(1, correlationId);
            statement.setString(2, MailOperationState.NEEDS_RECONCILIATION.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new CodClaimReconciliation(correlationId, result.getString("mail_id"),
                        result.getInt("sender_db_id"), result.getInt("recipient_db_id"), result.getLong("cod_amount"),
                        result.getString("cod_currency"))) : Optional.empty();
            }
        }
    }

    /** Records an independently verified post-payment claim without calling external APIs. */
    public boolean resolveQuarantinedClaimed(String correlationId, int adminDbId, String reason) throws SQLException {
        return resolveQuarantinedClaim(correlationId, adminDbId, reason, true);
    }

    /** Finalizes a successful idempotent COD refund by restoring mail custody. */
    public boolean resolveQuarantinedClaimRefunded(String correlationId, int adminDbId, String reason) throws SQLException {
        return resolveQuarantinedClaim(correlationId, adminDbId, reason, false);
    }

    private boolean resolveQuarantinedClaim(String correlationId, int adminDbId, String reason, boolean claimed)
            throws SQLException {
        if (correlationId == null || correlationId.isBlank() || adminDbId <= 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("correlation id, administrator, and reason are required");
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String mailId = null;
            try (PreparedStatement lookup = connection.prepareStatement("""
                    SELECT mail_id FROM mail_operations WHERE correlation_id = ? AND operation_type = 'CLAIM' AND state = ?
                    """)) {
                lookup.setString(1, correlationId);
                lookup.setString(2, MailOperationState.NEEDS_RECONCILIATION.name());
                try (ResultSet result = lookup.executeQuery()) { if (result.next()) mailId = result.getString(1); }
            }
            if (mailId == null) { connection.rollback(); return false; }
            try (PreparedStatement operation = connection.prepareStatement("""
                    UPDATE mail_operations SET state = ?, error_detail = ?, updated_at = ? WHERE correlation_id = ? AND state = ?
                    """)) {
                operation.setString(1, MailOperationState.COMPLETED.name());
                operation.setString(2, "Verified by administrator: " + reason.trim());
                operation.setLong(3, System.currentTimeMillis());
                operation.setString(4, correlationId);
                operation.setString(5, MailOperationState.NEEDS_RECONCILIATION.name());
                if (operation.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1 WHERE id = ? AND state = ?
                    """)) {
                message.setString(1, claimed ? MailMessageState.CLAIMED.name() : MailMessageState.DELIVERED.name());
                message.setString(2, mailId);
                message.setString(3, MailMessageState.QUARANTINED.name());
                if (message.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement attachments = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ? AND custody_state = ?
                    """)) {
                attachments.setString(1, claimed ? "CLAIMED" : "HELD_IN_MAIL");
                attachments.setString(2, mailId);
                attachments.setString(3, "QUARANTINED");
                attachments.executeUpdate();
            }
            audit(mailId, correlationId, "ADMIN", adminDbId,
                    claimed ? "ADMIN_CLAIM_VERIFIED_CLAIMED" : "ADMIN_CLAIM_COD_REFUNDED", reason.trim());
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private boolean transitionSend(PreparedMail prepared, MailOperationState expectedOperationState,
            MailOperationState nextOperationState, MailMessageState nextMailState, String attachmentState, int actorDbId,
            String eventType, String detail) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            try (PreparedStatement operation = connection.prepareStatement("""
                    UPDATE mail_operations SET state = ?, error_detail = ?, updated_at = ?
                    WHERE correlation_id = ? AND mail_id = ? AND state = ?
                    """)) {
                operation.setString(1, nextOperationState.name());
                operation.setString(2, nextOperationState == MailOperationState.NEEDS_RECONCILIATION ? detail : "");
                operation.setLong(3, now);
                operation.setString(4, prepared.correlationId());
                operation.setString(5, prepared.mailId());
                operation.setString(6, expectedOperationState.name());
                if (operation.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement message = connection.prepareStatement("""
                    UPDATE mail_messages SET state = ?, state_version = state_version + 1, delivered_at = ?
                    WHERE id = ? AND state = ?
                    """)) {
                message.setString(1, nextMailState.name());
                message.setLong(2, nextMailState == MailMessageState.DELIVERED ? now : 0L);
                message.setString(3, prepared.mailId());
                message.setString(4, MailMessageState.PENDING_SEND.name());
                if (message.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement attachments = connection.prepareStatement("""
                    UPDATE mail_attachments SET custody_state = ? WHERE mail_id = ?
                    """)) {
                attachments.setString(1, attachmentState);
                attachments.setString(2, prepared.mailId());
                attachments.executeUpdate();
            }
            audit(prepared.mailId(), prepared.correlationId(), "PLAYER", actorDbId, eventType, detail);
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void audit(String mailId, String correlationId, String actorType, int actorDbId, String eventType,
            String detail) throws SQLException {
        try (PreparedStatement event = connection.prepareStatement("""
                INSERT INTO mail_audit_events(mail_id, correlation_id, actor_type, actor_db_id, event_type, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            event.setString(1, mailId);
            event.setString(2, correlationId);
            event.setString(3, actorType);
            event.setInt(4, actorDbId);
            event.setString(5, eventType);
            event.setString(6, detail);
            event.setLong(7, System.currentTimeMillis());
            event.executeUpdate();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record PreparedMail(String mailId, String correlationId) {
    }

    public record ClaimPreparation(String mailId, String correlationId, List<MailAttachment> attachments, int senderDbId,
            long codAmount, String codCurrency, MailMessageState sourceState) {
    }

    public record ReturnPreparation(String mailId, String correlationId, int senderDbId, int recipientDbId,
            List<MailAttachment> attachments, String actorType, int actorDbId, String eventPrefix) {
    }

    public record ReturnMailResult(boolean success, boolean mailboxFull, String returnedMailId, String correlationId) {
        static ReturnMailResult completed(String returnedMailId, String correlationId) {
            return new ReturnMailResult(true, false, returnedMailId, correlationId);
        }
        static ReturnMailResult senderMailboxFull() { return new ReturnMailResult(false, true, "", ""); }
        static ReturnMailResult invalid() { return new ReturnMailResult(false, false, "", ""); }
    }

    public record ReturnSource(int senderDbId, String senderName, String recipientName, String subject, String body) {
    }

    public record ExpiredReturnCandidate(String mailId, int recipientDbId) {
    }

    public enum VerifiedSendOutcome {
        HELD_IN_MAIL,
        RETURNED_TO_SENDER
    }

    public record CodClaimReconciliation(String correlationId, String mailId, int senderDbId, int recipientDbId,
            long codAmount, String codCurrency) {
    }

    public record ReconciliationEntry(String correlationId, String mailId, String operationType, String detail,
            int senderDbId, int recipientDbId, String mailState, long createdAt, long updatedAt) {
    }

    public record AuditEvent(String eventType, String actorType, long createdAt) {
    }

    public record OperationalMetrics(int activeOperations, int reconciliationOperations, int heldAttachments,
            int quarantinedAttachments, long oldestUnfinishedOperationAt) {
    }

    public record OperationOutcome(String mailId, String operationState, String mailState) {
    }

    public record MailSummary(String id, String subject, String senderName, long createdAt, String state,
            boolean hasAttachments) {
    }

    public record MailDetail(String id, String subject, String body, String counterpartyName, String state, long createdAt,
            boolean hasAttachments, List<MailAttachment> attachments, long codAmount, String codCurrency) {
    }
}
