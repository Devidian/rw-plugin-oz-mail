package de.omegazirkel.risingworld.mail;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import net.risingworld.api.objects.Player;

/** First mail saga: durable intent, sender inventory custody, then delivery. */
public final class MailService {
    private final MailDatabase database;
    private final MailSettings settings;
    private final WalletBridge wallet;
    private final MailboxCapacityService mailboxCapacity;

    public MailService(MailDatabase database, MailSettings settings, WalletBridge wallet,
            MailboxCapacityService mailboxCapacity) {
        this.database = database;
        this.settings = settings;
        this.wallet = wallet;
        this.mailboxCapacity = mailboxCapacity;
    }

    public MailSendResult sendPlayerMail(Player sender, int recipientDbId, String recipientName, String subject,
            String body, List<MailAttachment> attachments) {
        return sendPlayerMail(sender, recipientDbId, recipientName, subject, body, attachments, 0L, "");
    }

    public MailSendResult sendPlayerMail(Player sender, int recipientDbId, String recipientName, String subject,
            String body, List<MailAttachment> attachments, long codAmount, String codCurrency) {
        if (sender == null || sender.getDbID() <= 0) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "sender has no database id");
        }
        MailSendResult validation = validate(recipientDbId, subject, body, attachments, codAmount);
        if (validation != null) return validation;
        String resolvedCodCurrency = codAmount == 0L ? "" : (codCurrency == null || codCurrency.isBlank()
                ? (wallet == null ? "" : wallet.defaultCurrencyIdentifier()) : codCurrency.trim());
        if (codAmount > 0L && resolvedCodCurrency.isBlank()) {
            return MailSendResult.failed(MailResultCode.PAYMENT_FAILED, "Wallet default currency is unavailable");
        }
        try {
            long lastSend = database.mostRecentPlayerMailCreatedAt(sender.getDbID());
            long remainingMillis = lastSend + (settings.sendCooldownSeconds * 1000L) - System.currentTimeMillis();
            if (remainingMillis > 0) {
                long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
                return MailSendResult.failed(MailResultCode.COOLDOWN_ACTIVE,
                        "send cooldown active for " + remainingSeconds + " seconds");
            }
            if (database.activeMailboxCount(recipientDbId) >= mailboxCapacity(recipientDbId)) {
                return MailSendResult.failed(MailResultCode.MAILBOX_FULL, "recipient mailbox limit reached");
            }
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(sender.getDbID(), sender.getName(),
                    recipientDbId, recipientName, subject, body, attachments, codAmount, resolvedCodCurrency, "");
            MailInventoryTransfer.TransferResult transfer = MailInventoryTransfer.removeAll(sender, attachments);
            if (!transfer.complete()) {
                database.quarantineSend(prepared, sender.getDbID(), transfer.detail());
                return MailSendResult.quarantined(prepared.mailId(), prepared.correlationId(), transfer.detail());
            }
            if (!database.completeSend(prepared, sender.getDbID())) {
                database.quarantineSend(prepared, sender.getDbID(), "Mail state changed while completing send");
                return MailSendResult.quarantined(prepared.mailId(), prepared.correlationId(),
                        "Mail state changed while completing send");
            }
            return MailSendResult.completed(prepared.mailId(), prepared.correlationId());
        } catch (SQLException | RuntimeException ex) {
            return MailSendResult.failed(MailResultCode.OPERATION_FAILED, ex.getMessage());
        }
    }

    public List<MailDatabase.MailSummary> inbox(Player player) {
        if (player == null || player.getDbID() <= 0) return List.of();
        try {
            return database.listInbox(player.getDbID(), mailboxCapacity(player.getDbID()));
        } catch (SQLException ex) {
            return List.of();
        }
    }

    public int mailboxUsage(Player player) {
        if (player == null || player.getDbID() <= 0) return 0;
        try {
            return database.activeMailboxCount(player.getDbID());
        } catch (SQLException ex) {
            return 0;
        }
    }

    public int unreadCount(Player player) {
        if (player == null || player.getDbID() <= 0) return 0;
        try {
            return database.unreadMailboxCount(player.getDbID());
        } catch (SQLException ex) {
            return 0;
        }
    }

    public List<MailDatabase.MailSummary> outbox(Player player) {
        return summaries(player, false);
    }

    public Optional<MailDatabase.MailDetail> inboxMail(Player player, String mailId) {
        if (player == null || player.getDbID() <= 0) return Optional.empty();
        try {
            Optional<MailDatabase.MailDetail> detail = database.findInboxMail(player.getDbID(), mailId);
            if (detail.isPresent() && MailMessageState.DELIVERED.name().equals(detail.get().state())) {
                database.markRead(player.getDbID(), mailId);
                return database.findInboxMail(player.getDbID(), mailId);
            }
            return detail;
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    public boolean archive(Player player, String mailId) {
        if (player == null || player.getDbID() <= 0) return false;
        try {
            return database.archive(player.getDbID(), mailId);
        } catch (SQLException ex) {
            return false;
        }
    }

    public Optional<MailDatabase.MailDetail> outboxMail(Player player, String mailId) {
        if (player == null || player.getDbID() <= 0) return Optional.empty();
        try {
            return database.findOutboxMail(player.getDbID(), mailId);
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    public boolean deleteOutboxMail(Player player, String mailId) {
        if (player == null || player.getDbID() <= 0) return false;
        try {
            return database.deleteOutboxMail(player.getDbID(), mailId);
        } catch (SQLException ex) {
            return false;
        }
    }

    public boolean deleteInboxMail(Player player, String mailId) {
        if (player == null || player.getDbID() <= 0) return false;
        try {
            return database.deleteInboxMail(player.getDbID(), mailId);
        } catch (SQLException ex) {
            return false;
        }
    }

    public MailSendResult claim(Player player, String mailId) {
        if (player == null || player.getDbID() <= 0) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "recipient has no database id");
        }
        try {
            MailDatabase.ClaimPreparation claim = database.prepareClaim(player.getDbID(), mailId);
            if (claim == null) return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "mail is not claimable");
            if (claim.codAmount() > 0L) {
                WalletBridge.TransferResult payment = wallet == null ? WalletBridge.TransferResult.unavailable()
                        : wallet.transferIdempotent(player.getDbID(), claim.senderDbId(), claim.codAmount(),
                                "OZMail COD " + claim.mailId(), claim.codCurrency(), "mail:cod:" + claim.correlationId());
                if (!payment.success()) {
                    database.cancelClaimPayment(claim, player.getDbID(), "COD payment rejected: " + payment.errorCode());
                    return MailSendResult.failed(MailResultCode.PAYMENT_FAILED, payment.message());
                }
            }
            MailInventoryTransfer.TransferResult transfer = MailInventoryTransfer.restoreAll(player, claim.attachments());
            if (!transfer.complete()) {
                database.quarantineClaim(claim, player.getDbID(), transfer.detail());
                return MailSendResult.quarantined(claim.mailId(), claim.correlationId(), transfer.detail());
            }
            if (!database.completeClaim(claim, player.getDbID())) {
                database.quarantineClaim(claim, player.getDbID(), "Claim state changed while completing inventory grant");
                return MailSendResult.quarantined(claim.mailId(), claim.correlationId(),
                        "Claim state changed while completing inventory grant");
            }
            return MailSendResult.completed(claim.mailId(), claim.correlationId());
        } catch (SQLException | RuntimeException ex) {
            return MailSendResult.failed(MailResultCode.OPERATION_FAILED, ex.getMessage());
        }
    }

    public MailSendResult returnToSender(Player recipient, String mailId) {
        if (recipient == null || recipient.getDbID() <= 0) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "recipient has no database id");
        }
        try {
            MailDatabase.ReturnSource source = database.returnSource(recipient.getDbID(), mailId);
            if (source == null) return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "mail is not returnable");
            MailDatabase.ReturnMailResult returned = database.returnAsMail(recipient.getDbID(), mailId,
                    mailboxCapacity(source.senderDbId()));
            if (returned.success()) return MailSendResult.completed(returned.returnedMailId(), returned.correlationId());
            return MailSendResult.failed(returned.mailboxFull() ? MailResultCode.MAILBOX_FULL : MailResultCode.INVALID_REQUEST,
                    returned.mailboxFull() ? "sender mailbox is full" : "mail is not returnable");
        } catch (SQLException | RuntimeException ex) {
            return MailSendResult.failed(MailResultCode.OPERATION_FAILED, ex.getMessage());
        }
    }

    private int mailboxCapacity(int playerDbId) {
        return mailboxCapacity == null ? settings.mailboxLimit
                : mailboxCapacity.mailboxCapacity(playerDbId, settings.mailboxLimit);
    }

    /** Bounded login-time expiry pass; ambiguous inventory boundaries stay quarantined. */
    public int expireAttachmentsForSender(Player sender) {
        if (sender == null || sender.getDbID() <= 0) return 0;
        long cutoff = System.currentTimeMillis() - (settings.attachmentExpiryDays * 86_400_000L);
        int completed = 0;
        try {
            for (MailDatabase.ExpiredReturnCandidate candidate : database.expiredReturnCandidates(sender.getDbID(), cutoff, 10)) {
                MailDatabase.ReturnPreparation returned = database.prepareExpiredReturn(sender.getDbID(),
                        candidate.recipientDbId(), candidate.mailId());
                if (returned == null) continue;
                MailInventoryTransfer.TransferResult transfer = MailInventoryTransfer.restoreAll(sender,
                        returned.attachments());
                if (!transfer.complete()) {
                    database.quarantineReturn(returned, candidate.recipientDbId(), transfer.detail());
                    continue;
                }
                if (database.completeReturn(returned, candidate.recipientDbId())) completed++;
                else database.quarantineReturn(returned, candidate.recipientDbId(),
                        "Expiry return state changed while completing sender inventory grant");
            }
        } catch (SQLException | RuntimeException ex) {
            return completed;
        }
        return completed;
    }

    public List<MailDatabase.ReconciliationEntry> reconciliationEntries(Player admin) {
        if (admin == null || !admin.isAdmin()) return List.of();
        try {
            return database.listReconciliationEntries(100);
        } catch (SQLException ex) {
            return List.of();
        }
    }

    public List<MailDatabase.AuditEvent> auditEvents(Player admin, String mailId) {
        if (admin == null || !admin.isAdmin()) return List.of();
        try {
            return database.listAuditEvents(mailId, 100);
        } catch (SQLException ex) {
            return List.of();
        }
    }

    public Optional<String> findMailIdByReference(Player admin, String reference) {
        if (admin == null || !admin.isAdmin()) return Optional.empty();
        try {
            return database.findMailIdByReference(reference);
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    public Optional<MailDatabase.ReconciliationEntry> findReconciliationEntryByReference(Player admin,
            String reference) {
        if (admin == null || !admin.isAdmin()) return Optional.empty();
        try {
            return database.findReconciliationEntryByReference(reference);
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    public boolean resolveQuarantinedSend(Player admin, String correlationId, MailDatabase.VerifiedSendOutcome outcome,
            String reason) {
        if (admin == null || !admin.isAdmin()) return false;
        try {
            return database.resolveQuarantinedSend(correlationId, admin.getDbID(), outcome, reason);
        } catch (SQLException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean resolveQuarantinedClaimed(Player admin, String correlationId, String reason) {
        if (admin == null || !admin.isAdmin()) return false;
        try {
            return database.resolveQuarantinedClaimed(correlationId, admin.getDbID(), reason);
        } catch (SQLException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean refundQuarantinedCodClaim(Player admin, String correlationId, String reason) {
        if (admin == null || !admin.isAdmin()) return false;
        try {
            Optional<MailDatabase.CodClaimReconciliation> reconciliation = database.codClaimReconciliation(correlationId);
            if (reconciliation.isEmpty() || reconciliation.get().codAmount() <= 0L) return false;
            MailDatabase.CodClaimReconciliation claim = reconciliation.get();
            WalletBridge.TransferResult refund = wallet == null ? WalletBridge.TransferResult.unavailable()
                    : wallet.transferIdempotent(claim.senderDbId(), claim.recipientDbId(), claim.codAmount(),
                            "OZMail COD refund " + claim.mailId(), claim.codCurrency(),
                            "mail:cod:refund:" + claim.correlationId());
            if (!refund.success()) return false;
            return database.resolveQuarantinedClaimRefunded(correlationId, admin.getDbID(), reason);
        } catch (SQLException | IllegalArgumentException ex) {
            return false;
        }
    }

    public MailDatabase.OperationalMetrics operationalMetrics(Player admin) {
        if (admin == null || !admin.isAdmin()) return new MailDatabase.OperationalMetrics(0, 0, 0, 0, 0);
        try {
            return database.operationalMetrics();
        } catch (SQLException ex) {
            return new MailDatabase.OperationalMetrics(0, 0, 0, 0, 0);
        }
    }

    public List<MailDatabase.MailSummary> archive(Player player) {
        return summaries(player, true);
    }

    private List<MailDatabase.MailSummary> summaries(Player player, boolean archive) {
        if (player == null || player.getDbID() <= 0) return List.of();
        try {
            return archive ? database.listArchive(player.getDbID(), settings.mailboxLimit)
                    : database.listOutbox(player.getDbID(), settings.mailboxLimit);
        } catch (SQLException ex) {
            return List.of();
        }
    }

    /** Trusted plugin-originated mail has no player inventory boundary. */
    public MailSendResult sendPluginMail(String senderPlugin, int recipientDbId, String recipientName, String subject,
            String body, String callerCorrelationId) {
        if (senderPlugin == null || senderPlugin.isBlank() || recipientDbId <= 0) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "plugin sender and recipient database id are required");
        }
        if (!settings.isTrustedPluginSender(senderPlugin)) {
            return MailSendResult.failed(MailResultCode.PLUGIN_NOT_TRUSTED, "plugin sender is not trusted");
        }
        MailSendResult validation = validate(recipientDbId, subject, body, List.of(), 0L);
        if (validation != null) return validation;
        try {
            String correlationId = callerCorrelationId == null || callerCorrelationId.isBlank() ? ""
                    : "plugin:" + senderPlugin.trim().toLowerCase(java.util.Locale.ROOT) + ':' + callerCorrelationId.trim();
            if (!correlationId.isBlank()) {
                java.util.Optional<MailDatabase.OperationOutcome> existing = database.operationOutcome(correlationId);
                if (existing.isPresent()) {
                    MailDatabase.OperationOutcome outcome = existing.get();
                    if (MailOperationState.COMPLETED.name().equals(outcome.operationState())) {
                        return MailSendResult.completed(outcome.mailId(), correlationId);
                    }
                    return MailSendResult.quarantined(outcome.mailId(), correlationId,
                            "existing plugin mail operation is not complete");
                }
            }
            if (database.activeMailboxCount(recipientDbId) >= settings.mailboxLimit) {
                return MailSendResult.failed(MailResultCode.MAILBOX_FULL, "recipient mailbox limit reached");
            }
            MailDatabase.PreparedMail prepared = database.prepareOutgoingMail(0, senderPlugin, recipientDbId,
                    recipientName, subject, body, List.of(), 0L, "", senderPlugin, correlationId);
            if (!database.completeSend(prepared, 0)) {
                database.quarantineSend(prepared, 0, "Plugin mail state changed while completing send");
                return MailSendResult.quarantined(prepared.mailId(), prepared.correlationId(),
                        "Plugin mail state changed while completing send");
            }
            return MailSendResult.completed(prepared.mailId(), prepared.correlationId());
        } catch (SQLException | RuntimeException ex) {
            return MailSendResult.failed(MailResultCode.OPERATION_FAILED, ex.getMessage());
        }
    }

    private MailSendResult validate(int recipientDbId, String subject, String body, List<MailAttachment> attachments,
            long codAmount) {
        if (recipientDbId <= 0 || subject == null || body == null || subject.isBlank()
                || subject.indexOf('<') >= 0 || subject.indexOf('>') >= 0
                || subject.length() > settings.maxSubjectLength || body.length() > settings.maxBodyLength) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "message validation failed");
        }
        if (attachments != null && attachments.size() > settings.maxPlayerAttachments) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "attachment count limit reached");
        }
        if (codAmount < 0L || (codAmount > 0L && (!settings.enableCod || attachments == null || attachments.isEmpty()))) {
            return MailSendResult.failed(MailResultCode.INVALID_REQUEST, "cash on delivery is unavailable or has no attachments");
        }
        return null;
    }

    public record MailSendResult(MailResultCode code, boolean success, boolean reconciliationRequired, String mailId, String correlationId,
            String detail) {
        public static MailSendResult completed(String mailId, String correlationId) {
            return new MailSendResult(MailResultCode.SUCCESS, true, false, mailId, correlationId, "");
        }

        public static MailSendResult quarantined(String mailId, String correlationId, String detail) {
            return new MailSendResult(MailResultCode.RECONCILIATION_REQUIRED, false, true, mailId, correlationId, detail);
        }

        public static MailSendResult failed(MailResultCode code, String detail) {
            return new MailSendResult(code, false, false, "", "", detail);
        }
    }
}
