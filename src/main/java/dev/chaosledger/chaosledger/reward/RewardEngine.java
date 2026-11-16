package dev.chaosledger.chaosledger.reward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.chaosledger.chaosledger.audit.AuditEntry;
import dev.chaosledger.chaosledger.audit.AuditLogger;
import dev.chaosledger.chaosledger.economy.EconomyEngine;
import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.storage.RewardRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative reward service.
 *
 * <p>The engine is invoked by the {@code QuestEngine} when a quest is claimed,
 * and by the admin API for ad-hoc grants. Money rewards go through the
 * {@link EconomyEngine} (so the transaction is in the ledger); item rewards go
 * through the registered {@link ItemDelivery} adapter. Every grant is logged
 * into the {@code rewards_granted} table and an audit entry is emitted.</p>
 */
public final class RewardEngine {

    private final EconomyEngine economy;
    private final RewardRepository rewards;
    private final AuditLogger audit;
    private final ItemDelivery itemDelivery;
    private final Gson gson = new Gson();

    public RewardEngine(EconomyEngine economy,
                       RewardRepository rewards,
                       AuditLogger audit,
                       ItemDelivery itemDelivery) {
        this.economy = economy;
        this.rewards = rewards;
        this.audit = audit;
        this.itemDelivery = itemDelivery;
    }

    public void grant(UUID playerUuid, String playerDisplay, Reward reward, String questId) {
        switch (reward.type()) {
            case MONEY -> {
                Money amount = reward.money();
                economy.grant(playerUuid, playerDisplay, amount.currency(), amount, "Quest reward: " + (questId == null ? "ad-hoc" : questId));
            }
            case ITEM -> {
                if (itemDelivery == null) {
                    throw new RewardException("Item delivery is not registered");
                }
                boolean ok = itemDelivery.deliver(playerUuid, reward.itemId(), reward.count());
                if (!ok) {
                    // Inventory full — fall back to a money grant at a notional price.
                    Money fallback = new Money(reward.count(), economy.currency(economy.currencies().isEmpty() ? "CC" : economy.currencies().get(0).code())
                            .orElseThrow().code());
                    economy.grant(playerUuid, playerDisplay, fallback.currency(), fallback, "Item overflow fallback: " + reward.itemId());
                }
            }
        }
        long id = rewards.recordGrant(playerUuid, questId, reward);

        JsonObject payload = new JsonObject();
        payload.addProperty("rewardId", id);
        payload.addProperty("type", reward.type().name());
        if (reward.type() == Reward.Type.MONEY) {
            payload.addProperty("currency", reward.money().currency());
            payload.addProperty("amount", reward.money().amount());
        } else {
            payload.addProperty("itemId", reward.itemId());
            payload.addProperty("count", reward.count());
        }
        audit.log(new AuditEntry(0, playerUuid, playerDisplay, "REWARD_GRANT",
                questId, gson.toJson(payload), System.currentTimeMillis()));
    }

    public List<Reward> history(UUID playerUuid, int limit) {
        return rewards.forPlayer(playerUuid, limit);
    }

    public Optional<ItemDelivery> itemDelivery() { return Optional.ofNullable(itemDelivery); }
}
