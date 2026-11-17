package dev.chaosledger.chaosledger.http;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.chaosledger.chaosledger.ChaosLedgerConfig;
import dev.chaosledger.chaosledger.ChaosLedgerServices;
import dev.chaosledger.chaosledger.http.handler.AccountsHandler;
import dev.chaosledger.chaosledger.http.handler.AuditHandler;
import dev.chaosledger.chaosledger.http.handler.CurrenciesHandler;
import dev.chaosledger.chaosledger.http.handler.HealthHandler;
import dev.chaosledger.chaosledger.http.handler.MarketHandler;
import dev.chaosledger.chaosledger.http.handler.QuestsHandler;
import dev.chaosledger.chaosledger.http.handler.RewardsHandler;
import dev.chaosledger.chaosledger.http.handler.TransactionsHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP API for ChaosLedger.
 *
 * <p>One {@link HttpServer} instance is bound to {@link ChaosLedgerConfig#httpBindHost()}
 * on {@link ChaosLedgerConfig#httpPort()} with a fixed thread pool. Every context
 * mounts under {@code /api/*} and is wrapped by the {@link AuthFilter}.</p>
 *
 * <p>Writes (pay, grant, charge, market trades, quest mutations) must be executed
 * on the Minecraft server thread so they are transactional against the rest of
 * the simulation. For the first version the handlers invoke the engines
 * directly — the engines themselves are single-writer safe on a single-threaded
 * executor.</p>
 */
public final class HttpApiServer {

    private final ChaosLedgerConfig config;
    private final ChaosLedgerServices services;
    private final String version;
    private HttpServer server;
    private Executor executor;

    public HttpApiServer(ChaosLedgerConfig config, ChaosLedgerServices services, String version) {
        this.config = config;
        this.services = services;
        this.version = version;
    }

    public synchronized void start() throws IOException {
        if (server != null) return;
        InetSocketAddress addr = new InetSocketAddress(config.httpBindHost(), config.httpPort());
        server = HttpServer.create(addr, 0);
        AuthFilter auth = new AuthFilter(config.httpApiKey());

        mount("/api/health",        new HealthHandler(version), auth);
        mount("/api/currencies",    new CurrenciesHandler(services.economy()), auth);
        mount("/api/accounts",      new AccountsHandler(services.economy()), auth);
        mount("/api/transactions",  new TransactionsHandler(services.economy()), auth);
        mount("/api/market",        new MarketHandler(services.marketEngine()), auth);
        mount("/api/quests",        new QuestsHandler(services.questEngine()), auth);
        mount("/api/rewards",       new RewardsHandler(services.rewardEngine()), auth);
        mount("/api/audit",         new AuditHandler(services.audit()), auth);

        // Single-thread executor so that writes serialise against the server thread.
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChaosLedger-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor instanceof AutoCloseable c) {
            try { c.close(); } catch (Exception ignore) {}
        }
        executor = null;
    }

    /**
     * The single-thread executor that serialises all engine writes.
     *
     * <p>v0.2.0: exposed so the WebSocket reverse-tunnel client can dispatch
     * bot commands on the <em>same</em> thread the HTTP handlers use. The
     * engines are not internally synchronised — they rely on this executor
     * being single-threaded to keep writes atomic. When the WS client submits
     * a command here, it lands in the same queue as HTTP POSTs and is executed
     * strictly in order, after any in-flight HTTP request.</p>
     *
     * <p>Returns {@code null} if the server is stopped (so the WS dispatcher
     * falls back to its own single-thread executor).</p>
     */
    public synchronized Executor executor() {
        return executor;
    }

    public List<String> routes() {
        return List.of(
                "GET    /api/health",
                "GET    /api/currencies",
                "POST   /api/currencies",
                "GET    /api/accounts/top/{code}?limit=N",
                "GET    /api/accounts/{uuid}",
                "GET    /api/accounts/{uuid}/{code}",
                "POST   /api/transactions/pay",
                "POST   /api/transactions/grant",
                "POST   /api/transactions/charge",
                "GET    /api/transactions/{uuid}?limit=N",
                "GET    /api/transactions/recent/{code}?limit=N",
                "GET    /api/market",
                "POST   /api/market",
                "GET    /api/market/{itemId}",
                "POST   /api/market/buy",
                "POST   /api/market/sell",
                "GET    /api/market/{itemId}/history?limit=N",
                "GET    /api/quests",
                "POST   /api/quests",
                "DELETE /api/quests/{id}",
                "GET    /api/quests/progress/{uuid}",
                "GET    /api/quests/progress/{uuid}/{id}",
                "POST   /api/quests/available",
                "POST   /api/quests/start",
                "POST   /api/quests/claim",
                "POST   /api/quests/fail",
                "POST   /api/quests/event",
                "GET    /api/rewards/{uuid}?limit=N",
                "POST   /api/rewards/grant",
                "GET    /api/audit?limit=N",
                "GET    /api/audit/{uuid}?limit=N");
    }

    private void mount(String path, HttpHandler handler, AuthFilter auth) {
        var ctx = server.createContext(path, handler);
        ctx.getFilters().add(auth);
    }
}
