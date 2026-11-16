package dev.chaosledger.chaosledger.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DDL for ChaosLedger.
 *
 * <p>Tables are created with {@code IF NOT EXISTS} so applying the schema repeatedly
 * (server restarts) is idempotent. Migrations are intentionally minimal for the
 * first version of the mod; future schema changes should use a {@code schema_version}
 * table.</p>
 */
public final class Schema {

    public void apply(Connection c) {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER NOT NULL
                    );
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid   TEXT    NOT NULL,
                        owner_name   TEXT    NOT NULL,
                        currency     TEXT    NOT NULL,
                        balance      REAL    NOT NULL DEFAULT 0,
                        created_at   INTEGER NOT NULL,
                        updated_at   INTEGER NOT NULL,
                        UNIQUE (owner_uuid, currency)
                    );
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS currencies (
                        code          TEXT PRIMARY KEY,
                        display_name  TEXT NOT NULL,
                        symbol        TEXT NOT NULL,
                        default_starting REAL NOT NULL DEFAULT 0,
                        created_at    INTEGER NOT NULL
                    );
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        from_uuid    TEXT,
                        to_uuid      TEXT,
                        currency     TEXT NOT NULL,
                        amount       REAL    NOT NULL,
                        reason       TEXT    NOT NULL,
                        category     TEXT    NOT NULL,
                        reference    TEXT,
                        created_at   INTEGER NOT NULL,
                        FOREIGN KEY (currency) REFERENCES currencies(code)
                    );
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_transactions_from ON transactions(from_uuid, created_at);
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_transactions_to   ON transactions(to_uuid,   created_at);
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_items (
                        item_id      TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        base_price   REAL NOT NULL,
                        stock        REAL NOT NULL DEFAULT 0,
                        demand       REAL NOT NULL DEFAULT 0,
                        supply       REAL NOT NULL DEFAULT 0,
                        last_price   REAL NOT NULL,
                        updated_at   INTEGER NOT NULL
                    );
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_history (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_id      TEXT NOT NULL,
                        price        REAL NOT NULL,
                        stock        REAL NOT NULL,
                        recorded_at  INTEGER NOT NULL,
                        FOREIGN KEY (item_id) REFERENCES market_items(item_id)
                    );
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_market_history ON market_history(item_id, recorded_at);
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quests (
                        id           TEXT PRIMARY KEY,
                        title        TEXT NOT NULL,
                        description  TEXT NOT NULL,
                        state_json   TEXT NOT NULL,
                        reward_json  TEXT NOT NULL,
                        repeatable   INTEGER NOT NULL DEFAULT 0,
                        cooldown_sec INTEGER NOT NULL DEFAULT 0,
                        created_at   INTEGER NOT NULL,
                        updated_at   INTEGER NOT NULL
                    );
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quest_progress (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid   TEXT NOT NULL,
                        quest_id      TEXT NOT NULL,
                        state         TEXT NOT NULL,
                        progress_json TEXT NOT NULL,
                        started_at    INTEGER NOT NULL,
                        completed_at  INTEGER,
                        claimed_at    INTEGER,
                        UNIQUE (player_uuid, quest_id),
                        FOREIGN KEY (quest_id) REFERENCES quests(id)
                    );
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_quest_progress_player ON quest_progress(player_uuid, state);
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rewards_granted (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid  TEXT NOT NULL,
                        quest_id     TEXT,
                        reward_json  TEXT NOT NULL,
                        granted_at   INTEGER NOT NULL
                    );
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_rewards_granted_player ON rewards_granted(player_uuid, granted_at);
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        actor_uuid   TEXT,
                        actor_name   TEXT,
                        action       TEXT NOT NULL,
                        target       TEXT,
                        payload_json TEXT,
                        created_at   INTEGER NOT NULL
                    );
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at);
                    """);
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_audit_log_actor   ON audit_log(actor_uuid, created_at);
                    """);

            // Seed the schema version row.
            st.executeUpdate("""
                    INSERT INTO schema_version(version)
                    SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM schema_version);
                    """);

            c.commit();
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignore) {}
            throw new IllegalStateException("Failed to apply ChaosLedger schema.", e);
        }
    }
}
