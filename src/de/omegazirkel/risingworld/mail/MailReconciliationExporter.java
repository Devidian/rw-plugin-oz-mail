package de.omegazirkel.risingworld.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Writes a privacy-minimized operator snapshot of the reconciliation queue. */
public final class MailReconciliationExporter {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private MailReconciliationExporter() {
    }

    public static Path export(Path pluginDirectory, List<MailDatabase.ReconciliationEntry> entries) throws IOException {
        if (pluginDirectory == null) throw new IOException("plugin directory unavailable");
        Path exportDirectory = pluginDirectory.resolve("exports");
        Files.createDirectories(exportDirectory);
        Path output = exportDirectory.resolve("reconciliation-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".csv");
        Path temporary = Files.createTempFile(exportDirectory, ".reconciliation-", ".tmp");
        try {
            StringBuilder csv = new StringBuilder("correlation_id,mail_id,operation_type,mail_state,created_at,updated_at\n");
            for (MailDatabase.ReconciliationEntry entry : entries == null ? List.<MailDatabase.ReconciliationEntry>of() : entries) {
                csv.append(escape(entry.correlationId())).append(',').append(escape(entry.mailId())).append(',')
                        .append(escape(entry.operationType())).append(',').append(escape(entry.mailState())).append(',')
                        .append(entry.createdAt()).append(',').append(entry.updatedAt()).append('\n');
            }
            Files.writeString(temporary, csv.toString(), StandardCharsets.UTF_8);
            try {
                return Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                return Files.move(temporary, output);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String escape(String value) {
        String normalized = value == null ? "" : value.replace("\"", "\"\"");
        return '"' + normalized + '"';
    }
}
