package dev.chaosledger.chaosledger.storage;

import dev.chaosledger.chaosledger.economy.Currency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD for {@link Currency} rows. Rows are written rarely (admin-driven) and
 * read on every economy operation, so they are cached after the first load.
 */
public final class CurrencyRepository {

    private final Connection connection;
    private volatile List<Currency> cache;

    public CurrencyRepository(Connection connection) {
        this.connection = connection;
    }

    public void save(Currency currency) {
        String sql = """
                INSERT INTO currencies(code, display_name, symbol, default_starting, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(code) DO UPDATE SET
                    display_name    = excluded.display_name,
                    symbol          = excluded.symbol,
                    default_starting = excluded.default_starting;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, currency.code());
            ps.setString(2, currency.displayName());
            ps.setString(3, currency.symbol());
            ps.setDouble(4, currency.defaultStarting());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            connection.commit();
            cache = null;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to save currency " + currency.code(), e);
        }
    }

    public Optional<Currency> findByCode(String code) {
        return all().stream().filter(c -> c.code().equals(code)).findFirst();
    }

    public List<Currency> all() {
        List<Currency> snapshot = cache;
        if (snapshot != null) {
            return snapshot;
        }
        List<Currency> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT code, display_name, symbol, default_starting FROM currencies");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Currency(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        rs.getString("symbol"),
                        rs.getDouble("default_starting")));
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list currencies", e);
        }
        cache = List.copyOf(out);
        return cache;
    }

    public void delete(String code) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM currencies WHERE code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
            connection.commit();
            cache = null;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to delete currency " + code, e);
        }
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
