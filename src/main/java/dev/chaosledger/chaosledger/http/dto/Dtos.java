package dev.chaosledger.chaosledger.http.dto;

import dev.chaosledger.chaosledger.economy.Account;
import dev.chaosledger.chaosledger.economy.Currency;
import dev.chaosledger.chaosledger.economy.MarketItem;
import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.economy.Transaction;
import dev.chaosledger.chaosledger.audit.AuditEntry;
import dev.chaosledger.chaosledger.quest.Quest;
import dev.chaosledger.chaosledger.quest.QuestObjective;
import dev.chaosledger.chaosledger.quest.QuestProgress;
import dev.chaosledger.chaosledger.reward.Reward;

import java.util.List;
import java.util.UUID;

/**
 * Plain-data transfer objects for the HTTP layer.
 *
 * <p>These types decouple the JSON contract exposed to the web panel from the
 * internal domain models so that internal refactors do not silently change
 * the wire format. Every DTO is a record; Gson serialises them directly.</p>
 */
public final class Dtos {

    private Dtos() {}

    public record CurrencyDto(String code, String displayName, String symbol, double defaultStarting) {
        public static CurrencyDto of(Currency c) {
            return new CurrencyDto(c.code(), c.displayName(), c.symbol(), c.defaultStarting());
        }
    }

    public record MoneyDto(double amount, String currency) {
        public static MoneyDto of(Money m) { return new MoneyDto(m.amount(), m.currency()); }
    }

    public record AccountDto(long id, UUID ownerUuid, String ownerName, String currency,
                             MoneyDto balance, long createdAt, long updatedAt) {
        public static AccountDto of(Account a) {
            return new AccountDto(a.id(), a.ownerUuid(), a.ownerName(), a.currency(),
                    MoneyDto.of(a.balance()), a.createdAt(), a.updatedAt());
        }
    }

    public record TransactionDto(long id, UUID fromUuid, UUID toUuid, MoneyDto amount,
                                 String reason, String category, String reference, long createdAt) {
        public static TransactionDto of(Transaction t) {
            return new TransactionDto(t.id(), t.fromUuid().orElse(null),
                    t.toUuid().orElse(null),
                    MoneyDto.of(t.amount()), t.reason(), t.category(),
                    t.reference().orElse(null), t.createdAt());
        }
    }

    public record MarketItemDto(String itemId, String displayName, MoneyDto basePrice,
                                double stock, double demand, double supply,
                                MoneyDto lastPrice, long updatedAt) {
        public static MarketItemDto of(MarketItem m) {
            return new MarketItemDto(m.itemId(), m.displayName(), MoneyDto.of(m.basePrice()),
                    m.stock(), m.demand(), m.supply(), MoneyDto.of(m.lastPrice()), m.updatedAt());
        }
    }

    public record PricePointDto(double price, long recordedAt) {}

    public record ObjectiveDto(String id, String type, String target, double required, String description) {
        public static ObjectiveDto of(QuestObjective o) {
            return new ObjectiveDto(o.id(), o.type(), o.target(), o.required(), o.description());
        }
    }

    public record RewardDto(String type, MoneyDto money, String itemId, int count, String label) {
        public static RewardDto of(Reward r) {
            return new RewardDto(r.type().name(),
                    r.money() == null ? null : MoneyDto.of(r.money()),
                    r.itemId(), r.count(), r.label());
        }
    }

    public record QuestDto(String id, String title, String description,
                          List<ObjectiveDto> objectives, RewardDto reward,
                          boolean repeatable, long cooldownSec,
                          long createdAt, long updatedAt) {
        public static QuestDto of(Quest q) {
            return new QuestDto(q.id(), q.title(), q.description(),
                    q.objectives().stream().map(ObjectiveDto::of).toList(),
                    RewardDto.of(q.reward()), q.repeatable(), q.cooldownSec(),
                    q.createdAt(), q.updatedAt());
        }
    }

    public record QuestProgressDto(long id, UUID playerUuid, String questId, String state,
                                   List<Double> progress, long startedAt,
                                   Long completedAt, Long claimedAt) {
        public static QuestProgressDto of(QuestProgress p) {
            return new QuestProgressDto(p.id(), p.playerUuid(), p.questId(), p.state().name(),
                    p.progress(), p.startedAt(), p.completedAt(), p.claimedAt());
        }
    }

    public record AuditEntryDto(long id, UUID actorUuid, String actorName, String action,
                               String target, String payloadJson, long createdAt) {
        public static AuditEntryDto of(AuditEntry e) {
            return new AuditEntryDto(e.id(), e.actorUuid().orElse(null),
                    e.actorName().orElse(null), e.action(), e.target().orElse(null),
                    e.payloadJson().orElse(null), e.createdAt());
        }
    }

    // ---- Request DTOs ----

    public record PayRequest(UUID fromUuid, String fromName, UUID toUuid, String toName,
                             String currency, double amount, String reason) {}

    public record GrantRequest(UUID playerUuid, String playerName, String currency,
                               double amount, String reason) {}

    public record ChargeRequest(UUID playerUuid, String playerName, String currency,
                                double amount, String reason, String category) {}

    public record MarketTradeRequest(UUID playerUuid, String playerName, String itemId, double quantity) {}

    public record DefineMarketItemRequest(String itemId, String displayName, String currency,
                                          double basePrice, double stock) {}

    public record DefineQuestRequest(String id, String title, String description,
                                     List<ObjectiveDto> objectives, RewardDto reward,
                                     boolean repeatable, long cooldownSec) {}

    public record QuestActionRequest(UUID playerUuid, String playerName, String questId) {}

    public record QuestEventRequest(UUID playerUuid, String playerName, String type,
                                    String target, double amount) {}
}
