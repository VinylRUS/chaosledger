package dev.chaosledger.chaosledger.economy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record of a single ledger movement.
 *
 * <p>Either {@code fromUuid} or {@code toUuid} can be empty to represent
 * minting / burning (rewards, fees, admin adjustments), but never both at the
 * same time. The {@code reason} is a short human-readable string used in chat
 * and the web panel; {@code category} is a machine-readable tag
 * ({@code PAY}, {@code REWARD}, {@code QUEST}, {@code ADMIN}, {@code FEE}).</p>
 */
public record Transaction(long id, Optional<UUID> fromUuid, Optional<UUID> toUuid, Money amount,
                          String reason, String category, Optional<String> reference,
                          long createdAt) {

    public Transaction {
        Objects.requireNonNull(fromUuid, "fromUuid");
        Objects.requireNonNull(toUuid, "toUuid");
        Objects.requireNonNull(reference, "reference");
        if (fromUuid.isEmpty() && toUuid.isEmpty()) {
            throw new IllegalArgumentException("fromUuid and toUuid cannot both be empty");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
    }

    /** Convenience constructor wrapping nullable primitives into {@link Optional}. */
    public Transaction(long id, UUID fromUuid, UUID toUuid, Money amount,
                       String reason, String category, String reference, long createdAt) {
        this(id, Optional.ofNullable(fromUuid), Optional.ofNullable(toUuid), amount,
                reason, category, Optional.ofNullable(reference), createdAt);
    }
}
