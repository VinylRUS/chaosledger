package dev.chaosledger.chaosledger.http.ws;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.ChaosLedgerConfig;
import dev.chaosledger.chaosledger.ChaosLedgerMod;
import dev.chaosledger.chaosledger.audit.AuditEntry;
import dev.chaosledger.chaosledger.economy.Account;
import dev.chaosledger.chaosledger.economy.Currency;
import dev.chaosledger.chaosledger.economy.EconomyException;
import dev.chaosledger.chaosledger.economy.MarketItem;
import dev.chaosledger.chaosledger.economy.Transaction;
import dev.chaosledger.chaosledger.http.dto.Dtos;
import dev.chaosledger.chaosledger.quest.Quest;
import dev.chaosledger.chaosledger.reward.RewardException;
import dev.chaosledger.chaosledger.storage.StorageException;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * WebSocket reverse-tunnel client (mod → bot).
 *
 * <p>Exaroton/Aternos-style shared Minecraft hosting opens only the MC port
 * (25565) to the outside world. The mod's HTTP API listens on localhost inside
 * the container and is unreachable from the bot host. To bridge the gap without
 * asking the operator to open a port, the mod opens an <em>outbound</em>
 * WebSocket to the bot's {@code /ws/chaosledger} endpoint (Exaroton allows
 * outbound TCP/TLS). Over that single duplex connection the mod pushes state
 * snapshots and the bot sends command requests; replies come back on the same
 * socket.</p>
 *
 * <p>Threading: this client owns a single daemon thread for the WS connection
 * (managed by {@code Java-WebSocket}). Snapshot building runs on a small
 * scheduled executor. <strong>Command dispatch is delegated to the same
 * single-thread executor the HTTP handlers use</strong> ({@code ChaosLedger-HTTP})
 * so all writes stay serialised and the existing atomicity guarantees are
 * preserved — see {@link WsDispatcher#dispatch}.</p>
 *
 * <p>Wire protocol: {@code WS_TUNNEL_PROTOCOL.md} (v1).</p>
 */
public final class WsTunnelClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosLedger-WS");

    private static final int PROTOCOL_VERSION = 1;

    private final ChaosLedgerConfig config;
    private final WsDispatcher dispatcher;
    private final ScheduledExecutorService scheduler;

    private volatile ClientImpl client;
    private volatile boolean running;
    private volatile long snapshotSeq = 0L;
    private ScheduledFuture<?> snapshotTask;
    private ScheduledFuture<?> reconnectTask;

    public WsTunnelClient(ChaosLedgerConfig config, WsDispatcher dispatcher) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ChaosLedger-WS-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Start the connect loop. No-op if disabled in config. */
    public void start() {
        if (!config.wsEnabled() || config.wsUrl().isBlank()) {
            LOGGER.info("[ChaosLedger-WS] Disabled (wsEnabled=false or wsUrl empty). Reverse-tunnel not started.");
            return;
        }
        running = true;
        LOGGER.info("[ChaosLedger-WS] Starting reverse-tunnel → {} (serverId={})",
                config.wsUrl(), config.wsServerId());
        connect();
    }

    /** Graceful shutdown: send close 4011, stop scheduler, drop the socket. */
    public void stop() {
        running = false;
        if (snapshotTask != null) {
            snapshotTask.cancel(false);
            snapshotTask = null;
        }
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        if (client != null) {
            try {
                client.close(4011, "server shutting down");
            } catch (Exception ignored) {
                // best-effort
            }
        }
        scheduler.shutdownNow();
        LOGGER.info("[ChaosLedger-WS] Stopped.");
    }

    // --- connect loop with exponential backoff --------------------------------

    private void connect() {
        if (!running) return;
        URI uri;
        try {
            uri = URI.create(config.wsUrl());
        } catch (IllegalArgumentException e) {
            LOGGER.error("[ChaosLedger-WS] Bad wsUrl '{}': {}", config.wsUrl(), e.getMessage());
            return;
        }
        try {
            client = new ClientImpl(uri);
            client.connect();
        } catch (Exception e) {
            LOGGER.warn("[ChaosLedger-WS] connect() failed: {}", e.getMessage());
            scheduleReconnect(0);
        }
    }

    private void scheduleReconnect(int attempt) {
        if (!running) return;
        long base = Math.min(
                (long) config.wsReconnectMinMs() * (1L << Math.min(attempt, 16)),
                config.wsReconnectMaxMs()
        );
        // ±20% jitter
        long jitter = (long) (base * 0.2 * (Math.random() - 0.5) * 2);
        long delay = Math.max(config.wsReconnectMinMs(), base + jitter);
        LOGGER.info("[ChaosLedger-WS] Reconnect in {} ms (attempt {})", delay, attempt + 1);
        reconnectTask = scheduler.schedule(() -> connect(), delay, TimeUnit.MILLISECONDS);
    }

    // --- snapshot push --------------------------------------------------------

    private void startSnapshotLoop() {
        int period = Math.max(1, config.wsSnapshotIntervalSec());
        snapshotTask = scheduler.scheduleAtFixedRate(this::pushSnapshot,
                period, period, TimeUnit.SECONDS);
    }

    private void pushSnapshot() {
        ClientImpl c = client;
        if (c == null || !c.isOpen()) return;
        try {
            JsonObject snap = buildSnapshot();
            JsonObject msg = base("snapshot");
            msg.add("data", snap);
            c.send(GSON.toJson(msg));
        } catch (Exception e) {
            LOGGER.warn("[ChaosLedger-WS] snapshot push failed: {}", e.getMessage());
        }
    }

    private JsonObject buildSnapshot() {
        JsonObject d = new JsonObject();
        d.addProperty("seq", ++snapshotSeq);
        d.addProperty("currency", config.defaultCurrency());

        // currencies
        JsonArray currencies = new JsonArray();
        for (Currency c : ChaosLedger.services().economy().currencies()) {
            currencies.add(GSON.toJsonTree(Dtos.CurrencyDto.of(c)));
        }
        d.add("currencies", currencies);

        // top accounts in default currency (10)
        JsonArray accounts = new JsonArray();
        try {
            for (Account a : ChaosLedger.services().economy().top(config.defaultCurrency(), 10)) {
                accounts.add(GSON.toJsonTree(Dtos.AccountDto.of(a)));
            }
        } catch (EconomyException | StorageException e) {
            d.addProperty("accountsError", e.getMessage());
        }
        d.add("accounts", accounts);

        // market
        JsonArray market = new JsonArray();
        for (MarketItem m : ChaosLedger.services().marketEngine().all()) {
            market.add(GSON.toJsonTree(Dtos.MarketItemDto.of(m)));
        }
        d.add("market", market);

        // recent transactions in default currency (20)
        JsonArray txns = new JsonArray();
        try {
            for (Transaction t : ChaosLedger.services().economy().recent(config.defaultCurrency(), 20)) {
                txns.add(GSON.toJsonTree(Dtos.TransactionDto.of(t)));
            }
        } catch (EconomyException | StorageException e) {
            d.addProperty("transactionsError", e.getMessage());
        }
        d.add("transactions", txns);

        // quests
        JsonArray quests = new JsonArray();
        for (Quest q : ChaosLedger.services().questEngine().all()) {
            quests.add(GSON.toJsonTree(Dtos.QuestDto.of(q)));
        }
        d.add("quests", quests);

        // audit (last 20)
        JsonArray audit = new JsonArray();
        for (AuditEntry e : ChaosLedger.services().audit().recent(20)) {
            audit.add(GSON.toJsonTree(Dtos.AuditEntryDto.of(e)));
        }
        d.add("audit", audit);

        return d;
    }

    // --- message framing helpers ---------------------------------------------

    private JsonObject base(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("v", PROTOCOL_VERSION);
        o.addProperty("type", type);
        o.addProperty("ts", System.currentTimeMillis());
        return o;
    }

    // --- inner client impl ----------------------------------------------------

    private final class ClientImpl extends WebSocketClient {

        ClientImpl(URI serverUri) {
            super(serverUri);
            setConnectionLostTimeout(0);
            this.setTcpNoDelay(true);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LOGGER.info("[ChaosLedger-WS] Connected to {} (HTTP {})", config.wsUrl(),
                    handshake.getHttpStatus());
            // 1. Send hello — bot validates the token, replies hello_ack or closes 4001.
            JsonObject hello = base("hello");
            JsonObject data = new JsonObject();
            data.addProperty("token", config.wsBotToken());
            data.addProperty("serverId", config.wsServerId());
            data.addProperty("modVersion", ChaosLedgerMod.MOD_VERSION);
            data.addProperty("mcVersion", "26.3");
            data.addProperty("protocol", PROTOCOL_VERSION);
            JsonArray caps = new JsonArray();
            caps.add("snapshot");
            caps.add("commands");
            caps.add("audit");
            data.add("capabilities", caps);
            hello.add("data", data);
            send(GSON.toJson(hello));
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
                String type = msg.has("type") ? msg.get("type").getAsString() : "";
                if ("hello_ack".equals(type)) {
                    LOGGER.info("[ChaosLedger-WS] Handshake acknowledged — snapshot loop starting.");
                    startSnapshotLoop();
                    return;
                }
                if ("ping".equals(type)) {
                    JsonObject pong = base("pong");
                    send(GSON.toJson(pong));
                    return;
                }
                if (type == null || type.isEmpty()) {
                    LOGGER.warn("[ChaosLedger-WS] Received message without type: {}", truncate(message));
                    return;
                }
                if (type.startsWith("request.")) {
                    handleRequest(type, msg);
                    return;
                }
                LOGGER.debug("[ChaosLedger-WS] Ignoring message type={}", type);
            } catch (Exception e) {
                LOGGER.warn("[ChaosLedger-WS] onMessage error: {} (raw={})", e.getMessage(),
                        truncate(message));
            }
        }

        private void handleRequest(String type, JsonObject msg) {
            String id = msg.has("id") ? msg.get("id").getAsString() : "";
            JsonObject data = msg.has("data") && msg.get("data").isJsonObject()
                    ? msg.getAsJsonObject("data") : new JsonObject();
            String action = type.substring("request.".length());
            // Dispatch on the HTTP executor — keeps all writes serialised.
            dispatcher.dispatch(action, data).whenComplete((result, err) -> {
                JsonObject reply = base("reply." + action);
                if (id != null && !id.isEmpty()) reply.addProperty("id", id);
                JsonObject replyData = new JsonObject();
                if (err != null) {
                    replyData.addProperty("ok", false);
                    replyData.addProperty("error", errorCode(err));
                    replyData.addProperty("message", err.getMessage() == null ? err.toString() : err.getMessage());
                } else {
                    replyData.addProperty("ok", true);
                    if (result != null) {
                        replyData.add("result", GSON.toJsonTree(result));
                    } else {
                        replyData.add("result", new JsonObject());
                    }
                }
                reply.add("data", replyData);
                try {
                    send(GSON.toJson(reply));
                } catch (Exception e) {
                    LOGGER.warn("[ChaosLedger-WS] Failed to send reply for {}: {}", id, e.getMessage());
                }
            });
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOGGER.info("[ChaosLedger-WS] Closed code={} reason='{}' remote={}", code, reason, remote);
            if (snapshotTask != null) {
                snapshotTask.cancel(false);
                snapshotTask = null;
            }
            if (code == 4001) {
                // Unauthorized — no point retrying until config changes.
                LOGGER.error("[ChaosLedger-WS] Bot rejected token (4001). Stopping. Fix wsBotToken in chaosledger.properties and restart.");
                running = false;
                return;
            }
            if (running) {
                scheduleReconnect(0);
            }
        }

        @Override
        public void onError(Exception e) {
            // onError fires for connect failures too; scheduleReconnect handles them.
            LOGGER.debug("[ChaosLedger-WS] socket error: {}", e.getMessage());
        }
    }

    private static String errorCode(Throwable err) {
        if (err instanceof EconomyException)  return "ECONOMY";
        if (err instanceof StorageException)  return "STORAGE";
        if (err instanceof QuestException)    return "QUEST";
        if (err instanceof RewardException)    return "REWARD";
        if (err instanceof IllegalArgumentException) return "BAD_REQUEST";
        return "INTERNAL";
    }

    private static String truncate(String s) {
        if (s == null) return "<null>";
        return s.length() > 240 ? s.substring(0, 240) + "…" : s;
    }
}
