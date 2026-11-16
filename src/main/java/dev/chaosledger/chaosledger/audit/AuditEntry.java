package dev.chaosledger.chaosledger.audit;

import java.util.Objects;
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
 *
 * <p>Nullable fields ({@code actorUuid}, {@code actorName}, {@code target},
 * {@code payloadJson}) are declared as {@link Optional} so the accessor types are
 * unambiguous and never expose a bare {@code null}.</p>
 */
public record AuditEntry(long id, Optional<UUID> actorUuid, Optional<String> actorName, String action,
                         Optional<String> target, Optional<String> payloadJson, long createdAt) {

    public AuditEntry {
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
    }

    /** Convenience constructor wrapping nullable primitives into {@link Optional}. */
    public AuditEntry(long id, UUID actorUuid, String actorName, String action,
                      String target, String payloadJson, long createdAt) {
        this(id, Optional.ofNullable(actorUuid), Optional.ofNullable(actorName), action,
                Optional.ofNullable(target), Optional.ofNullable(payloadJson), createdAt);
    }
}
