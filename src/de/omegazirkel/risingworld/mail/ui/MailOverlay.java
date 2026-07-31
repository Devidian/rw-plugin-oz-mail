package de.omegazirkel.risingworld.mail.ui;

import de.omegazirkel.risingworld.OZMail;
import de.omegazirkel.risingworld.mail.MailDatabase;
import de.omegazirkel.risingworld.mail.MailMessageState;
import de.omegazirkel.risingworld.mail.MailService;
import de.omegazirkel.risingworld.mail.MailAttachment;
import de.omegazirkel.risingworld.mail.MailInventoryTransfer;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.BasePluginOverlayWithTabs;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.Dropdown;
import de.omegazirkel.risingworld.tools.ui.DropdownOption;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.Unit;
import net.risingworld.api.ui.UIScrollView.ScrollViewMode;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Localized mail shell; mailbox data rendering is added on top of this
 * contract.
 */
public final class MailOverlay extends BasePluginOverlayWithTabs {
    private static final DateTimeFormatter MAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private enum MailTab {
        INBOX, OUTBOX, ARCHIVE, COMPOSE, PLAYERS, ADMIN
    }

    private final OZMail plugin;
    private MailTab active = MailTab.INBOX;
    private UITextField recipientField;
    private UITextField subjectField;
    private UITextField bodyField;
    private UITextField codAmountField;
    private Dropdown codCurrencyDropdown;
    private UITextField attachmentAmountField;
    private UILabel statusLabel;
    private UILabel selectedCandidateLabel;
    private UILabel attachmentsLabel;
    private AdvancedButton clearAttachmentsButton;
    private AdvancedButton confirmAttachmentsButton;
    private MailInventoryTransfer.AttachmentCandidate selectedCandidate;
    private final Map<String, MailAttachment> selectedAttachments = new LinkedHashMap<>();
    private boolean attachmentsConfirmed;
    private String composeRecipient = "";
    private String composeSubject = "";
    private String composeBody = "";
    private String selectedCodCurrency = "";
    private String selectedMailId;
    private String selectedAdminMailId;
    private MailDatabase.ReconciliationEntry selectedAdminEntry;
    private String adminOperationFilter = "";
    private UILabel adminStatusLabel;

    public MailOverlay(OZMail plugin, Player player) {
        super(player, ignored -> {
        });
        this.plugin = plugin;
        titleLabelKey = "MAIL_UI_TITLE";
        descLabelKey = "MAIL_UI_SUBTITLE";
        legendLabelKey = "MAIL_UI_LEGEND";
        rebuild();
    }

    @Override
    protected I18n t() {
        return plugin.i18n();
    }

    @Override
    protected void setupTabs() {
        setupTabContainer();
        tab(MailTab.INBOX, "MAIL_UI_TAB_INBOX", 130, false);
        tab(MailTab.OUTBOX, "MAIL_UI_TAB_OUTBOX", 130, false);
        tab(MailTab.ARCHIVE, "MAIL_UI_TAB_ARCHIVE", 130, false);
        tab(MailTab.COMPOSE, "MAIL_UI_TAB_COMPOSE", 150, false);
        tab(MailTab.PLAYERS, "MAIL_UI_TAB_RECIPIENTS", 150, false);
        if (uiPlayer.isAdmin())
            tab(MailTab.ADMIN, "MAIL_UI_TAB_ADMIN", 140, true);
        body.removeAllChilds();
        addMailboxCapacityFooter();
        if (active == MailTab.COMPOSE) {
            setupCompose();
            return;
        }
        if (active == MailTab.PLAYERS) {
            setupPlayers();
            return;
        }
        if ((active == MailTab.INBOX || active == MailTab.OUTBOX || active == MailTab.ARCHIVE)
                && selectedMailId != null) {
            setupMailDetail(active != MailTab.OUTBOX);
            return;
        }
        if (active == MailTab.ADMIN) {
            setupAdmin();
            return;
        }
        List<MailDatabase.MailSummary> messages = messages();
        if (active == MailTab.INBOX || active == MailTab.OUTBOX || active == MailTab.ARCHIVE) {
            setupMailboxTable(messages, active != MailTab.OUTBOX);
            return;
        }
        if (!messages.isEmpty()) {
            int y = 18;
            for (MailDatabase.MailSummary mail : messages) {
                AdvancedButton row = AdvancedButtonFactory.defaultButton(mail.subject() + " | " + mail.senderName()
                        + (mail.hasAttachments() ? " | " + t().get("MAIL_UI_ATTACHMENT", uiPlayer) : ""), event -> {
                            if (active == MailTab.INBOX || active == MailTab.OUTBOX) {
                                selectedMailId = mail.id();
                                rebuild();
                            }
                        });
                row.setPivot(Pivot.UpperLeft);
                row.setPosition(18, y, false);
                row.setSize(96, 28, true);
                body.addChild(row);
                y += 34;
            }
        } else {
            UILabel label = new UILabel(t().get(contentKey(), uiPlayer));
            label.setPivot(Pivot.UpperLeft);
            label.setPosition(18, 18, false);
            label.setFont(Font.Default);
            label.setFontSize(15);
            body.addChild(label);
        }
    }

    private void addMailboxCapacityFooter() {
        UILabel capacity = new UILabel(t().get("MAIL_UI_MAILBOX_CAPACITY", uiPlayer)
                .replace("PH_USED", String.valueOf(plugin.mailboxUsage(uiPlayer)))
                .replace("PH_CAPACITY", String.valueOf(plugin.mailboxCapacity(uiPlayer))));
        capacity.setPivot(Pivot.LowerRight);
        capacity.style.position.set(Position.Absolute);
        capacity.style.right.set(24, Unit.Pixel);
        capacity.style.bottom.set(15, Unit.Pixel);
        capacity.setSize(280, 18, false);
        capacity.setFont(Font.DefaultBold);
        capacity.setFontSize(12);
        capacity.setTextAlign(TextAnchor.MiddleRight);
        panel.addChild(capacity);
    }

    private void setupMailboxTable(List<MailDatabase.MailSummary> messages, boolean inbox) {
        if (messages.isEmpty()) {
            addMessage(contentKey(), 18);
            return;
        }
        TableScrollView table = new TableScrollView(Arrays.asList(
                t().get("MAIL_UI_COL_SUBJECT", uiPlayer),
                t().get(inbox ? "MAIL_UI_COL_SENDER" : "MAIL_UI_COL_RECIPIENT", uiPlayer),
                t().get("MAIL_UI_COL_DATE", uiPlayer),
                t().get("MAIL_UI_COL_ATTACHMENTS", uiPlayer),
                t().get("MAIL_UI_COL_STATE", uiPlayer),
                t().get("MAIL_UI_COL_ACTION", uiPlayer)),
                Arrays.asList(31f, 18f, 17f, 10f, 13f, 11f));
        table.setPosition(0, 0, false);
        table.style.width.set(100, Unit.Percent);
        table.setScrollBodyHeight(400);
        for (MailDatabase.MailSummary mail : messages) {
            table.addRow(mailRow(mail, inbox));
        }
        body.addChild(table.getRoot());
    }

    private TableRow mailRow(MailDatabase.MailSummary mail, boolean inbox) {
        AdvancedButton open = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_OPEN", uiPlayer), event -> {
            selectedMailId = mail.id();
            rebuild();
        });
        open.style.width.set(92, Unit.Percent);
        open.style.height.set(24, Unit.Pixel);
        TableRow row = new TableRow(Arrays.asList(
                tableCell(mail.subject(), 31f),
                tableCell(mail.senderName(), 18f),
                tableCell(MAIL_DATE_FORMAT.format(Instant.ofEpochMilli(mail.createdAt())), 17f),
                tableCell(mail.hasAttachments() ? t().get("MAIL_UI_ATTACHMENT", uiPlayer) : "-", 10f),
                tableCell(mailStateText(mail.state()), 13f),
                new TableCell(open, 11f)));
        if (inbox && MailMessageState.DELIVERED.name().equals(mail.state())) {
            row.setBackgroundColor(0x282517E8);
        }
        return row;
    }

    private TableCell tableCell(String text, float width) {
        return tableCell(text, width, false);
    }

    private TableCell tableCell(String text, float width, boolean highlighted) {
        UILabel label = new UILabel(text == null ? "" : text);
        label.setFont(highlighted ? Font.DefaultBold : Font.Default);
        label.setFontSize(13);
        if (highlighted) label.setFontColor(0xF2C766FF);
        label.setTextWrap(false);
        label.setTextAlign(TextAnchor.MiddleLeft);
        return new TableCell(label, width);
    }

    private String mailStateText(String state) {
        String key = "MAIL_UI_STATE_" + (state == null ? "UNKNOWN" : state);
        String value = t().get(key, uiPlayer);
        return key.equals(value) ? t().get("MAIL_UI_STATE_UNKNOWN", uiPlayer) : value;
    }

    private void setupAdmin() {
        if (selectedAdminMailId != null) {
            setupAdminTimeline();
            return;
        }
        UITextField referenceField = field("");
        UILabel searchLabel = new UILabel(t().get("MAIL_UI_ADMIN_SEARCH", uiPlayer));
        searchLabel.setPivot(Pivot.UpperLeft);
        searchLabel.setPosition(18, 18, false);
        searchLabel.setFont(Font.DefaultBold);
        searchLabel.setFontSize(13);
        body.addChild(searchLabel);
        referenceField.setPivot(Pivot.UpperLeft);
        referenceField.setPosition(190, 14, false);
        referenceField.setSize(200, 30, false);
        body.addChild(referenceField);
        AdvancedButton search = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_SEARCH_BUTTON", uiPlayer),
                event -> referenceField.getCurrentText(uiPlayer, this::openAdminReference));
        search.setPivot(Pivot.UpperLeft);
        search.setPosition(402, 14, false);
        search.setSize(90, 30, false);
        body.addChild(search);
        AdvancedButton export = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_EXPORT_QUEUE", uiPlayer),
                event -> plugin.exportReconciliationQueue(uiPlayer).ifPresentOrElse(
                        file -> uiPlayer.sendTextMessage(t().get("MAIL_UI_ADMIN_EXPORT_SUCCESS", uiPlayer)
                                .replace("PH_FILE", file)),
                        () -> uiPlayer.sendTextMessage(t().get("MAIL_UI_ADMIN_EXPORT_FAILED", uiPlayer))));
        export.setPivot(Pivot.UpperLeft);
        export.setPosition(504, 14, false);
        export.setSize(160, 30, false);
        body.addChild(export);
        adminStatusLabel = new UILabel("");
        adminStatusLabel.setPivot(Pivot.UpperLeft);
        adminStatusLabel.setPosition(18, 48, false);
        adminStatusLabel.setFontSize(13);
        body.addChild(adminStatusLabel);
        MailDatabase.OperationalMetrics metrics = plugin.operationalMetrics(uiPlayer);
        UILabel overview = new UILabel(t().get("MAIL_UI_ADMIN_METRICS", uiPlayer)
                .replace("PH_ACTIVE", String.valueOf(metrics.activeOperations()))
                .replace("PH_RECONCILIATION", String.valueOf(metrics.reconciliationOperations()))
                .replace("PH_HELD", String.valueOf(metrics.heldAttachments()))
                .replace("PH_QUARANTINED", String.valueOf(metrics.quarantinedAttachments()))
                .replace("PH_OLDEST", oldestOpenOperationText(metrics.oldestUnfinishedOperationAt())));
        overview.setPivot(Pivot.UpperLeft);
        overview.setPosition(18, 72, false);
        overview.setSize(90, 78, true);
        overview.setFontSize(14);
        overview.setTextWrap(true);
        body.addChild(overview);
        List<MailDatabase.ReconciliationEntry> entries = plugin.reconciliationEntries(uiPlayer);
        addAdminQueueFilter("", "MAIL_UI_ADMIN_FILTER_ALL", 18, 90);
        addAdminQueueFilter("SEND", "MAIL_OPERATION_SEND", 116, 90);
        addAdminQueueFilter("CLAIM", "MAIL_OPERATION_CLAIM", 214, 90);
        addAdminQueueFilter("RETURN", "MAIL_OPERATION_RETURN", 312, 90);
        addAdminQueueFilter("EXPIRY_RETURN", "MAIL_OPERATION_EXPIRY_RETURN", 410, 130);
        if (entries.isEmpty()) {
            addMessage("MAIL_UI_ADMIN_QUEUE_EMPTY", 208);
            return;
        }
        UIScrollView queue = new UIScrollView(ScrollViewMode.Vertical);
        queue.setPivot(Pivot.UpperLeft);
        queue.setPosition(18, 208, false);
        queue.setSize(90, 36, true);
        queue.setBackgroundColor(0x080806CC);
        queue.setBorder(1);
        queue.setBorderColor(0x5E4A25FF);
        body.addChild(queue);
        int y = 0;
        boolean anyVisible = false;
        for (MailDatabase.ReconciliationEntry entry : entries) {
            if (!isVisibleInAdminQueue(entry))
                continue;
            anyVisible = true;
            AdvancedButton label = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_QUEUE_ENTRY", uiPlayer)
                    .replace("PH_OPERATION", operationText(entry.operationType()))
                    .replace("PH_MAIL_ID", entry.mailId()), event -> {
                        selectedAdminMailId = entry.mailId();
                        selectedAdminEntry = entry;
                        rebuild();
                    });
            label.setPivot(Pivot.UpperLeft);
            label.setPosition(4, y, false);
            label.style.width.set(98, Unit.Percent);
            label.style.height.set(26, Unit.Pixel);
            queue.addChild(label);
            y += 28;
        }
        if (!anyVisible) {
            UILabel empty = new UILabel(t().get("MAIL_UI_ADMIN_FILTER_EMPTY", uiPlayer));
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(4, 6, false);
            empty.setFontSize(14);
            queue.addChild(empty);
        }
    }

    private void addAdminQueueFilter(String filter, String labelKey, int x, int width) {
        String prefix = filter.equals(adminOperationFilter) ? "• " : "";
        AdvancedButton button = AdvancedButtonFactory.defaultButton(prefix + t().get(labelKey, uiPlayer), event -> {
            adminOperationFilter = filter;
            rebuild();
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, 174, false);
        button.setSize(width, 26, false);
        body.addChild(button);
    }

    private boolean isVisibleInAdminQueue(MailDatabase.ReconciliationEntry entry) {
        return adminOperationFilter.isEmpty() || adminOperationFilter.equals(entry.operationType());
    }

    private void openAdminReference(String reference) {
        plugin.findReconciliationEntryByReference(uiPlayer, reference).ifPresentOrElse(entry -> {
            selectedAdminMailId = entry.mailId();
            selectedAdminEntry = entry;
            rebuild();
        }, () -> plugin.findMailIdByReference(uiPlayer, reference).ifPresentOrElse(mailId -> {
            selectedAdminMailId = mailId;
            selectedAdminEntry = null;
            rebuild();
        }, () -> setAdminStatus("MAIL_UI_ADMIN_SEARCH_NONE")));
    }

    private void setupAdminTimeline() {
        UILabel heading = new UILabel(t().get("MAIL_UI_ADMIN_TIMELINE", uiPlayer)
                .replace("PH_MAIL_ID", selectedAdminMailId));
        heading.setPivot(Pivot.UpperLeft);
        heading.setPosition(18, 18, false);
        heading.setFont(Font.DefaultBold);
        heading.setFontSize(16);
        body.addChild(heading);
        UIScrollView timeline = new UIScrollView(ScrollViewMode.Vertical);
        timeline.setPivot(Pivot.UpperLeft);
        timeline.setPosition(18, 56, false);
        timeline.setSize(90, 42, true);
        timeline.setBackgroundColor(0x080806CC);
        timeline.setBorder(1);
        timeline.setBorderColor(0x5E4A25FF);
        body.addChild(timeline);
        int y = 0;
        for (MailDatabase.AuditEvent event : plugin.auditEvents(uiPlayer, selectedAdminMailId)) {
            UILabel row = new UILabel(t().get("MAIL_UI_ADMIN_AUDIT_ROW", uiPlayer)
                    .replace("PH_EVENT", auditText(event.eventType()))
                    .replace("PH_ACTOR", auditActorText(event.actorType())));
            row.setPivot(Pivot.UpperLeft);
            row.setPosition(4, y, false);
            row.setFontSize(14);
            timeline.addChild(row);
            y += 26;
        }
        if (selectedAdminEntry != null && "SEND".equals(selectedAdminEntry.operationType())) {
            setupVerifiedSendResolution(265);
        }
        if (selectedAdminEntry != null && "CLAIM".equals(selectedAdminEntry.operationType())) {
            setupVerifiedClaimResolution(265);
        }
        AdvancedButton back = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_BACK", uiPlayer), event -> {
            selectedAdminMailId = null;
            selectedAdminEntry = null;
            rebuild();
        });
        back.setPivot(Pivot.UpperLeft);
        back.setPosition(18, 370, false);
        back.setSize(110, 30, false);
        body.addChild(back);
    }

    private void setupVerifiedSendResolution(int y) {
        UILabel heading = new UILabel(t().get("MAIL_UI_ADMIN_RESOLVE_SEND", uiPlayer));
        heading.setPivot(Pivot.UpperLeft);
        heading.setPosition(18, y, false);
        heading.setFont(Font.DefaultBold);
        heading.setFontSize(13);
        body.addChild(heading);
        UILabel reasonLabel = new UILabel(t().get("MAIL_UI_ADMIN_REASON", uiPlayer));
        reasonLabel.setPivot(Pivot.UpperLeft);
        reasonLabel.setPosition(18, y + 28, false);
        reasonLabel.setFontSize(13);
        body.addChild(reasonLabel);
        UITextField reasonField = field("");
        reasonField.setPivot(Pivot.UpperLeft);
        reasonField.setPosition(150, y + 24, false);
        reasonField.setSize(430, 30, false);
        body.addChild(reasonField);
        AdvancedButton held = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_RESOLVE_HELD", uiPlayer),
                event -> resolveVerifiedSend(reasonField, MailDatabase.VerifiedSendOutcome.HELD_IN_MAIL));
        held.setPivot(Pivot.UpperLeft);
        held.setPosition(18, y + 62, false);
        held.setSize(260, 30, false);
        body.addChild(held);
        AdvancedButton returned = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_RESOLVE_RETURNED", uiPlayer),
                event -> resolveVerifiedSend(reasonField, MailDatabase.VerifiedSendOutcome.RETURNED_TO_SENDER));
        returned.setPivot(Pivot.UpperLeft);
        returned.setPosition(290, y + 62, false);
        returned.setSize(290, 30, false);
        body.addChild(returned);
    }

    private void resolveVerifiedSend(UITextField reasonField, MailDatabase.VerifiedSendOutcome outcome) {
        if (selectedAdminEntry == null)
            return;
        reasonField.getCurrentText(uiPlayer, reason -> {
            if (reason == null || reason.isBlank()) {
                uiPlayer.sendTextMessage(t().get("MAIL_UI_ADMIN_REASON_REQUIRED", uiPlayer));
                return;
            }
            boolean resolved = plugin.resolveQuarantinedSend(uiPlayer, selectedAdminEntry.correlationId(), outcome,
                    reason.trim());
            uiPlayer.sendTextMessage(t().get(resolved ? "MAIL_UI_ADMIN_RESOLUTION_SUCCESS"
                    : "MAIL_UI_ADMIN_RESOLUTION_FAILED", uiPlayer));
            if (resolved) {
                selectedAdminMailId = null;
                selectedAdminEntry = null;
                rebuild();
            }
        });
    }

    private void setupVerifiedClaimResolution(int y) {
        UILabel heading = new UILabel(t().get("MAIL_UI_ADMIN_RESOLVE_CLAIM", uiPlayer));
        heading.setPivot(Pivot.UpperLeft);
        heading.setPosition(18, y, false);
        heading.setFont(Font.DefaultBold);
        heading.setFontSize(13);
        body.addChild(heading);
        UILabel reasonLabel = new UILabel(t().get("MAIL_UI_ADMIN_REASON", uiPlayer));
        reasonLabel.setPivot(Pivot.UpperLeft);
        reasonLabel.setPosition(18, y + 28, false);
        reasonLabel.setFontSize(13);
        body.addChild(reasonLabel);
        UITextField reasonField = field("");
        reasonField.setPivot(Pivot.UpperLeft);
        reasonField.setPosition(150, y + 24, false);
        reasonField.setSize(430, 30, false);
        body.addChild(reasonField);
        AdvancedButton claimed = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_RESOLVE_CLAIMED", uiPlayer),
                event -> resolveVerifiedClaim(reasonField, false));
        claimed.setPivot(Pivot.UpperLeft);
        claimed.setPosition(18, y + 62, false);
        claimed.setSize(260, 30, false);
        body.addChild(claimed);
        AdvancedButton refund = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ADMIN_RESOLVE_COD_REFUND", uiPlayer),
                event -> resolveVerifiedClaim(reasonField, true));
        refund.setPivot(Pivot.UpperLeft);
        refund.setPosition(290, y + 62, false);
        refund.setSize(290, 30, false);
        body.addChild(refund);
    }

    private void resolveVerifiedClaim(UITextField reasonField, boolean refundCod) {
        if (selectedAdminEntry == null)
            return;
        reasonField.getCurrentText(uiPlayer, reason -> {
            if (reason == null || reason.isBlank()) {
                uiPlayer.sendTextMessage(t().get("MAIL_UI_ADMIN_REASON_REQUIRED", uiPlayer));
                return;
            }
            boolean resolved = refundCod
                    ? plugin.refundQuarantinedCodClaim(uiPlayer, selectedAdminEntry.correlationId(), reason.trim())
                    : plugin.resolveQuarantinedClaimed(uiPlayer, selectedAdminEntry.correlationId(), reason.trim());
            uiPlayer.sendTextMessage(t().get(resolved ? "MAIL_UI_ADMIN_RESOLUTION_SUCCESS"
                    : "MAIL_UI_ADMIN_RESOLUTION_FAILED", uiPlayer));
            if (resolved) {
                selectedAdminMailId = null;
                selectedAdminEntry = null;
                rebuild();
            }
        });
    }

    private String auditText(String eventType) {
        String key = "MAIL_AUDIT_" + (eventType == null ? "UNKNOWN" : eventType);
        String value = t().get(key, uiPlayer);
        return key.equals(value) ? t().get("MAIL_AUDIT_UNKNOWN", uiPlayer) : value;
    }

    private String auditActorText(String actorType) {
        String key = "MAIL_AUDIT_ACTOR_" + (actorType == null ? "UNKNOWN" : actorType);
        String value = t().get(key, uiPlayer);
        return key.equals(value) ? t().get("MAIL_AUDIT_ACTOR_UNKNOWN", uiPlayer) : value;
    }

    private String operationText(String operationType) {
        String key = "MAIL_OPERATION_" + (operationType == null ? "UNKNOWN" : operationType);
        String value = t().get(key, uiPlayer);
        return key.equals(value) ? t().get("MAIL_OPERATION_UNKNOWN", uiPlayer) : value;
    }

    private String oldestOpenOperationText(long createdAt) {
        if (createdAt <= 0L)
            return t().get("MAIL_UI_ADMIN_NONE", uiPlayer);
        long minutes = Math.max(1L, (System.currentTimeMillis() - createdAt) / 60_000L);
        return t().get("MAIL_UI_ADMIN_AGE_MINUTES", uiPlayer).replace("PH_MINUTES", String.valueOf(minutes));
    }

    private void setAdminStatus(String key) {
        if (adminStatusLabel != null)
            adminStatusLabel.setText(t().get(key, uiPlayer));
    }

    private void addMessage(String key, int y) {
        UILabel label = new UILabel(t().get(key, uiPlayer));
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(18, y, false);
        label.setFontSize(15);
        body.addChild(label);
    }

    private void setupMailDetail(boolean inbox) {
        MailDatabase.MailDetail mail = (inbox ? plugin.inboxMail(uiPlayer, selectedMailId)
                : plugin.outboxMail(uiPlayer, selectedMailId)).orElse(null);
        if (mail == null) {
            selectedMailId = null;
            rebuild();
            return;
        }
        UILabel subject = new UILabel(mail.subject());
        subject.setPivot(Pivot.UpperLeft);
        subject.setPosition(18, 18, false);
        subject.setFont(Font.DefaultBold);
        subject.setFontSize(18);
        body.addChild(subject);
        UILabel sender = new UILabel(t().get(inbox ? "MAIL_UI_FROM" : "MAIL_UI_TO", uiPlayer)
                .replace("PH_SENDER", mail.counterpartyName()));
        sender.setPivot(Pivot.UpperLeft);
        sender.setPosition(18, 48, false);
        sender.setFontSize(13);
        body.addChild(sender);
        UILabel content = new UILabel(mail.body());
        content.setPivot(Pivot.UpperLeft);
        content.setPosition(18, 82, false);
        content.setSize(90, 50, true);
        content.setTextWrap(true);
        content.setFontSize(14);
        body.addChild(content);
        if (mail.hasAttachments()) {
            String attachmentText = mail.attachments().stream()
                    .map(attachment -> MailInventoryTransfer.displayName(attachment.itemName(), attachment.variant())
                            + " x" + attachment.amount())
                    .reduce((left, right) -> left + ", " + right).orElse("");
            UILabel attachments = new UILabel(t().get("MAIL_UI_DETAIL_ATTACHMENTS", uiPlayer)
                    .replace("PH_ITEMS", attachmentText));
            attachments.setPivot(Pivot.UpperLeft);
            attachments.setPosition(18, 304, false);
            attachments.setSize(90, 12, true);
            attachments.setFontSize(13);
            attachments.setTextWrap(true);
            body.addChild(attachments);
        }
        if (mail.codAmount() > 0L) {
            UILabel cod = new UILabel(t().get("MAIL_UI_DETAIL_COD", uiPlayer)
                    .replace("PH_AMOUNT", String.valueOf(mail.codAmount()))
                    .replace("PH_CURRENCY", mail.codCurrency()));
            cod.setPivot(Pivot.UpperLeft);
            cod.setPosition(18, 278, false);
            cod.setFont(Font.DefaultBold);
            cod.setFontSize(13);
            body.addChild(cod);
        }
        AdvancedButton back = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_BACK", uiPlayer), event -> {
            selectedMailId = null;
            rebuild();
        });
        back.setPivot(Pivot.UpperLeft);
        back.setPosition(18, 390, false);
        back.setSize(110, 30, false);
        body.addChild(back);
        if (inbox) {
            AdvancedButton reply = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_REPLY", uiPlayer), event -> startReply(mail));
            reply.setPivot(Pivot.UpperLeft);
            reply.setPosition(140, 390, false);
            reply.setSize(110, 30, false);
            body.addChild(reply);
        }
        if (inbox && !mail.hasAttachments() && MailMessageState.ARCHIVED.name().equals(mail.state()) == false) {
            AdvancedButton archive = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ARCHIVE", uiPlayer), event -> {
                boolean archived = plugin.archiveMail(uiPlayer, mail.id());
                uiPlayer.sendTextMessage(t().get(archived ? "MAIL_UI_ARCHIVE_SUCCESS" : "MAIL_UI_ARCHIVE_FAILED", uiPlayer));
                if (archived) {
                    selectedMailId = null;
                    rebuild();
                }
            });
            archive.setPivot(Pivot.UpperLeft);
            archive.setPosition(262, 390, false);
            archive.setSize(130, 30, false);
            body.addChild(archive);
        }
        if (inbox && mail.hasAttachments()) {
            AdvancedButton claim = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_CLAIM", uiPlayer), event -> {
                if (mail.codAmount() > 0L) showCodClaimConfirmation(mail);
                else claimAttachments(mail.id());
            });
            claim.setPivot(Pivot.UpperLeft);
            claim.setPosition(404, 390, false);
            claim.setSize(130, 30, false);
            body.addChild(claim);
            AdvancedButton returnToSender = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_RETURN", uiPlayer), event -> {
                MailService.MailSendResult result = plugin.returnMailToSender(uiPlayer, mail.id());
                uiPlayer.sendTextMessage(t().get("MAIL_RESULT_" + result.code().name(), uiPlayer));
                selectedMailId = null;
                rebuild();
            });
            returnToSender.setPivot(Pivot.UpperLeft);
            returnToSender.setPosition(546, 390, false);
            returnToSender.setSize(150, 30, false);
            body.addChild(returnToSender);
        }
        if (inbox && !mail.hasAttachments()) {
            AdvancedButton delete = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_DELETE", uiPlayer), event ->
                    showDeleteConfirmation(true, mail.id()));
            delete.setPivot(Pivot.UpperLeft);
            delete.setPosition(404, 390, false);
            delete.setSize(130, 30, false);
            body.addChild(delete);
        }
        if (!inbox && !mail.hasAttachments()) {
            AdvancedButton delete = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_DELETE", uiPlayer), event ->
                    showDeleteConfirmation(false, mail.id()));
            delete.setPivot(Pivot.UpperLeft);
            delete.setPosition(140, 390, false);
            delete.setSize(130, 30, false);
            body.addChild(delete);
        }
    }

    private void setupCompose() {
        OZUIElement form = new OZUIElement();
        form.setPivot(Pivot.UpperLeft);
        form.setPosition(2, 4, true);
        form.setSize(49, 92, true);
        body.addChild(form);
        recipientField = field(composeRecipient);
        subjectField = field(composeSubject);
        bodyField = field(composeBody);
        bodyField.setMultiLine(true);
        recipientField.setMaxCharacters(80);
        subjectField.setMaxCharacters(plugin.settings().maxSubjectLength);
        bodyField.setMaxCharacters(plugin.settings().maxBodyLength);
        addComposeField(form, t().get("MAIL_UI_FIELD_RECIPIENT", uiPlayer), recipientField, 0, 0, 90, 30);
        AdvancedButton recipients = AdvancedButtonFactory.defaultButton("☰", event -> {
            active = MailTab.PLAYERS;
            rebuild();
        });
        recipients.setPivot(Pivot.UpperLeft);
        recipients.setPosition(0, 22, false);
        recipients.style.left.set(92, Unit.Percent);
        recipients.style.width.set(8, Unit.Percent);
        recipients.style.height.set(30, Unit.Pixel);
        form.addChild(recipients);
        addComposeField(form, t().get("MAIL_UI_FIELD_SUBJECT", uiPlayer), subjectField, 0, 60, 100, 30);
        addComposeLimit(form, plugin.settings().maxSubjectLength, 0, 60, 100);
        addComposeField(form, t().get("MAIL_UI_FIELD_BODY", uiPlayer), bodyField, 0, 120, 100, 128);
        addComposeLimit(form, plugin.settings().maxBodyLength, 0, 120, 100);
        if (plugin.settings().enableCod) {
            codAmountField = field("0");
            codAmountField.setMaxCharacters(18);
            if (plugin.hasMultipleWalletCurrencies()) {
                codCurrencyDropdown = codCurrencyDropdown();
                addComposeField(form, t().get("MAIL_UI_FIELD_COD_AMOUNT", uiPlayer), codAmountField, 0, 280, 48, 30);
                addComposeField(form, t().get("MAIL_UI_FIELD_COD_CURRENCY", uiPlayer), codCurrencyDropdown, 52, 280, 48, 30);
            } else {
                codCurrencyDropdown = null;
                addComposeField(form, t().get("MAIL_UI_FIELD_COD_AMOUNT", uiPlayer), codAmountField, 0, 280, 100, 30);
            }
        } else {
            codAmountField = null;
            codCurrencyDropdown = null;
        }
        setupAttachmentPanel();
        AdvancedButton send = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_SEND", uiPlayer), event -> sendCompose());
        send.setPivot(Pivot.UpperLeft);
        send.setPosition(0, 346, false);
        send.setSize(150, 32, false);
        form.addChild(send);
        statusLabel = new UILabel("");
        statusLabel.setPivot(Pivot.UpperLeft);
        statusLabel.setPosition(0, 386, false);
        statusLabel.setFont(Font.DefaultBold);
        statusLabel.setFontSize(14);
        statusLabel.style.width.set(100, Unit.Percent);
        statusLabel.style.height.set(38, Unit.Pixel);
        statusLabel.setTextWrap(true);
        form.addChild(statusLabel);
    }

    private void addComposeLimit(OZUIElement form, int limit, float x, int y, float width) {
        UILabel label = new UILabel(t().get("MAIL_UI_LIMIT", uiPlayer).replace("PH_LIMIT", String.valueOf(limit)));
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(0, y, false);
        label.style.left.set(x, Unit.Percent);
        label.style.width.set(width, Unit.Percent);
        label.style.height.set(18, Unit.Pixel);
        label.setFont(Font.Default);
        label.setFontSize(12);
        label.setTextAlign(TextAnchor.MiddleRight);
        form.addChild(label);
    }

    private void setupPlayers() {
        List<OZMail.Recipient> recipients = plugin.recentRecipients(uiPlayer);
        UILabel heading = new UILabel(t().get("MAIL_UI_PLAYERS_INTRO", uiPlayer)
                .replace("PH_DAYS", String.valueOf(plugin.recipientWindowDays(uiPlayer))));
        heading.setPivot(Pivot.UpperLeft);
        heading.setPosition(18, 16, false);
        heading.setFont(Font.Default);
        heading.setFontSize(14);
        body.addChild(heading);
        TableScrollView table = new TableScrollView(Arrays.asList(t().get("MAIL_UI_PLAYERS_COL_NAME", uiPlayer),
                t().get("MAIL_UI_PLAYERS_COL_LAST_SEEN", uiPlayer), t().get("MAIL_UI_PLAYERS_COL_FAVORITE", uiPlayer),
                t().get("MAIL_UI_COL_ACTION", uiPlayer)), Arrays.asList(35f, 25f, 18f, 22f));
        table.setPosition(0, 48, false);
        table.style.width.set(100, Unit.Percent);
        table.setScrollBodyHeight(350);
        for (OZMail.Recipient recipient : recipients) table.addRow(playerRow(recipient));
        body.addChild(table.getRoot());
        if (recipients.isEmpty()) addMessage("MAIL_UI_PLAYERS_EMPTY", 80);
    }

    private TableRow playerRow(OZMail.Recipient recipient) {
        AdvancedButton favorite = AdvancedButtonFactory.defaultButton(t().get(recipient.favorite() ? "MAIL_UI_PLAYERS_UNFAVORITE" : "MAIL_UI_PLAYERS_FAVORITE", uiPlayer), event -> {
            plugin.toggleRecipientFavorite(uiPlayer, recipient.dbId());
            rebuild();
        });
        favorite.style.width.set(94, Unit.Percent);
        favorite.style.height.set(24, Unit.Pixel);
        AdvancedButton compose = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_PLAYERS_COMPOSE", uiPlayer), event -> {
            composeRecipient = recipient.name();
            active = MailTab.COMPOSE;
            rebuild();
        });
        compose.style.width.set(94, Unit.Percent);
        compose.style.height.set(24, Unit.Pixel);
        String displayName = recipient.admin()
                ? recipient.name() + " · " + t().get("MAIL_UI_PLAYERS_ADMIN", uiPlayer)
                : recipient.name();
        TableRow row = new TableRow(Arrays.asList(tableCell(displayName, 35f, recipient.admin()),
                tableCell(formatLastSeen(recipient.lastSeenEpochSeconds()), 25f), new TableCell(favorite, 18f),
                new TableCell(compose, 22f)));
        if (recipient.admin()) row.setBackgroundColor(0x3A2D18D8);
        return row;
    }

    private String formatLastSeen(long epochSeconds) {
        return epochSeconds <= 0 ? "-" : MAIL_DATE_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    private void sendCompose() {
        if (!selectedAttachments.isEmpty() && !attachmentsConfirmed) {
            setStatus("MAIL_UI_SEND_CONFIRM_REQUIRED");
            return;
        }
        if (codAmountField == null) {
            submitCompose(0L, "");
            return;
        }
        codAmountField.getCurrentText(uiPlayer, rawAmount -> {
            long codAmount;
            try {
                codAmount = Long.parseLong(rawAmount == null || rawAmount.isBlank() ? "0" : rawAmount.trim());
            } catch (NumberFormatException ex) {
                setStatus("MAIL_RESULT_INVALID_REQUEST");
                return;
            }
            if (codCurrencyDropdown == null) {
                submitCompose(codAmount, "");
            } else {
                submitCompose(codAmount, selectedCodCurrency);
            }
        });
    }

    private Dropdown codCurrencyDropdown() {
        String defaultCurrency = plugin.defaultWalletCurrencyIdentifier();
        List<DropdownOption> options = plugin.walletCurrencyIdentifiers().stream()
                .map(currency -> new DropdownOption(currency.equals(defaultCurrency) ? "" : currency,
                        currency + (currency.equals(defaultCurrency) ? " *" : "")))
                .toList();
        Dropdown dropdown = new Dropdown(options, selectedCodCurrency,
                key -> selectedCodCurrency = key == null ? "" : key);
        dropdown.setPivot(Pivot.UpperLeft);
        dropdown.setSize(160, 30, false);
        return dropdown;
    }

    private void submitCompose(long codAmount, String currency) {
        recipientField.getCurrentText(uiPlayer, recipientName -> subjectField.getCurrentText(uiPlayer,
                subject -> bodyField.getCurrentText(uiPlayer, text -> {
                    MailService.MailSendResult result = plugin.sendPlayerMail(uiPlayer,
                            recipientName == null ? "" : recipientName.trim(),
                            subject == null ? "" : subject.trim(), text == null ? "" : text,
                            List.copyOf(selectedAttachments.values()), codAmount, currency);
                    setStatus("MAIL_RESULT_" + result.code().name());
                    if (result.success()) {
                        if (MailPlayerPreferences.sendConfirmationEnabled(uiPlayer)) {
                            uiPlayer.showSuccessMessageBox(t().get("MAIL_UI_TITLE", uiPlayer),
                                    t().get("MAIL_RESULT_SUCCESS", uiPlayer));
                        }
                        resetComposeDraft();
                        rebuild();
                    } else {
                        uiPlayer.showErrorMessageBox(t().get("MAIL_UI_TITLE", uiPlayer),
                                t().get("MAIL_RESULT_" + result.code().name(), uiPlayer));
                    }
                })));
    }

    private void startReply(MailDatabase.MailDetail mail) {
        resetComposeDraft();
        composeRecipient = mail.counterpartyName() == null ? "" : mail.counterpartyName();
        String subject = mail.subject() == null ? "" : mail.subject().trim();
        String prefix = t().get("MAIL_UI_REPLY_SUBJECT_PREFIX", uiPlayer);
        composeSubject = subject.regionMatches(true, 0, prefix, 0, prefix.length()) ? subject : prefix + subject;
        active = MailTab.COMPOSE;
        selectedMailId = null;
        rebuild();
    }

    private void claimAttachments(String mailId) {
        MailService.MailSendResult result = plugin.claimMail(uiPlayer, mailId);
        String key = result.success() ? "MAIL_RESULT_CLAIM_SUCCESS" : "MAIL_RESULT_" + result.code().name();
        uiPlayer.sendTextMessage(t().get(key, uiPlayer));
        if (result.success()) {
            selectedMailId = null;
            rebuild();
        }
    }

    private void showCodClaimConfirmation(MailDatabase.MailDetail mail) {
        UIElement dialog = new UIElement();
        dialog.setPivot(Pivot.MiddleCenter);
        dialog.setPosition(50, 50, true);
        dialog.setSize(520, 220, false);
        dialog.setBackgroundColor(0x10100EF5);
        dialog.setBorder(1);
        dialog.setBorderColor(0xC6953FFF);
        dialog.setBorderEdgeRadius(6, false);
        UILabel title = new UILabel(t().get("MAIL_UI_COD_CONFIRM_TITLE", uiPlayer));
        title.setPivot(Pivot.UpperCenter);
        title.setPosition(50, 16, true);
        title.setFont(Font.DefaultBold);
        title.setFontSize(20);
        dialog.addChild(title);
        UILabel message = new UILabel(t().get("MAIL_UI_COD_CONFIRM_BODY", uiPlayer)
                .replace("PH_AMOUNT", String.valueOf(mail.codAmount()))
                .replace("PH_CURRENCY", mail.codCurrency()));
        message.setPivot(Pivot.UpperLeft);
        message.setPosition(24, 64, false);
        message.setSize(472, 70, false);
        message.setFontSize(15);
        message.setTextWrap(true);
        dialog.addChild(message);
        AdvancedButton cancel = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_CANCEL", uiPlayer), event -> uiPlayer.removeUIElement(dialog));
        cancel.setPivot(Pivot.UpperLeft);
        cancel.setPosition(24, 164, false);
        cancel.setSize(140, 32, false);
        dialog.addChild(cancel);
        AdvancedButton confirm = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_COD_CONFIRM", uiPlayer), event -> {
            uiPlayer.removeUIElement(dialog);
            claimAttachments(mail.id());
        });
        confirm.setPivot(Pivot.UpperRight);
        confirm.setPosition(496, 164, false);
        confirm.setSize(180, 32, false);
        dialog.addChild(confirm);
        uiPlayer.addUIElement(dialog);
    }

    private void resetComposeDraft() {
        composeRecipient = "";
        composeSubject = "";
        composeBody = "";
        selectedCodCurrency = "";
        selectedCandidate = null;
        selectedAttachments.clear();
        attachmentsConfirmed = false;
    }

    private void showDeleteConfirmation(boolean inbox, String mailId) {
        UIElement dialog = new UIElement();
        dialog.setPivot(Pivot.MiddleCenter);
        dialog.setPosition(50, 50, true);
        dialog.setSize(520, 220, false);
        dialog.setBackgroundColor(0x10100EF5);
        dialog.setBorder(1);
        dialog.setBorderColor(0xC6953FFF);
        dialog.setBorderEdgeRadius(6, false);
        UILabel title = new UILabel(t().get("MAIL_UI_DELETE_CONFIRM_TITLE", uiPlayer));
        title.setPivot(Pivot.UpperCenter);
        title.setPosition(50, 16, true);
        title.setFont(Font.DefaultBold);
        title.setFontSize(20);
        dialog.addChild(title);
        UILabel message = new UILabel(t().get("MAIL_UI_DELETE_CONFIRM_BODY", uiPlayer));
        message.setPivot(Pivot.UpperLeft);
        message.setPosition(24, 64, false);
        message.setSize(472, 70, false);
        message.setFontSize(15);
        message.setTextWrap(true);
        dialog.addChild(message);
        AdvancedButton cancel = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_CANCEL", uiPlayer), event -> uiPlayer.removeUIElement(dialog));
        cancel.setPivot(Pivot.UpperLeft);
        cancel.setPosition(24, 164, false);
        cancel.setSize(140, 32, false);
        dialog.addChild(cancel);
        AdvancedButton confirm = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_DELETE_CONFIRM", uiPlayer), event -> {
            boolean deleted = inbox ? plugin.deleteInboxMail(uiPlayer, mailId) : plugin.deleteOutboxMail(uiPlayer, mailId);
            uiPlayer.removeUIElement(dialog);
            uiPlayer.sendTextMessage(t().get(deleted ? "MAIL_UI_DELETE_SUCCESS" : "MAIL_UI_DELETE_FAILED", uiPlayer));
            if (deleted) {
                selectedMailId = null;
                rebuild();
            }
        });
        confirm.setPivot(Pivot.UpperLeft);
        confirm.setPosition(316, 164, false);
        confirm.setSize(180, 32, false);
        dialog.addChild(confirm);
        uiPlayer.addUIElement(dialog);
    }

    private void setupAttachmentPanel() {
        OZUIElement panel = new OZUIElement();
        panel.setPivot(Pivot.UpperLeft);
        panel.setPosition(54, 4, true);
        panel.setSize(43, 92, true);
        panel.setBackgroundColor(0x10100EC8);
        panel.setBorder(1);
        panel.setBorderColor(0x7A5D2AFF);
        body.addChild(panel);
        UILabel heading = new UILabel(t().get("MAIL_UI_ATTACHMENT_SELECT", uiPlayer));
        heading.setPivot(Pivot.UpperLeft);
        heading.setPosition(3, 3, true);
        heading.setFont(Font.DefaultBold);
        heading.setFontSize(16);
        panel.addChild(heading);
        UIScrollView scroll = new UIScrollView(ScrollViewMode.Vertical);
        scroll.setPivot(Pivot.UpperLeft);
        scroll.setPosition(3, 12, true);
        scroll.setSize(54, 84, true);
        scroll.setBackgroundColor(0x080806CC);
        scroll.setBorder(1);
        scroll.setBorderColor(0x5E4A25FF);
        for (MailInventoryTransfer.AttachmentCandidate candidate : MailInventoryTransfer
                .attachmentCandidates(uiPlayer)) {
            int y = scroll.getChildCount() * 30;
            AdvancedButton button = AdvancedButtonFactory.defaultButton(candidate.displayName() + " x" + candidate.availableAmount(),
                    event -> {
                        selectedCandidate = candidate;
                        selectedCandidateLabel.setText(t().get("MAIL_UI_ATTACHMENT_SELECTED", uiPlayer)
                                .replace("PH_ITEM", candidate.displayName())
                                .replace("PH_AVAILABLE", String.valueOf(candidate.availableAmount())));
                        String key = candidate.itemName().toLowerCase() + ':' + candidate.variant();
                        MailAttachment attached = selectedAttachments.get(key);
                        attachmentAmountField.setText(String.valueOf(attached == null ? 1 : attached.amount()));
                    });
            button.setPivot(Pivot.UpperLeft);
            button.setPosition(0, y, false);
            button.style.width.set(100, Unit.Percent);
            button.style.height.set(26, Unit.Pixel);
            scroll.addChild(button);
        }
        panel.addChild(scroll);

        selectedCandidateLabel = new UILabel(t().get("MAIL_UI_ATTACHMENT_NONE", uiPlayer));
        selectedCandidateLabel.setPivot(Pivot.UpperLeft);
        selectedCandidateLabel.setPosition(61, 14, true);
        selectedCandidateLabel.setSize(35, 15, true);
        selectedCandidateLabel.setFontSize(13);
        selectedCandidateLabel.setTextWrap(true);
        panel.addChild(selectedCandidateLabel);

        UILabel amountLabel = new UILabel(t().get("MAIL_UI_FIELD_ATTACHMENT_AMOUNT", uiPlayer));
        amountLabel.setPivot(Pivot.UpperLeft);
        amountLabel.setPosition(61, 31, true);
        amountLabel.setFont(Font.DefaultBold);
        amountLabel.setFontSize(13);
        panel.addChild(amountLabel);
        attachmentAmountField = field("1");
        attachmentAmountField.setPivot(Pivot.UpperLeft);
        attachmentAmountField.setPosition(61, 38, true);
        attachmentAmountField.setSize(34, 9, true);
        panel.addChild(attachmentAmountField);

        AdvancedButton addAttachment = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ATTACHMENT_ADD", uiPlayer),
                event -> addAttachment());
        addAttachment.setPivot(Pivot.UpperLeft);
        addAttachment.setPosition(61, 51, true);
        addAttachment.setSize(34, 9, true);
        panel.addChild(addAttachment);
        clearAttachmentsButton = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ATTACHMENT_CLEAR", uiPlayer), event -> {
            selectedAttachments.clear();
            attachmentsConfirmed = false;
            updateAttachmentSummary();
        });
        clearAttachmentsButton.setPivot(Pivot.UpperLeft);
        clearAttachmentsButton.setPosition(61, 62, true);
        clearAttachmentsButton.setSize(34, 9, true);
        panel.addChild(clearAttachmentsButton);

        confirmAttachmentsButton = AdvancedButtonFactory.defaultButton(t().get("MAIL_UI_ATTACHMENT_CONFIRM", uiPlayer), event -> {
            if (selectedAttachments.isEmpty()) {
                setStatus("MAIL_UI_ATTACHMENTS_EMPTY");
                return;
            }
            attachmentsConfirmed = true;
            setStatus("MAIL_UI_ATTACHMENT_CONFIRMED");
            updateAttachmentControls();
        });
        confirmAttachmentsButton.setPivot(Pivot.UpperLeft);
        confirmAttachmentsButton.setPosition(61, 73, true);
        confirmAttachmentsButton.setSize(34, 9, true);
        panel.addChild(confirmAttachmentsButton);

        attachmentsLabel = new UILabel("");
        attachmentsLabel.setPivot(Pivot.UpperLeft);
        attachmentsLabel.setPosition(61, 84, true);
        attachmentsLabel.setSize(35, 12, true);
        attachmentsLabel.setFontSize(13);
        attachmentsLabel.setTextWrap(true);
        panel.addChild(attachmentsLabel);
        updateAttachmentSummary();
    }

    private void addAttachment() {
        if (selectedCandidate == null) {
            setStatus("MAIL_RESULT_ATTACHMENT_INVALID");
            return;
        }
        attachmentAmountField.getCurrentText(uiPlayer, rawAmount -> {
            int amount;
            try {
                amount = Integer.parseInt(rawAmount == null ? "" : rawAmount.trim());
            } catch (NumberFormatException ex) {
                setStatus("MAIL_RESULT_ATTACHMENT_INVALID");
                return;
            }
            if (amount <= 0 || amount > selectedCandidate.availableAmount()) {
                setStatus("MAIL_RESULT_ATTACHMENT_INVALID");
                return;
            }
            String key = selectedCandidate.itemName().toLowerCase() + ':' + selectedCandidate.variant();
            if (!selectedAttachments.containsKey(key)
                    && selectedAttachments.size() >= plugin.settings().maxPlayerAttachments) {
                setStatus("MAIL_RESULT_ATTACHMENT_LIMIT");
                return;
            }
            selectedAttachments.put(key, selectedCandidate.snapshot(amount));
            attachmentsConfirmed = false;
            updateAttachmentSummary();
            setStatus("MAIL_UI_ATTACHMENT_ADDED");
        });
    }

    private void updateAttachmentSummary() {
        if (attachmentsLabel == null)
            return;
        if (selectedAttachments.isEmpty()) {
            attachmentsLabel.setText(t().get("MAIL_UI_ATTACHMENTS_EMPTY", uiPlayer));
            updateAttachmentControls();
            return;
        }
        String items = selectedAttachments.values().stream()
                .map(item -> MailInventoryTransfer.displayName(item.itemName(), item.variant()) + " x" + item.amount())
                .reduce((left, right) -> left + ", " + right).orElse("");
        attachmentsLabel.setText(t().get("MAIL_UI_ATTACHMENTS_SELECTED", uiPlayer).replace("PH_ITEMS", items));
        updateAttachmentControls();
    }

    private void updateAttachmentControls() {
        boolean hasAttachments = !selectedAttachments.isEmpty();
        if (clearAttachmentsButton != null) {
            clearAttachmentsButton.setClickable(hasAttachments);
            styleAttachmentButton(clearAttachmentsButton, hasAttachments ? 0x3266E6FF : 0x38383888,
                    hasAttachments ? 0x4F7EFFFF : 0x38383888);
            refreshButtonParent(clearAttachmentsButton);
        }
        if (confirmAttachmentsButton == null) return;
        confirmAttachmentsButton.setClickable(hasAttachments);
        if (!hasAttachments) {
            confirmAttachmentsButton.setText(t().get("MAIL_UI_ATTACHMENT_CONFIRM", uiPlayer));
            styleAttachmentButton(confirmAttachmentsButton, 0x38383888, 0x38383888);
        } else if (attachmentsConfirmed) {
            confirmAttachmentsButton.setText(t().get("MAIL_UI_ATTACHMENT_CONFIRMED_ACTION", uiPlayer));
            styleAttachmentButton(confirmAttachmentsButton, 0x258541FF, 0x36A85AFF);
        } else {
            confirmAttachmentsButton.setText(t().get("MAIL_UI_ATTACHMENT_CONFIRM", uiPlayer));
            styleAttachmentButton(confirmAttachmentsButton, 0xC6953FFF, 0xE0B453FF);
        }
        refreshButtonParent(confirmAttachmentsButton);
    }

    private void styleAttachmentButton(AdvancedButton button, int background, int hover) {
        button.setBackgroundColor(background);
        button.setHoverBackgroundColor(hover);
        button.setBorder(1);
        button.setHoverBorderWidth(1);
    }

    /** Rising World's hover style cache needs a reattach after enabled-state changes. */
    private void refreshButtonParent(AdvancedButton button) {
        UIElement parent = button.getParent();
        if (parent == null) return;
        parent.removeChild(button);
        parent.addChild(button);
    }

    private void addComposeField(OZUIElement form, String labelText, UIElement field, float x, int y,
            float width, int height) {
        UILabel label = new UILabel(labelText);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(0, 0, false);
        label.style.left.set(x, Unit.Percent);
        label.style.top.set(y, Unit.Pixel);
        label.setFont(Font.DefaultBold);
        label.setFontSize(13);
        form.addChild(label);
        if (field instanceof UITextField textField && textField.isMultiLine()) {
            OZUIElement background = new OZUIElement();
            background.setPivot(Pivot.UpperLeft);
            background.setPosition(0, 0, false);
            background.style.left.set(x, Unit.Percent);
            background.style.top.set(y + 22, Unit.Pixel);
            background.style.width.set(width, Unit.Percent);
            background.style.height.set(height, Unit.Pixel);
            background.setBackgroundColor(0x20201EE8);
            background.setBorder(1);
            background.setBorderColor(0.95f, 0.75f, 0.25f, 0.46f);
            background.setBorderEdgeRadius(4, false);
            form.addChild(background);
            textField.setBackgroundColor(0x00000000);
            textField.setBorderEdgeRadius(4, false);
        }
        field.setPivot(Pivot.UpperLeft);
        field.setPosition(0, 0, false);
        field.style.left.set(x, Unit.Percent);
        field.style.top.set(y + 22, Unit.Pixel);
        field.style.width.set(width, Unit.Percent);
        field.style.height.set(height, Unit.Pixel);
        form.addChild(field);
    }

    private UITextField field(String value) {
        UITextField field = new UITextField(value);
        field.setReadOnly(false);
        field.setBackgroundColor(0x20201EE8);
        field.setBorder(1);
        field.setBorderColor(0.95f, 0.75f, 0.25f, 0.46f);
        field.setBorderEdgeRadius(4, false);
        field.setFontSize(13);
        return field;
    }

    private void setStatus(String key) {
        if (statusLabel != null)
            statusLabel.setText(t().get(key, uiPlayer));
    }

    private List<MailDatabase.MailSummary> messages() {
        return switch (active) {
            case INBOX -> plugin.inbox(uiPlayer);
            case OUTBOX -> plugin.outbox(uiPlayer);
            case ARCHIVE -> plugin.archive(uiPlayer);
            default -> List.of();
        };
    }

    private void tab(MailTab tab, String label, float width, boolean admin) {
        addTab(t().get(label, uiPlayer), width, active == tab, admin, () -> {
            active = tab;
            rebuild();
        });
    }

    private String contentKey() {
        return switch (active) {
            case INBOX -> "MAIL_UI_INBOX_EMPTY";
            case OUTBOX -> "MAIL_UI_OUTBOX_EMPTY";
            case ARCHIVE -> "MAIL_UI_ARCHIVE_EMPTY";
            case COMPOSE -> "MAIL_UI_COMPOSE_INTRO";
            case PLAYERS -> "MAIL_UI_PLAYERS_EMPTY";
            case ADMIN -> "MAIL_UI_ADMIN_INTRO";
        };
    }
}
