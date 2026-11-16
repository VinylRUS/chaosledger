package dev.chaosledger.chaosledger.economy;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record of a single ledger movement.
 *
 * <p>Either {@code fromUuid} or {@code toUuid} can be {@code null} to represent
 * minting / burning (rewards, fees, admin adjustments), but never both at the
 * same time. The {@code reason} is a short human-readable string used in chat
 * and the web panel; {@code category} is a machine-readable tag
 * ({@code PAY}, {@code REWARD}, {@code QUEST}, {@code ADMIN}, {@code FEE}).</p>
 */
public record Transaction(long id, UUID fromUuid, UUID toUuid, Money amount,
                          String reason, String category, String reference,
                          long createdAt) {

    public Transaction {
        if (fromUuid == null && toUuid == null) {
            throw new IllegalArgumentException("fromUuid and toUuid cannot both be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
    }

    public Optional<UUID> fromUuid()  { return Optional.ofNullable(fromUuid); }
    public Optional<UUID> toUuid()    { return Optional.ofNullable(toUuid); }
    public Optional<String> reference() { return Optional.ofNullable(reference); }
}
