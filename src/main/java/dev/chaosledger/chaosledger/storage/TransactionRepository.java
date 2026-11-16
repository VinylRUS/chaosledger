package dev.chaosledger.chaosledger.storage;

import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.economy.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only ledger of all monetary movements.
 *
 * <p>Rows are never updated or deleted. Every {@link #insert} runs on the same
 * connection as the matching {@code UPDATE accounts} so that a balance change
 * and its ledger row commit atomically.</p>
 */
public final class TransactionRepository {

    private final Connection connection;

    public TransactionRepository(Connection connection) {
        this.connection = connection;
    }

    public long insert(Transaction tx) {
        String sql = """
                INSERT INTO transactions(from_uuid, to_uuid, currency, amount, reason, category, reference, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tx.fromUuid().map(UUID::toString).orElse(null));
            ps.setString(2, tx.toUuid().map(UUID::toString).orElse(null));
            ps.setString(3, tx.amount().currency());
            ps.setDouble(4, tx.amount().amount());
            ps.setString(5, tx.reason());
            ps.setString(6, tx.category());
            ps.setString(7, tx.reference().orElse(null));
            ps.setLong(8, tx.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    connection.commit();
                    return id;
                }
            }
            connection.commit();
            throw new StorageException("No generated key returned for transaction insert", null);
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to insert transaction", e);
        }
    }

    public List<Transaction> forPlayer(UUID playerUuid, int limit) {
        String sql = """
                SELECT id, from_uuid, to_uuid, currency, amount, reason, category, reference, created_at
                FROM transactions
                WHERE from_uuid = ? OR to_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<Transaction> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerUuid.toString());
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            // SELECT-only: no commit().
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to query transactions for " + playerUuid, e);
        }
    }

    public List<Transaction> recent(String currency, int limit) {
        String sql = """
                SELECT id, from_uuid, to_uuid, currency, amount, reason, category, reference, created_at
                FROM transactions
                WHERE currency = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<Transaction> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            // SELECT-only: no commit().
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to query recent transactions for " + currency, e);
        }
    }

    private Transaction map(ResultSet rs) throws SQLException {
        String from = rs.getString("from_uuid");
        String to = rs.getString("to_uuid");
        String ref = rs.getString("reference");
        String currency = rs.getString("currency");
        return new Transaction(
                rs.getLong("id"),
                from == null ? null : UUID.fromString(from),
                to == null ? null : UUID.fromString(to),
                new Money(rs.getDouble("amount"), currency),
                rs.getString("reason"),
                rs.getString("category"),
                ref,
                rs.getLong("created_at"));
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
