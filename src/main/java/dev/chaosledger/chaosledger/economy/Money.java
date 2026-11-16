package dev.chaosledger.chaosledger.economy;

import java.util.Objects;

/**
 * Type-safe monetary amount: value + currency code.
 *
 * <p>Money is immutable. All arithmetic returns a new instance and validates that
 * the operands share the same currency. Money is the only acceptable type for
 * any economy operation; raw {@code double}s must never be threaded through the
 * API directly because the currency would be lost.</p>
 */
public record Money(double amount, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("amount must be finite");
        }
    }

    public static Money zero(String currency) {
        return new Money(0d, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount + other.amount, currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount - other.amount, currency);
    }

    public Money negate() {
        return new Money(-amount, currency);
    }

    public boolean isNegative() {
        return amount < 0d;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        requireSameCurrency(other);
        return amount >= other.amount;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
