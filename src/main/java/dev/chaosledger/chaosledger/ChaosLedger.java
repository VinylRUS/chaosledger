package dev.chaosledger.chaosledger;

/**
 * Process-wide handle for the running ChaosLedger instance.
 *
 * <p>The Fabric entrypoint sets the singleton on server start and clears it on
 * shutdown. The HTTP layer and other non-Minecraft code read the singleton to
 * reach the engines without threading them through every call site.</p>
 */
public final class ChaosLedger {

    private static volatile ChaosLedgerConfig config;
    private static volatile ChaosLedgerServices services;

    private ChaosLedger() {}

    public static void init(ChaosLedgerConfig config, ChaosLedgerServices services) {
        ChaosLedger.config = config;
        ChaosLedger.services = services;
    }

    public static ChaosLedgerConfig config() {
        ChaosLedgerConfig c = config;
        if (c == null) {
            throw new IllegalStateException("ChaosLedger is not initialised yet");
        }
        return c;
    }

    public static ChaosLedgerServices services() {
        ChaosLedgerServices s = services;
        if (s == null) {
            throw new IllegalStateException("ChaosLedger services are not initialised yet");
        }
        return s;
    }

    public static void shutdown() {
        if (services != null) services.close();
        services = null;
        config = null;
    }
}
