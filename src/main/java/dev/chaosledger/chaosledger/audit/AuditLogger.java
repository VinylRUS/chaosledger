package dev.chaosledger.chaosledger.audit;

import dev.chaosledger.chaosledger.storage.AuditRepository;

import java.util.List;

/**
 * Thin façade over {@link AuditRepository} used by all engines.
 *
 * <p>Centralising audit through this class keeps the action / payload contract
 * in one place and makes it trivial to add in-memory buffering or async writes
 * later without touching the engines.</p>
 */
public final class AuditLogger {

    private final AuditRepository repository;

    public AuditLogger(AuditRepository repository) {
        this.repository = repository;
    }

    public long log(AuditEntry entry) {
        return repository.record(entry);
    }

    public List<AuditEntry> recent(int limit) {
        return repository.recent(limit);
    }

    public List<AuditEntry> forActor(java.util.UUID actorUuid, int limit) {
        return repository.forActor(actorUuid, limit);
    }
}
