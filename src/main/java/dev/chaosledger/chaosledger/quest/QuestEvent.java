package dev.chaosledger.chaosledger.quest;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A gameplay event produced by Fabric hooks (pickup, kill, travel, ...).
 *
 * <p>Quests subscribe to events by {@link #type} + {@link #target}. The amount
 * field is how much the matching objective should advance (default {@code 1}).</p>
 */
public record QuestEvent(String type, String target, double amount, UUID actorUuid,
                         String actorName) {

    public QuestEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (amount <= 0) amount = 1d;
    }

    public Optional<UUID> actorUuid() { return Optional.ofNullable(actorUuid); }
    public Optional<String> actorName() { return Optional.ofNullable(actorName); }
}
