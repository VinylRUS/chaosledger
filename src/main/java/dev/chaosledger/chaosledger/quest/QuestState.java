package dev.chaosledger.chaosledger.quest;

import java.util.Objects;

/**
 * Machine-readable state of a quest in a player's progression.
 *
 * <p>Allowed transitions: {@code LOCKED → AVAILABLE → ACTIVE → COMPLETED → CLAIMED},
 * with {@code FAILED} reachable from {@code ACTIVE} on objective timeout or explicit
 * cancel. The state is the only mutable field of a quest progression row.</p>
 */
public enum QuestState {
    LOCKED,
    AVAILABLE,
    ACTIVE,
    COMPLETED,
    CLAIMED,
    FAILED;

    public boolean canTransitionTo(QuestState next) {
        Objects.requireNonNull(next, "next");
        return switch (this) {
            case LOCKED    -> next == AVAILABLE;
            case AVAILABLE -> next == ACTIVE || next == LOCKED;
            case ACTIVE    -> next == COMPLETED || next == FAILED;
            case COMPLETED -> next == CLAIMED || next == ACTIVE;
            case CLAIMED   -> next == ACTIVE; // repeatable quests
            case FAILED    -> next == ACTIVE; // retry
        };
    }
}
