package dev.chaosledger.chaosledger.storage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.chaosledger.chaosledger.quest.Quest;
import dev.chaosledger.chaosledger.quest.QuestObjective;
import dev.chaosledger.chaosledger.reward.Reward;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link Quest} definitions.
 *
 * <p>Quests are stored as a denormalised row whose {@code state_json} column holds
 * the objective list as JSON and {@code reward_json} holds the serialised reward.
 * The web panel edits quests through the HTTP API; the API rewrites the JSON and
 * bumps {@code updated_at}.</p>
 */
public final class QuestRepository {

    private final Connection connection;
    private final Gson gson = new Gson();

    public QuestRepository(Connection connection) {
        this.connection = connection;
    }

    public void save(Quest quest) {
        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO quests(id, title, description, state_json, reward_json, repeatable, cooldown_sec, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title        = excluded.title,
                    description = excluded.description,
                    state_json   = excluded.state_json,
                    reward_json  = excluded.reward_json,
                    repeatable   = excluded.repeatable,
                    cooldown_sec = excluded.cooldown_sec,
                    updated_at   = excluded.updated_at;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, quest.id());
            ps.setString(2, quest.title());
            ps.setString(3, quest.description());
            ps.setString(4, gson.toJson(quest.objectives()));
            ps.setString(5, gson.toJson(quest.reward()));
            ps.setInt(6, quest.repeatable() ? 1 : 0);
            ps.setLong(7, quest.cooldownSec());
            ps.setLong(8, quest.createdAt() == 0 ? now : quest.createdAt());
            ps.setLong(9, now);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to save quest " + quest.id(), e);
        }
    }

    public Optional<Quest> findById(String id) {
        String sql = "SELECT id, title, description, state_json, reward_json, repeatable, cooldown_sec, created_at, updated_at FROM quests WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    connection.commit();
                    return Optional.of(map(rs));
                }
                connection.commit();
                return Optional.empty();
            }
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to find quest " + id, e);
        }
    }

    public List<Quest> all() {
        String sql = "SELECT id, title, description, state_json, reward_json, repeatable, cooldown_sec, created_at, updated_at FROM quests ORDER BY id";
        List<Quest> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
            connection.commit();
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list quests", e);
        }
    }

    public void delete(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM quests WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to delete quest " + id, e);
        }
    }

    private Quest map(ResultSet rs) throws SQLException {
        List<QuestObjective> objectives = new ArrayList<>();
        JsonElement[] objs = gson.fromJson(rs.getString("state_json"), JsonElement[].class);
        if (objs != null) {
            for (JsonElement el : objs) {
                objectives.add(gson.fromJson(el, QuestObjective.class));
            }
        }
        Reward reward = gson.fromJson(rs.getString("reward_json"), Reward.class);
        return new Quest(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                List.copyOf(objectives),
                reward,
                rs.getInt("repeatable") == 1,
                rs.getLong("cooldown_sec"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
