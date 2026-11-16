package dev.chaosledger.chaosledger.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.chaosledger.chaosledger.quest.QuestProgress;
import dev.chaosledger.chaosledger.quest.QuestState;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link QuestProgress}.
 *
 * <p>Progress is stored as a row per (player, quest). The mutable {@code progress}
 * list is serialised as JSON; {@code state} is a string column to keep schema
 * stability across enum renames.</p>
 */
public final class QuestProgressRepository {

    private final Connection connection;
    private final Gson gson = new Gson();
    private static final Type DOUBLE_LIST = new TypeToken<List<Double>>() {}.getType();

    public QuestProgressRepository(Connection connection) {
        this.connection = connection;
    }

    public QuestProgress getOrCreate(UUID playerUuid, String questId) {
        Optional<QuestProgress> existing = find(playerUuid, questId);
        if (existing.isPresent()) {
            return existing.get();
        }
        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO quest_progress(player_uuid, quest_id, state, progress_json, started_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, quest_id) DO NOTHING;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, QuestState.LOCKED.name());
            ps.setString(4, "[]");
            ps.setLong(5, now);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to create quest progress for " + playerUuid + " / " + questId, e);
        }
        return find(playerUuid, questId).orElseThrow();
    }

    public Optional<QuestProgress> find(UUID playerUuid, String questId) {
        String sql = "SELECT id, player_uuid, quest_id, state, progress_json, started_at, completed_at, claimed_at FROM quest_progress WHERE player_uuid = ? AND quest_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // SELECT-only: no commit().
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to find quest progress", e);
        }
    }

    public List<QuestProgress> forPlayer(UUID playerUuid) {
        String sql = "SELECT id, player_uuid, quest_id, state, progress_json, started_at, completed_at, claimed_at FROM quest_progress WHERE player_uuid = ? ORDER BY started_at";
        List<QuestProgress> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            // SELECT-only: no commit().
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list quest progress for " + playerUuid, e);
        }
    }

    public void save(QuestProgress progress) {
        String sql = """
                UPDATE quest_progress
                SET state        = ?,
                    progress_json = ?,
                    completed_at  = ?,
                    claimed_at    = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, progress.state().name());
            ps.setString(2, gson.toJson(progress.progress(), DOUBLE_LIST));
            if (progress.completedAt() == null) ps.setNull(3, java.sql.Types.INTEGER);
            else ps.setLong(3, progress.completedAt());
            if (progress.claimedAt() == null) ps.setNull(4, java.sql.Types.INTEGER);
            else ps.setLong(4, progress.claimedAt());
            ps.setLong(5, progress.id());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to save quest progress " + progress.id(), e);
        }
    }

    private QuestProgress map(ResultSet rs) throws SQLException {
        List<Double> progress = gson.fromJson(rs.getString("progress_json"), DOUBLE_LIST);
        if (progress == null) progress = new ArrayList<>();
        Long completedAt = rs.getObject("completed_at") == null ? null : rs.getLong("completed_at");
        Long claimedAt   = rs.getObject("claimed_at")   == null ? null : rs.getLong("claimed_at");
        return new QuestProgress(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("quest_id"),
                QuestState.valueOf(rs.getString("state")),
                progress,
                rs.getLong("started_at"),
                completedAt,
                claimedAt);
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
