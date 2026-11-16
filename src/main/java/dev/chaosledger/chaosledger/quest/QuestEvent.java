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
public record QuestEvent(String type, String target, double amount, Optional<UUID> actorUuid,
                         Optional<String> actorName) {

    public QuestEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(actorName, "actorName");
        if (amount <= 0) amount = 1d;
    }

    /** Convenience constructor wrapping nullable primitives into {@link Optional}. */
    public QuestEvent(String type, String target, double amount, UUID actorUuid, String actorName) {
        this(type, target, amount, Optional.ofNullable(actorUuid), Optional.ofNullable(actorName));
    }
}
