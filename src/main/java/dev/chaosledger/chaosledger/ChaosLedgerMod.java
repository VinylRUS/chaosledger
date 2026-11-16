package dev.chaosledger.chaosledger;

import dev.chaosledger.chaosledger.command.ChaosLedgerCommands;
import dev.chaosledger.chaosledger.http.HttpApiServer;
import dev.chaosledger.chaosledger.item.ModItems;
import dev.chaosledger.chaosledger.item.ServerItemDelivery;
import dev.chaosledger.chaosledger.reward.ItemDelivery;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Fabric entrypoint for the ChaosLedger mod.
 *
 * <p>Wires together the storage layer, the engines, the HTTP API and the
 * in-game commands on server start, and tears them all down on server stop.</p>
 */
public final class ChaosLedgerMod implements ModInitializer {

    public static final String MOD_ID = "chaosledger";
    public static final String MOD_VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HttpApiServer httpApi;

    @Override
    public void onInitialize() {
        LOGGER.info("[ChaosLedger] Booting — authoritatively counting every coin.");

        ModItems.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ChaosLedgerCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
    }

    private void onServerStarting(MinecraftServer server) {
        ChaosLedgerConfig config = loadConfig();
        ItemDelivery delivery = new ServerItemDelivery(uuid -> server.getPlayerList().getPlayer(uuid));
        ChaosLedgerServices services = new ChaosLedgerServices(config, delivery);
        ChaosLedger.init(config, services);

        if (config.httpEnabled()) {
            try {
                httpApi = new HttpApiServer(config, services, MOD_VERSION);
                httpApi.start();
                LOGGER.info("[ChaosLedger] HTTP API on http://{}:{}/api/health",
                        config.httpBindHost(), config.httpPort());
                httpApi.routes().forEach(r -> LOGGER.info("[ChaosLedger]   " + r));
            } catch (IOException e) {
                LOGGER.error("[ChaosLedger] Failed to start HTTP API", e);
            }
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (httpApi != null) {
            httpApi.stop();
            httpApi = null;
        }
        ChaosLedger.shutdown();
        LOGGER.info("[ChaosLedger] Shut down.");
    }

    private void onEndServerTick(MinecraftServer server) {
        // Recompute market prices every 600 ticks (~30s) so signals decay and prices drift.
        if (server.getTickCount() % 600 == 0) {
            try {
                ChaosLedger.services().marketEngine().recomputeAll();
            } catch (RuntimeException e) {
                LOGGER.warn("[ChaosLedger] Market recompute failed", e);
            }
        }
    }

    private ChaosLedgerConfig loadConfig() {
        // Framework-stable game directory (mapping-independent). On a dedicated server
        // this is the server root, equivalent to the old Yarn server.getRunPath().
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path dir = gameDir.resolve("config").resolve(MOD_ID);
        Path file = dir.resolve("chaosledger.properties");
        try {
            Files.createDirectories(dir);
            Properties props = new Properties();
            if (Files.exists(file)) {
                try (var in = Files.newBufferedReader(file)) {
                    props.load(in);
                }
            } else {
                ChaosLedgerConfig defaults = new ChaosLedgerConfig();
                defaults.setDatabaseFile(gameDir.resolve("chaosledger.db"));
                props = defaults.asProperties();
                try (var out = Files.newBufferedWriter(file)) {
                    props.store(out, "ChaosLedger configuration. Edit and restart the server to apply.");
                }
            }
            return new ChaosLedgerConfig().loadFrom(props);
        } catch (IOException e) {
            LOGGER.warn("[ChaosLedger] Failed to load config, using defaults", e);
            return new ChaosLedgerConfig();
        }
    }
}
