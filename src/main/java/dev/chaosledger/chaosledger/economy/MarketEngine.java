package dev.chaosledger.chaosledger.economy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.chaosledger.chaosledger.ChaosLedgerConfig;
import dev.chaosledger.chaosledger.audit.AuditEntry;
import dev.chaosledger.chaosledger.audit.AuditLogger;
import dev.chaosledger.chaosledger.storage.MarketRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative market service.
 *
 * <p>Buying from the market charges the player's account at the current
 * {@link MarketItem#lastPrice()} and decreases stock (raising demand); selling
 * credits the account at {@code lastPrice * sellMargin} and increases stock.
 * After every trade the {@link PriceEngine} recomputes the next price and the
 * updated row is persisted.</p>
 */
public final class MarketEngine {

    private final MarketRepository repository;
    private final EconomyEngine economy;
    private final PriceEngine priceEngine;
    private final AuditLogger audit;
    private final ChaosLedgerConfig config;
    private final Gson gson = new Gson();

    /** Player receives {@code lastPrice * SELL_MARGIN} when selling to the market. */
    public static final double SELL_MARGIN = 0.75d;

    public MarketEngine(MarketRepository repository,
                       EconomyEngine economy,
                       PriceEngine priceEngine,
                       AuditLogger audit,
                       ChaosLedgerConfig config) {
        this.repository = repository;
        this.economy = economy;
        this.priceEngine = priceEngine;
        this.audit = audit;
        this.config = config;
    }

    public void define(MarketItem item) {
        repository.save(item);
    }

    public Optional<MarketItem> item(String itemId) {
        return repository.findById(itemId);
    }

    public List<MarketItem> all() {
        return repository.all(config.defaultCurrency());
    }

    public List<double[]> history(String itemId, int limit) {
        return repository.history(itemId, limit);
    }

    /**
     * Buy {@code quantity} of the item at the current market price.
     *
     * @return the new {@link MarketItem} row after the trade
     */
    public MarketItem buy(UUID playerUuid, String playerDisplay, String itemId, double quantity) {
        MarketItem item = repository.findById(itemId)
                .orElseThrow(() -> new EconomyException("Unknown market item: " + itemId));
        if (quantity <= 0) {
            throw new EconomyException("Quantity must be > 0");
        }
        if (item.stock() < quantity) {
            throw new EconomyException("Not enough stock for " + itemId + ": have " + item.stock() + ", want " + quantity);
        }
        Money cost = new Money(item.lastPrice().amount() * quantity, item.lastPrice().currency());
        economy.charge(playerUuid, playerDisplay, cost.currency(), cost, "Buy " + quantity + "x " + itemId, "MARKET_BUY");

        MarketItem after = priceEngine.onBuy(item, quantity);
        Money nextPrice = priceEngine.recompute(after, config);
        MarketItem updated = new MarketItem(
                after.itemId(), after.displayName(), after.basePrice(),
                after.stock(), after.demand(), after.supply(), nextPrice, after.updatedAt());
        repository.save(updated);

        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("quantity", quantity);
        payload.addProperty("cost", cost.amount());
        payload.addProperty("nextPrice", nextPrice.amount());
        audit.log(new AuditEntry(0, playerUuid, playerDisplay, "MARKET_BUY",
                itemId, gson.toJson(payload), System.currentTimeMillis()));
        return updated;
    }

    /**
     * Sell {@code quantity} of the item to the market at {@code lastPrice * SELL_MARGIN}.
     */
    public MarketItem sell(UUID playerUuid, String playerDisplay, String itemId, double quantity) {
        MarketItem item = repository.findById(itemId)
                .orElseThrow(() -> new EconomyException("Unknown market item: " + itemId));
        if (quantity <= 0) {
            throw new EconomyException("Quantity must be > 0");
        }
        Money proceeds = new Money(item.lastPrice().amount() * quantity * SELL_MARGIN, item.lastPrice().currency());
        economy.grant(playerUuid, playerDisplay, proceeds.currency(), proceeds, "Sell " + quantity + "x " + itemId);

        MarketItem after = priceEngine.onSell(item, quantity);
        Money nextPrice = priceEngine.recompute(after, config);
        MarketItem updated = new MarketItem(
                after.itemId(), after.displayName(), after.basePrice(),
                after.stock(), after.demand(), after.supply(), nextPrice, after.updatedAt());
        repository.save(updated);

        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("quantity", quantity);
        payload.addProperty("proceeds", proceeds.amount());
        payload.addProperty("nextPrice", nextPrice.amount());
        audit.log(new AuditEntry(0, playerUuid, playerDisplay, "MARKET_SELL",
                itemId, gson.toJson(payload), System.currentTimeMillis()));
        return updated;
    }

    /**
     * Recompute and persist the price for every market item. Called by a periodic
     * server tick so that even items nobody trades drift back toward their base
     * price as demand/supply decay over time.
     */
    public void recomputeAll() {
        for (MarketItem item : all()) {
            // Decay demand/supply by 1% per tick so signals fade.
            MarketItem decayed = new MarketItem(
                    item.itemId(), item.displayName(), item.basePrice(),
                    item.stock(),
                    item.demand() * 0.99d,
                    item.supply() * 0.99d,
                    item.lastPrice(),
                    System.currentTimeMillis());
            Money next = priceEngine.recompute(decayed, config);
            MarketItem updated = new MarketItem(
                    decayed.itemId(), decayed.displayName(), decayed.basePrice(),
                    decayed.stock(), decayed.demand(), decayed.supply(),
                    next, decayed.updatedAt());
            repository.save(updated);
        }
    }
}
