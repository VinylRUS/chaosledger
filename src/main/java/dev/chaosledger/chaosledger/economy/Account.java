package dev.chaosledger.chaosledger.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's account in a single currency.
 *
 * <p>The triple (ownerUuid, ownerName, currency) identifies an account. {@code ownerName}
 * is the snapshot of the player's username at the time the account was last touched;
 * it is convenient for the web panel but is not authoritative — the UUID is.</p>
 */
public record Account(long id, UUID ownerUuid, String ownerName, String currency, Money balance,
                      long createdAt, long updatedAt) {

    public Account {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(balance, "balance");
        if (!currency.equals(balance.currency())) {
            throw new IllegalArgumentException(
                    "Account currency " + currency + " does not match balance currency " + balance.currency());
        }
    }

    public Account withBalance(Money newBalance) {
        return new Account(id, ownerUuid, ownerName, currency, newBalance, createdAt, System.currentTimeMillis());
    }
}
