package dev.chaosledger.chaosledger.economy;

import java.util.Objects;

/**
 * Definition of a tradeable item on the ChaosLedger market.
 *
 * <p>{@code itemId} is the canonical item identifier — for vanilla items the
 * registry id (e.g. {@code minecraft:diamond}); for modded items the namespaced id.
 * {@code basePrice} is the unmodified anchor used by the {@link PriceEngine},
 * and {@code stock}/{@code demand}/{@code supply} are the live signals used to
 * nudge the price up or down.</p>
 */
public record MarketItem(String itemId, String displayName, Money basePrice,
                         double stock, double demand, double supply,
                         Money lastPrice, long updatedAt) {

    public MarketItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(lastPrice, "lastPrice");
        if (!basePrice.currency().equals(lastPrice.currency())) {
            throw new IllegalArgumentException("basePrice and lastPrice must share currency");
        }
    }
}
