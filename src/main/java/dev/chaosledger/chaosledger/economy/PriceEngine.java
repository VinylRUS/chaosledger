package dev.chaosledger.chaosledger.economy;

import dev.chaosledger.chaosledger.ChaosLedgerConfig;

/**
 * Naive-but-deterministic dynamic price formula.
 *
 * <p>The new price for a market item is the base price multiplied by a factor
 * derived from the imbalance between supply and demand. The factor is clamped
 * to {@code [priceMinFactor, priceMaxFactor]} from the config so the price can
 * never collapse to zero or explode unbounded between ticks.</p>
 *
 * <p>This is the first version of the engine — it is intentionally simple and
 * predictable. Real games would track buy/sell volume and use an AMM-like curve,
 * but the contract is the same: {@link #recompute(MarketItem, ChaosLedgerConfig)}
 * returns the next {@link Money} value to persist.</p>
 */
public final class PriceEngine {

    /**
     * Compute the next price for the given market item.
     *
     * @param item   the live market item row
     * @param config mod configuration (elasticity and clamps)
     * @return the new {@code lastPrice} to persist
     */
    public Money recompute(MarketItem item, ChaosLedgerConfig config) {
        double demand  = Math.max(0d, item.demand());
        double supply  = Math.max(0d, item.supply());
        double base    = Math.max(0.0001d, item.basePrice().amount());

        // Net demand pushes price up, net supply pushes it down.
        double net = demand - supply;
        double elasticity = config.priceElasticity();
        double factor = 1d + Math.tanh(net * elasticity);

        // Stock buffer: low stock adds upward pressure, high stock adds downward.
        if (item.stock() < 1d) {
            factor *= 1.10d;
        } else if (item.stock() > 1_000d) {
            factor *= 0.90d;
        }

        double min = config.priceMinFactor();
        double max = config.priceMaxFactor();
        if (factor < min) factor = min;
        if (factor > max) factor = max;

        double next = base * factor;
        // Round to two decimal places — the currency is "coins", not fractions of a coin.
        next = Math.round(next * 100d) / 100d;
        return new Money(next, item.lastPrice().currency());
    }

    /**
     * Apply a buy transaction: increases demand, decreases stock.
     */
    public MarketItem onBuy(MarketItem item, double quantity) {
        return new MarketItem(
                item.itemId(),
                item.displayName(),
                item.basePrice(),
                Math.max(0d, item.stock() - quantity),
                item.demand() + quantity,
                item.supply(),
                item.lastPrice(),
                System.currentTimeMillis());
    }

    /**
     * Apply a sell transaction: increases supply, increases stock.
     */
    public MarketItem onSell(MarketItem item, double quantity) {
        return new MarketItem(
                item.itemId(),
                item.displayName(),
                item.basePrice(),
                item.stock() + quantity,
                item.demand(),
                item.supply() + quantity,
                item.lastPrice(),
                System.currentTimeMillis());
    }
}
