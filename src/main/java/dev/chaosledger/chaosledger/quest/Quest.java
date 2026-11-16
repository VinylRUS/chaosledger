package dev.chaosledger.chaosledger.quest;

import dev.chaosledger.chaosledger.reward.Reward;

import java.util.List;
import java.util.Objects;

/**
 * A quest definition.
 *
 * <p>The {@code id} is a stable, human-readable slug ({@code daily_miner_1}) used
 * across YAML/JSON configs, the database and the HTTP API. Objectives are ordered;
 * the player completes them in sequence. The {@link Reward} is granted when the
 * quest transitions to {@link QuestState#CLAIMED}.</p>
 */
public record Quest(String id, String title, String description,
                    List<QuestObjective> objectives, Reward reward,
                    boolean repeatable, long cooldownSec,
                    long createdAt, long updatedAt) {

    public Quest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(objectives, "objectives");
        Objects.requireNonNull(reward, "reward");
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("quest " + id + " has no objectives");
        }
        objectives = List.copyOf(objectives);
    }

    public QuestObjective firstIncomplete(List<Double> progress) {
        for (int i = 0; i < objectives.size(); i++) {
            double done = i < progress.size() ? progress.get(i) : 0d;
            if (done < objectives.get(i).required()) {
                return objectives.get(i);
            }
        }
        return null;
    }

    public boolean isComplete(List<Double> progress) {
        if (progress.size() < objectives.size()) {
            return false;
        }
        for (int i = 0; i < objectives.size(); i++) {
            if (progress.get(i) < objectives.get(i).required()) {
                return false;
            }
        }
        return true;
    }
}
