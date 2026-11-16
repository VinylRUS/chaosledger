package dev.chaosledger.chaosledger.quest;

import java.util.List;
import java.util.Objects;

/**
 * A single objective inside a quest.
 *
 * <p>{@code type} is the machine-readable matcher ({@code COLLECT}, {@code KILL},
 * {@code REACH}, {@code TALK}, {@code PAY}) used by the {@code QuestEngine} to
 * route gameplay events into the right objective. {@code target} is the matched
 * identifier (item id, entity type id, biome id, npc id, currency code) and
 * {@code required} is the count/amount the player must accumulate.</p>
 */
public record QuestObjective(String id, String type, String target, double required,
                             String description) {

    public QuestObjective {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (required <= 0) {
            throw new IllegalArgumentException("required must be > 0");
        }
        description = description == null ? "" : description;
    }

    public boolean matches(String event, String eventTarget) {
        return type.equals(event) && Objects.equals(target, eventTarget);
    }

    public static List<QuestObjective> list(QuestObjective... objs) {
        return List.of(objs);
    }
}
