package dev.chaosledger.chaosledger.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record of an auditable action performed against the ledger.
 *
 * <p>Every economy / quest / reward / admin operation produces an {@link AuditEntry}.
 * The {@code payloadJson} is a free-form JSON snapshot of the operation's inputs and
 * outputs, written verbatim by the calling engine; the {@code action} tag is a
 * short machine-readable name ({@code ACCOUNT_PAY}, {@code QUEST_CLAIM},
 * {@code MARKET_BUY}, {@code ADMIN_GRANT}).</p>
 */
public record AuditEntry(long id, UUID actorUuid, String actorName, String action,
                         String target, String payloadJson, long createdAt) {

    public AuditEntry {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
    }

    public Optional<UUID> actorUuid() { return Optional.ofNullable(actorUuid); }
    public Optional<String> actorName() { return Optional.ofNullable(actorName); }
    public Optional<String> target() { return Optional.ofNullable(target); }
    public Optional<String> payloadJson() { return Optional.ofNullable(payloadJson); }
}
