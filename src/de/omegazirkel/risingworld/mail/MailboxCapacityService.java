package de.omegazirkel.risingworld.mail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.risingworld.api.objects.Player;

/** Player-owned purchased mailbox capacity, persisted independently of OZTools preferences. */
public final class MailboxCapacityService {
    private final Connection connection;

    public MailboxCapacityService(Connection connection) throws SQLException {
        this.connection = connection;
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_extra_mailboxes (
                        player_db_id INTEGER PRIMARY KEY,
                        extra_mailboxes INTEGER NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public int extraMailboxes(Player player) {
        return player == null ? 0 : extraMailboxes(player.getDbID());
    }

    public int extraMailboxes(int playerDbId) {
        if (playerDbId <= 0) return 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT extra_mailboxes FROM mail_extra_mailboxes WHERE player_db_id = ?")) {
            statement.setInt(1, playerDbId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0, result.getInt(1)) : 0;
            }
        } catch (SQLException ex) {
            return 0;
        }
    }

    public int addExtraMailbox(Player player) {
        if (player == null || player.getDbID() <= 0) return -1;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mail_extra_mailboxes(player_db_id, extra_mailboxes, updated_at) VALUES (?, 1, ?)
                ON CONFLICT(player_db_id) DO UPDATE SET extra_mailboxes = extra_mailboxes + 1,
                    updated_at = excluded.updated_at
                """)) {
            statement.setInt(1, player.getDbID());
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
            return extraMailboxes(player.getDbID());
        } catch (SQLException ex) {
            return -1;
        }
    }

    public int mailboxCapacity(Player player, int baseLimit) {
        return mailboxCapacity(player == null ? 0 : player.getDbID(), baseLimit);
    }

    public int mailboxCapacity(int playerDbId, int baseLimit) {
        long capacity = (long) Math.max(1, baseLimit) * (1L + extraMailboxes(playerDbId));
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }
}
