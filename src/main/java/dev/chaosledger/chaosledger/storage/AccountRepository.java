package dev.chaosledger.chaosledger.storage;

import dev.chaosledger.chaosledger.economy.Account;
import dev.chaosledger.chaosledger.economy.Money;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for player {@link Account} rows.
 *
 * <p>The ledger uses a single-table accounts model: one row per (player, currency).
 * All mutations go through {@link #updateBalance}; direct balance writes are
 * forbidden so that the {@link TransactionRepository} stays the single source of
 * truth for why a balance changed.</p>
 */
public final class AccountRepository {

    private final Connection connection;

    public AccountRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Returns the account for the given owner/currency, creating it on first
     * access with {@code startingBalance} as the opening amount.
     */
    public Account getOrCreate(UUID ownerUuid, String ownerName, String currency, Money startingBalance) {
        Account existing = findByOwner(ownerUuid, currency).orElse(null);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO accounts(owner_uuid, owner_name, currency, balance, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_uuid, currency) DO UPDATE SET
                    owner_name = excluded.owner_name
                RETURNING id;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, ownerName);
            ps.setString(3, currency);
            ps.setDouble(4, startingBalance.amount());
            ps.setLong(5, now);
            ps.setLong(6, now);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    connection.commit();
                    return new Account(id, ownerUuid, ownerName, currency, startingBalance, now, now);
                }
            }
            connection.commit();
            return findByOwner(ownerUuid, currency).orElseThrow();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to create account for " + ownerUuid + " / " + currency, e);
        }
    }

    public Optional<Account> findByOwner(UUID ownerUuid, String currency) {
        String sql = "SELECT id, owner_uuid, owner_name, currency, balance, created_at, updated_at FROM accounts WHERE owner_uuid = ? AND currency = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, currency);
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
            throw new StorageException("Failed to find account for " + ownerUuid + " / " + currency, e);
        }
    }

    public List<Account> findByOwner(UUID ownerUuid) {
        String sql = "SELECT id, owner_uuid, owner_name, currency, balance, created_at, updated_at FROM accounts WHERE owner_uuid = ? ORDER BY currency";
        List<Account> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            connection.commit();
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list accounts for " + ownerUuid, e);
        }
    }

    public List<Account> top(String currency, int limit) {
        String sql = "SELECT id, owner_uuid, owner_name, currency, balance, created_at, updated_at FROM accounts WHERE currency = ? ORDER BY balance DESC LIMIT ?";
        List<Account> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, currency);
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
            throw new StorageException("Failed to query top accounts for " + currency, e);
        }
    }

    public List<Account> all() {
        String sql = "SELECT id, owner_uuid, owner_name, currency, balance, created_at, updated_at FROM accounts ORDER BY currency, owner_name";
        List<Account> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
            connection.commit();
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list all accounts", e);
        }
    }

    /** Package-private: only the ledger service should call this. */
    void updateBalance(Account account) {
        String sql = "UPDATE accounts SET balance = ?, owner_name = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, account.balance().amount());
            ps.setString(2, account.ownerName());
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, account.id());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to update balance for account " + account.id(), e);
        }
    }

    private Account map(ResultSet rs) throws SQLException {
        return new Account(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("currency"),
                new Money(rs.getDouble("balance"), rs.getString("currency")),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
