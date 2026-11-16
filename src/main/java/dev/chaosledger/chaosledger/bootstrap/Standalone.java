package dev.chaosledger.chaosledger.bootstrap;

import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.ChaosLedgerConfig;
import dev.chaosledger.chaosledger.ChaosLedgerMod;
import dev.chaosledger.chaosledger.ChaosLedgerServices;
import dev.chaosledger.chaosledger.http.HttpApiServer;
import dev.chaosledger.chaosledger.reward.ItemDelivery;

import java.io.IOException;
import java.nio.file.Path;
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
        ChaosLedgerConfig config = new ChaosLedgerConfig()
                .setDatabaseFile(Path.of("chaosledger.db"))
                .setHttpEnabled(true);

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
        System.out.println("[ChaosLedger] Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
}
