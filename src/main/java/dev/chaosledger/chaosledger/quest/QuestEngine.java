package dev.chaosledger.chaosledger.quest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.chaosledger.chaosledger.audit.AuditEntry;
import dev.chaosledger.chaosledger.audit.AuditLogger;
import dev.chaosledger.chaosledger.reward.RewardEngine;
import dev.chaosledger.chaosledger.storage.QuestProgressRepository;
import dev.chaosledger.chaosledger.storage.QuestRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative quest state-machine.
 *
 * <p>The engine is the only writer of {@link QuestProgress} rows. It accepts
 * gameplay events, advances matching objectives, and transitions the quest's
 * state according to {@link QuestState#canTransitionTo(QuestState)}. When a
 * quest reaches {@link QuestState#CLAIMED}, the {@link RewardEngine} is invoked.</p>
 */
public final class QuestEngine {

    private final QuestRepository quests;
    private final QuestProgressRepository progress;
    private final RewardEngine rewards;
    private final AuditLogger audit;
    private final Gson gson = new Gson();

    public QuestEngine(QuestRepository quests,
                      QuestProgressRepository progress,
                      RewardEngine rewards,
                      AuditLogger audit) {
        this.quests = quests;
        this.progress = progress;
        this.rewards = rewards;
        this.audit = audit;
    }

    public void define(Quest quest) {
        quests.save(quest);
    }

    public void delete(String questId) {
        quests.delete(questId);
    }

    public Optional<Quest> quest(String id) {
        return quests.findById(id);
    }

    public List<Quest> all() {
        return quests.all();
    }

    public QuestProgress progressOf(UUID playerUuid, String questId) {
        return progress.getOrCreate(playerUuid, questId);
    }

    public List<QuestProgress> progressOf(UUID playerUuid) {
        return progress.forPlayer(playerUuid);
    }

    /** Make a quest available for the player (does not start it). */
    public QuestProgress makeAvailable(UUID playerUuid, String questId) {
        Quest quest = quests.findById(questId)
                .orElseThrow(() -> new QuestException("Unknown quest: " + questId));
        QuestProgress p = progress.getOrCreate(playerUuid, questId);
        if (p.state() == QuestState.LOCKED) {
            p.setState(QuestState.AVAILABLE);
            progress.save(p);
            audit.log(new AuditEntry(0, playerUuid, null, "QUEST_AVAILABLE",
                    questId, null, System.currentTimeMillis()));
        }
        return p;
    }

    /** Start an available quest for the player. */
    public QuestProgress start(UUID playerUuid, String questId) {
        Quest quest = quests.findById(questId)
                .orElseThrow(() -> new QuestException("Unknown quest: " + questId));
        QuestProgress p = progress.getOrCreate(playerUuid, questId);
        if (p.state() == QuestState.LOCKED) {
            p.setState(QuestState.AVAILABLE);
        }
        if (p.state() != QuestState.ACTIVE) {
            p.setState(QuestState.ACTIVE);
            progress.save(p);
            audit.log(new AuditEntry(0, playerUuid, null, "QUEST_START",
                    questId, gson.toJson(quest.objectives().size() + " objectives"),
                    System.currentTimeMillis()));
        }
        return p;
    }

    /** Apply a gameplay event to every active quest of the player. */
    public void ingest(QuestEvent event) {
        UUID playerUuid = event.actorUuid()
                .orElseThrow(() -> new QuestException("QuestEvent requires an actor uuid"));
        for (QuestProgress p : progress.forPlayer(playerUuid)) {
            if (p.state() != QuestState.ACTIVE) continue;
            Quest quest = quests.findById(p.questId()).orElse(null);
            if (quest == null) continue;

            boolean changed = false;
            for (int i = 0; i < quest.objectives().size(); i++) {
                QuestObjective obj = quest.objectives().get(i);
                if (obj.matches(event.type(), event.target())) {
                    p.addProgress(i, event.amount());
                    changed = true;
                }
            }
            if (changed) {
                if (quest.isComplete(p.progress())) {
                    p.setState(QuestState.COMPLETED);
                    audit.log(new AuditEntry(0, playerUuid, event.actorName().orElse(null),
                            "QUEST_COMPLETE", p.questId(), null, System.currentTimeMillis()));
                }
                progress.save(p);
            }
        }
    }

    /** Claim the reward of a completed quest; flips it to CLAIMED and grants the reward. */
    public void claim(UUID playerUuid, String playerDisplay, String questId) {
        Quest quest = quests.findById(questId)
                .orElseThrow(() -> new QuestException("Unknown quest: " + questId));
        QuestProgress p = progress.getOrCreate(playerUuid, questId);
        if (p.state() == QuestState.ACTIVE && quest.isComplete(p.progress())) {
            p.setState(QuestState.COMPLETED);
            progress.save(p);
        }
        if (p.state() != QuestState.COMPLETED) {
            throw new QuestException("Quest " + questId + " is not COMPLETED for " + playerUuid);
        }
        p.setState(QuestState.CLAIMED);
        progress.save(p);

        rewards.grant(playerUuid, playerDisplay, quest.reward(), questId);

        JsonObject payload = new JsonObject();
        payload.addProperty("rewardType", quest.reward().type().name());
        audit.log(new AuditEntry(0, playerUuid, playerDisplay, "QUEST_CLAIM",
                questId, gson.toJson(payload), System.currentTimeMillis()));

        // For repeatable quests, reset to ACTIVE so the player can re-tackle it.
        if (quest.repeatable()) {
            p.setState(QuestState.ACTIVE);
            progress.save(p);
        }
    }

    public void fail(UUID playerUuid, String questId) {
        QuestProgress p = progress.getOrCreate(playerUuid, questId);
        if (p.state() == QuestState.ACTIVE) {
            p.setState(QuestState.FAILED);
            progress.save(p);
            audit.log(new AuditEntry(0, playerUuid, null, "QUEST_FAIL",
                    questId, null, System.currentTimeMillis()));
        }
    }
}
