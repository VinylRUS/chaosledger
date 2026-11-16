package dev.chaosledger.chaosledger.storage;

import dev.chaosledger.chaosledger.economy.MarketItem;
import dev.chaosledger.chaosledger.economy.Money;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link MarketItem} rows and the rolling price history.
 *
 * <p>Every {@link #save} also writes a {@code market_history} row so that the
 * web panel can plot a price curve without running extra bookkeeping.</p>
 */
public final class MarketRepository {

    private final Connection connection;

    public MarketRepository(Connection connection) {
        this.connection = connection;
    }

    public void save(MarketItem item) {
        String sql = """
                INSERT INTO market_items(item_id, display_name, base_price, stock, demand, supply, last_price, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(item_id) DO UPDATE SET
                    display_name = excluded.display_name,
                    base_price    = excluded.base_price,
                    stock         = excluded.stock,
                    demand        = excluded.demand,
                    supply        = excluded.supply,
                    last_price    = excluded.last_price,
                    updated_at    = excluded.updated_at;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.itemId());
            ps.setString(2, item.displayName());
            ps.setDouble(3, item.basePrice().amount());
            ps.setDouble(4, item.stock());
            ps.setDouble(5, item.demand());
            ps.setDouble(6, item.supply());
            ps.setDouble(7, item.lastPrice().amount());
            ps.setLong(8, item.updatedAt());
            ps.executeUpdate();

            try (PreparedStatement hist = connection.prepareStatement(
                    "INSERT INTO market_history(item_id, price, stock, recorded_at) VALUES (?, ?, ?, ?)")) {
                hist.setString(1, item.itemId());
                hist.setDouble(2, item.lastPrice().amount());
                hist.setDouble(3, item.stock());
                hist.setLong(4, item.updatedAt());
                hist.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to save market item " + item.itemId(), e);
        }
    }

    public Optional<MarketItem> findById(String itemId) {
        String sql = "SELECT item_id, display_name, base_price, stock, demand, supply, last_price, updated_at FROM market_items WHERE item_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // SELECT-only: no commit().
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to find market item " + itemId, e);
        }
    }

    public List<MarketItem> all(String currency) {
        List<MarketItem> out = new ArrayList<>();
        String sql = "SELECT item_id, display_name, base_price, stock, demand, supply, last_price, updated_at FROM market_items WHERE substr(item_id, 1, 0) = '' OR 1=1 ORDER BY item_id";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MarketItem item = map(rs);
                if (item.lastPrice().currency().equals(currency)) {
                    out.add(item);
                }
            }
            // SELECT-only: no commit().
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to list market items", e);
        }
    }

    public List<double[]> history(String itemId, int limit) {
        String sql = "SELECT price, recorded_at FROM market_history WHERE item_id = ? ORDER BY recorded_at DESC LIMIT ?";
        List<double[]> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new double[]{rs.getDouble("price"), rs.getLong("recorded_at")});
                }
            }
            // SELECT-only: no commit().
            return out;
        } catch (SQLException e) {
            rollback();
            throw new StorageException("Failed to query history for " + itemId, e);
        }
    }

    private MarketItem map(ResultSet rs) throws SQLException {
        String currency = inferCurrency(rs.getString("item_id"));
        Money base = new Money(rs.getDouble("base_price"), currency);
        Money last = new Money(rs.getDouble("last_price"), currency);
        return new MarketItem(
                rs.getString("item_id"),
                rs.getString("display_name"),
                base,
                rs.getDouble("stock"),
                rs.getDouble("demand"),
                rs.getDouble("supply"),
                last,
                rs.getLong("updated_at"));
    }

    /**
     * Default currency for a market row. We keep this lightweight for the first
     * version: every market item is priced in the ledger's default currency
     * (configurable, see ChaosLedgerConfig). The id is not stored alongside the
     * row to keep the schema stable across currency renames.
     */
    private String inferCurrency(String itemId) {
        return dev.chaosledger.chaosledger.ChaosLedger.config().defaultCurrency();
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignore) {}
    }
}
