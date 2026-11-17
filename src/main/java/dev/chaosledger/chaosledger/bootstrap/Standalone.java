package dev.chaosledger.chaosledger.bootstrap;

import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.ChaosLedgerConfig;
import dev.chaosledger.chaosledger.ChaosLedgerMod;
import dev.chaosledger.chaosledger.ChaosLedgerServices;
import dev.chaosledger.chaosledger.http.HttpApiServer;
import dev.chaosledger.chaosledger.http.ws.WsDispatcherImpl;
import dev.chaosledger.chaosledger.http.ws.WsTunnelClient;
import dev.chaosledger.chaosledger.reward.ItemDelivery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * Standalone runner for ChaosLedger's storage + HTTP layers without booting
 * Minecraft.
 *
 * <p>Useful for smoke-testing the API against the real SQLite database during
 * development. Run with {@code java -jar chaosledger-<version>.jar}.</p>
 */
public final class Standalone {

    public static void main(String[] args) throws IOException {
        // v0.2.0: load full config (including WS fields) from
        // ./config/chaosledger/chaosledger.properties if present, so the
        // standalone runner exercises the same reverse-tunnel code path as
        // the Fabric entrypoint.
        ChaosLedgerConfig config = loadStandaloneConfig();

        ItemDelivery noopDelivery = new ItemDelivery() {
            @Override
            public boolean deliver(UUID playerUuid, String itemId, int count) {
                System.out.println("[noop] Would deliver " + count + "x " + itemId + " to " + playerUuid);
                return true;
            }
        };

        ChaosLedgerServices services = new ChaosLedgerServices(config, noopDelivery);
        ChaosLedger.init(config, services);

        HttpApiServer http = new HttpApiServer(config, services, ChaosLedgerMod.MOD_VERSION);
        http.start();
        System.out.println("[ChaosLedger] Standalone HTTP API listening on http://"
                + config.httpBindHost() + ":" + config.httpPort() + "/api/health");

        // v0.2.0: also start the WS reverse-tunnel if enabled — the dispatcher
        // reuses the HTTP single-thread executor so writes stay serialised.
        WsTunnelClient ws = new WsTunnelClient(config, new WsDispatcherImpl(http));
        ws.start();

        System.out.println("[ChaosLedger] Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ws.stop();
            http.stop();
            services.close();
            System.out.println("[ChaosLedger] Shut down.");
        }));

        // Block forever so the daemon HTTP threads stay alive.
        Thread.currentThread().setDaemon(false);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Load config from {@code ./config/chaosledger/chaosledger.properties} if
     * present, otherwise fall back to a minimal default (HTTP enabled, no WS).
     */
    private static ChaosLedgerConfig loadStandaloneConfig() {
        Path file = Path.of("config", "chaosledger", "chaosledger.properties");
        ChaosLedgerConfig config = new ChaosLedgerConfig()
                .setDatabaseFile(Path.of("chaosledger.db"))
                .setHttpEnabled(true);
        if (Files.exists(file)) {
            Properties props = new Properties();
            try (var in = Files.newBufferedReader(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("[ChaosLedger] Failed to read " + file + ": " + e.getMessage());
                return config;
            }
            return config.loadFrom(props);
        }
        return config;
    }
}
