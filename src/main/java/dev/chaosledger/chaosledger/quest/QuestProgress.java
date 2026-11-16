package dev.chaosledger.chaosledger.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable per-(player, quest) progress.
 *
 * <p>{@code progress} is a list aligned with {@link Quest#objectives()}: each
 * entry holds the accumulated count for the matching objective. The
 * {@code QuestEngine} updates these entries from gameplay events and flips
 * {@link #state} through the {@link QuestState} state-machine.</p>
 */
public final class QuestProgress {

    private final long id;
    private final UUID playerUuid;
    private final String questId;
    private QuestState state;
    private final List<Double> progress;
    private final long startedAt;
    private Long completedAt;
    private Long claimedAt;

    public QuestProgress(long id, UUID playerUuid, String questId, QuestState state,
                         List<Double> progress, long startedAt,
                         Long completedAt, Long claimedAt) {
        this.id = id;
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.questId = Objects.requireNonNull(questId, "questId");
        this.state = Objects.requireNonNull(state, "state");
        this.progress = new ArrayList<>(progress);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.claimedAt = claimedAt;
    }

    public long id() { return id; }
    public UUID playerUuid() { return playerUuid; }
    public String questId() { return questId; }
    public QuestState state() { return state; }
    public List<Double> progress() { return Collections.unmodifiableList(progress); }
    public long startedAt() { return startedAt; }
    public Long completedAt() { return completedAt; }
    public Long claimedAt() { return claimedAt; }

    public void setState(QuestState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal quest transition: " + state + " → " + next);
        }
        this.state = next;
        long now = System.currentTimeMillis();
        if (next == QuestState.COMPLETED) {
            this.completedAt = now;
        } else if (next == QuestState.CLAIMED) {
            this.claimedAt = now;
        } else if (next == QuestState.ACTIVE && completedAt != null) {
            // retrying a failed / already completed quest
            this.completedAt = null;
            this.claimedAt = null;
        }
    }

    public void addProgress(int objectiveIndex, double delta) {
        while (progress.size() <= objectiveIndex) {
            progress.add(0d);
        }
        progress.set(objectiveIndex, Math.max(0d, progress.get(objectiveIndex) + delta));
    }

    public void setProgress(int objectiveIndex, double value) {
        while (progress.size() <= objectiveIndex) {
            progress.add(0d);
        }
        progress.set(objectiveIndex, Math.max(0d, value));
    }
}
