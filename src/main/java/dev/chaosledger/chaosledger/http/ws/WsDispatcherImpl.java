package dev.chaosledger.chaosledger.http.ws;

import com.google.gson.JsonObject;
import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.economy.Currency;
import dev.chaosledger.chaosledger.economy.EconomyException;
import dev.chaosledger.chaosledger.economy.MarketItem;
import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.http.HttpApiServer;
import dev.chaosledger.chaosledger.http.dto.Dtos;
import dev.chaosledger.chaosledger.http.handler.RewardsHandler;
import dev.chaosledger.chaosledger.quest.QuestEvent;
import dev.chaosledger.chaosledger.reward.Reward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * Default {@link WsDispatcher} — mirrors the existing HTTP handlers so the WS
 * transport returns <em>exactly</em> the same DTOs the bot already parses.
 *
 * <p>Threading: commands are submitted to the {@link HttpApiServer#executor()}
 * single-thread pool when the HTTP API is running, so writes serialise against
 * in-flight HTTP requests. If HTTP is disabled (typical on Exaroton), the
 * dispatcher spins up its own single-thread executor ({@code ChaosLedger-WS-Dispatch})
 * — same guarantee, different thread name.</p>
 */
public final class WsDispatcherImpl implements WsDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosLedger-WS-Dispatch");

    private final HttpApiServer httpApi;
    private volatile Executor fallback;

    public WsDispatcherImpl(HttpApiServer httpApi) {
        this.httpApi = httpApi;
    }

    @Override
    public CompletableFuture<Object> dispatch(String action, JsonObject data) {
        Executor exec = pickExecutor();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return runAction(action, data);
            } catch (RuntimeException e) {
                throw e;
            }
        }, exec);
    }

    private Executor pickExecutor() {
        if (httpApi != null) {
            Executor e = httpApi.executor();
            if (e != null) return e;
        }
        Executor f = fallback;
        if (f == null) {
            synchronized (this) {
                f = fallback;
                if (f == null) {
                    f = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "ChaosLedger-WS-Dispatch");
                        t.setDaemon(true);
                        return t;
                    });
                    fallback = f;
                }
            }
        }
        return f;
    }

    // --- action table ---------------------------------------------------------

    private Object runAction(String action, JsonObject data) {
        return switch (action) {
            case "pay"            -> doPay(data);
            case "grant"          -> doGrant(data);
            case "charge"         -> doCharge(data);
            case "market_buy"     -> doMarketTrade(data, true);
            case "market_sell"    -> doMarketTrade(data, false);
            case "market_define"  -> doMarketDefine(data);
            case "quest_start"    -> doQuestAction(data, "start");
            case "quest_available"-> doQuestAction(data, "available");
            case "quest_claim"    -> doQuestAction(data, "claim");
            case "quest_fail"     -> doQuestAction(data, "fail");
            case "quest_event"    -> doQuestEvent(data);
            case "reward_grant"   -> doRewardGrant(data);
            case "currency_define"-> doCurrencyDefine(data);
            // v0.2.0: GET-команды не из snapshot'а (нужны cl-balance / cl-history).
            case "account"        -> doGetAccount(data);
            case "player_transactions" -> doPlayerTransactions(data);
            case "snapshot"       -> throw new IllegalArgumentException(
                    "snapshot is a push message, not a command");
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    // --- transactions ---------------------------------------------------------

    private Object doPay(JsonObject d) {
        Dtos.PayRequest req = GSON.fromJson(d, Dtos.PayRequest.class);
        Money amount = new Money(req.amount(), req.currency());
        return Dtos.TransactionDto.of(ChaosLedger.services().economy().pay(
                req.fromUuid(), req.fromName(), req.toUuid(), req.toName(),
                req.currency(), amount, req.reason()));
    }

    private Object doGrant(JsonObject d) {
        Dtos.GrantRequest req = GSON.fromJson(d, Dtos.GrantRequest.class);
        Money amount = new Money(req.amount(), req.currency());
        return Dtos.TransactionDto.of(
                ChaosLedger.services().economy().grant(
                        req.playerUuid(), req.playerName(), req.currency(), amount, req.reason()));
    }

    private Object doCharge(JsonObject d) {
        Dtos.ChargeRequest req = GSON.fromJson(d, Dtos.ChargeRequest.class);
        Money amount = new Money(req.amount(), req.currency());
        String category = req.category() == null ? "ADMIN" : req.category();
        return Dtos.TransactionDto.of(ChaosLedger.services().economy().charge(
                req.playerUuid(), req.playerName(), req.currency(), amount, req.reason(), category));
    }

    // --- market ---------------------------------------------------------------

    private Object doMarketTrade(JsonObject d, boolean buy) {
        Dtos.MarketTradeRequest req = GSON.fromJson(d, Dtos.MarketTradeRequest.class);
        MarketItem result = buy
                ? ChaosLedger.services().marketEngine().buy(
                        req.playerUuid(), req.playerName(), req.itemId(), req.quantity())
                : ChaosLedger.services().marketEngine().sell(
                        req.playerUuid(), req.playerName(), req.itemId(), req.quantity());
        return Dtos.MarketItemDto.of(result);
    }

    private Object doMarketDefine(JsonObject d) {
        Dtos.DefineMarketItemRequest req = GSON.fromJson(d, Dtos.DefineMarketItemRequest.class);
        Money base = new Money(req.basePrice(), req.currency());
        MarketItem item = new MarketItem(req.itemId(), req.displayName(), base,
                req.stock(), 0d, 0d, base, System.currentTimeMillis());
        ChaosLedger.services().marketEngine().define(item);
        return Dtos.MarketItemDto.of(item);
    }

    // --- quests ---------------------------------------------------------------

    private Object doQuestAction(JsonObject d, String kind) {
        Dtos.QuestActionRequest req = GSON.fromJson(d, Dtos.QuestActionRequest.class);
        return switch (kind) {
            case "start"     -> Dtos.QuestProgressDto.of(
                    ChaosLedger.services().questEngine().start(req.playerUuid(), req.questId()));
            case "available" -> Dtos.QuestProgressDto.of(
                    ChaosLedger.services().questEngine().makeAvailable(req.playerUuid(), req.questId()));
            case "claim"     -> {
                ChaosLedger.services().questEngine().claim(req.playerUuid(), req.playerName(), req.questId());
                yield new JsonObject();  // empty 200
            }
            case "fail"      -> {
                ChaosLedger.services().questEngine().fail(req.playerUuid(), req.questId());
                yield new JsonObject();
            }
            default -> throw new IllegalStateException("quest action: " + kind);
        };
    }

    private Object doQuestEvent(JsonObject d) {
        Dtos.QuestEventRequest req = GSON.fromJson(d, Dtos.QuestEventRequest.class);
        QuestEvent event = new QuestEvent(req.type(), req.target(), req.amount(),
                req.playerUuid(), req.playerName());
        ChaosLedger.services().questEngine().ingest(event);
        return new JsonObject();
    }

    // --- rewards --------------------------------------------------------------

    private Object doRewardGrant(JsonObject d) {
        RewardsHandler.RewardGrantRequest req = GSON.fromJson(d, RewardsHandler.RewardGrantRequest.class);
        Reward reward = toReward(req.reward());
        ChaosLedger.services().rewardEngine().grant(
                req.playerUuid(), req.playerName(), reward, req.questId());
        return new JsonObject();
    }

    // --- currencies -----------------------------------------------------------

    private Object doCurrencyDefine(JsonObject d) {
        Dtos.CurrencyDto dto = GSON.fromJson(d, Dtos.CurrencyDto.class);
        Currency currency = new Currency(dto.code(), dto.displayName(), dto.symbol(), dto.defaultStarting());
        ChaosLedger.services().economy().defineCurrency(currency);
        return Dtos.CurrencyDto.of(currency);
    }

    // --- GET commands (cl-balance / cl-history) -------------------------------
    // Snapshot не содержит баланса произвольного игрока или его персональной
    // истории транзакций — только топ-N по валюте. Эти две команды покрывают
    //缺口; дёргают те же read-API, что и AccountsHandler/TransactionsHandler.

    private Object doGetAccount(JsonObject d) {
        UUID ownerUuid = UUID.fromString(d.get("ownerUuid").getAsString());
        String ownerName = d.has("ownerName") && !d.get("ownerName").isJsonNull()
                ? d.get("ownerName").getAsString() : ownerUuid.toString();
        String currency = d.get("currency").getAsString();
        return Dtos.AccountDto.of(ChaosLedger.services().economy().accountOf(ownerUuid, ownerName, currency));
    }

    private Object doPlayerTransactions(JsonObject d) {
        UUID ownerUuid = UUID.fromString(d.get("ownerUuid").getAsString());
        int limit = d.has("limit") && d.get("limit").isJsonPrimitive()
                ? d.get("limit").getAsInt() : 20;
        return ChaosLedger.services().economy().history(ownerUuid, limit).stream()
                .map(Dtos.TransactionDto::of)
                .toList();
    }

    // --- helpers --------------------------------------------------------------

    private static Reward toReward(Dtos.RewardDto dto) {
        if (dto == null) {
            throw new EconomyException("Quest reward is required");
        }
        return switch (Reward.Type.valueOf(dto.type())) {
            case MONEY  -> Reward.money(new Money(dto.money().amount(), dto.money().currency()));
            case ITEM   -> Reward.item(dto.itemId(), dto.count());
        };
    }
}
