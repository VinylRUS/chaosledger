package dev.chaosledger.chaosledger.storage;

import com.google.gson.Gson;
import dev.chaosledger.chaosledger.reward.Reward;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only log of every {@link Reward} granted by the {@code RewardEngine}.
 *
 * <p>Each row records who got what and why ({@code quest_id} nullable because
 * the same engine is reused for admin grants and event rewards). The reward
 * itself is stored as JSON so future {@link Reward.Type} additions do not need a
 * schema migration.</p>
 */
public final class RewardRepository {

    private final Connection connection;
    private final Gson gson = new Gson();

    public RewardRepository(Connection connection) {
        this.connection = connection;
    }

    public long recordGrant(UUID playerUuid, String questId, Reward reward) {
        String sql = "INSERT INTO rewards_granted(player_uuid, quest_id, reward_json, granted_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, questId);
            ps.setString(3, gson.toJson(reward));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    connection.commit();
                    return id;
                }
            }
            connection.commit();
            throw new StorageException("No generated key for reward grant", null);
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to record reward grant", e);
        }
    }

    public List<Reward> forPlayer(UUID playerUuid, int limit) {
        String sql = "SELECT reward_json FROM rewards_granted WHERE player_uuid = ? ORDER BY granted_at DESC LIMIT ?";
        List<Reward> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(gson.fromJson(rs.getString("reward_json"), Reward.class));
                }
            }
            // SELECT-only: no commit() — read queries must not advance the write
            // transaction. See task #3 in the Qwen review.
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list reward grants for " + playerUuid, e);
        }
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
