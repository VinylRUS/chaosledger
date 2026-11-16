package dev.chaosledger.chaosledger.storage;

import dev.chaosledger.chaosledger.audit.AuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only {@link AuditEntry} log. Entries are written by every engine and
 * read by the web panel for the audit feed.
 */
public final class AuditRepository {

    private final Connection connection;

    public AuditRepository(Connection connection) {
        this.connection = connection;
    }

    public long record(AuditEntry entry) {
        String sql = "INSERT INTO audit_log(actor_uuid, actor_name, action, target, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entry.actorUuid() == null ? null : entry.actorUuid().get().toString());
            ps.setString(2, entry.actorName().orElse(null));
            ps.setString(3, entry.action());
            ps.setString(4, entry.target().orElse(null));
            ps.setString(5, entry.payloadJson().orElse(null));
            ps.setLong(6, entry.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    connection.commit();
                    return id;
                }
            }
            connection.commit();
            throw new StorageException("No generated key for audit entry", null);
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to record audit entry", e);
        }
    }

    public List<AuditEntry> recent(int limit) {
        String sql = "SELECT id, actor_uuid, actor_name, action, target, payload_json, created_at FROM audit_log ORDER BY created_at DESC LIMIT ?";
        List<AuditEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            connection.commit();
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list audit entries", e);
        }
    }

    public List<AuditEntry> forActor(UUID actorUuid, int limit) {
        String sql = "SELECT id, actor_uuid, actor_name, action, target, payload_json, created_at FROM audit_log WHERE actor_uuid = ? ORDER BY created_at DESC LIMIT ?";
        List<AuditEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, actorUuid.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            connection.commit();
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list audit entries for " + actorUuid, e);
        }
    }

    private AuditEntry map(ResultSet rs) throws SQLException {
        String actor = rs.getString("actor_uuid");
        return new AuditEntry(
                rs.getLong("id"),
                actor == null ? null : UUID.fromString(actor),
                rs.getString("actor_name"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getString("payload_json"),
                rs.getLong("created_at"));
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
