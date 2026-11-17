package dev.chaosledger.chaosledger;

import java.nio.file.Path;
import java.util.Properties;

/**
 * Mod-wide configuration. For the first version the config is a small set of
 * typed fields backed by a {@code chaosledger.properties} file next to the database.
 *
 * <p>The fields are intentionally simple and use sensible defaults so that the
 * mod boots out of the box. Field names map 1:1 to property keys.</p>
 */
public final class ChaosLedgerConfig {

    private String defaultCurrency = "CC";
    private double startingBalance  = 100d;
    private Path   databaseFile     = Path.of("chaosledger.db");
    private int    httpPort         = 8775;
    private String httpBindHost     = "127.0.0.1";
    private boolean httpEnabled     = true;
    private String httpApiKey       = "";
    private double priceElasticity  = 0.05d;
    private double priceMinFactor   = 0.25d;
    private double priceMaxFactor   = 4.00d;
    private boolean allowNegativeBalance = false;

    // --- v0.2.0: WebSocket reverse-tunnel (mod → bot outbound) ----------------
    // See WS_TUNNEL_PROTOCOL.md. Disabled by default so existing installs are
    // unaffected; the bot panel turns it on by setting `wsUrl` + `wsBotToken`
    // in chaosledger.properties (the operator copies these from /adminpanel).
    private boolean wsEnabled            = false;
    private String  wsUrl               = "";          // wss://bot-host/ws/chaosledger
    private String  wsBotToken          = "";          // shared secret, mirrors CL_WS_TOKEN in bot DB
    private String  wsServerId          = "";          // human label, e.g. "dibilki"
    private int     wsSnapshotIntervalSec = 10;
    private int     wsReconnectMinMs    = 1000;
    private int     wsReconnectMaxMs    = 30000;
    private int     wsRequestTimeoutMs  = 5000;

    public ChaosLedgerConfig() {}

    public ChaosLedgerConfig loadFrom(Properties props) {
        if (props == null) return this;
        defaultCurrency      = props.getProperty("defaultCurrency", defaultCurrency);
        startingBalance       = parse(props.getProperty("startingBalance"),       startingBalance);
        databaseFile          = Path.of(props.getProperty("databaseFile",          databaseFile.toString()));
        httpPort              = (int) parse(props.getProperty("httpPort"),         httpPort);
        httpBindHost          = props.getProperty("httpBindHost",                  httpBindHost);
        httpEnabled           = Boolean.parseBoolean(props.getProperty("httpEnabled", String.valueOf(httpEnabled)));
        httpApiKey            = props.getProperty("httpApiKey", httpApiKey);
        priceElasticity       = parse(props.getProperty("priceElasticity"),       priceElasticity);
        priceMinFactor        = parse(props.getProperty("priceMinFactor"),        priceMinFactor);
        priceMaxFactor        = parse(props.getProperty("priceMaxFactor"),        priceMaxFactor);
        allowNegativeBalance  = Boolean.parseBoolean(props.getProperty("allowNegativeBalance", String.valueOf(allowNegativeBalance)));
        // v0.2.0: WebSocket reverse-tunnel fields.
        wsEnabled             = Boolean.parseBoolean(props.getProperty("wsEnabled", String.valueOf(wsEnabled)));
        wsUrl                 = props.getProperty("wsUrl", wsUrl);
        wsBotToken            = props.getProperty("wsBotToken", wsBotToken);
        wsServerId            = props.getProperty("wsServerId", wsServerId);
        wsSnapshotIntervalSec = (int) parse(props.getProperty("wsSnapshotIntervalSec"), wsSnapshotIntervalSec);
        wsReconnectMinMs      = (int) parse(props.getProperty("wsReconnectMinMs"),    wsReconnectMinMs);
        wsReconnectMaxMs      = (int) parse(props.getProperty("wsReconnectMaxMs"),    wsReconnectMaxMs);
        wsRequestTimeoutMs    = (int) parse(props.getProperty("wsRequestTimeoutMs"),  wsRequestTimeoutMs);
        return this;
    }

    public Properties asProperties() {
        Properties p = new Properties();
        p.setProperty("defaultCurrency",     defaultCurrency);
        p.setProperty("startingBalance",    Double.toString(startingBalance));
        p.setProperty("databaseFile",        databaseFile.toString());
        p.setProperty("httpPort",           Integer.toString(httpPort));
        p.setProperty("httpBindHost",        httpBindHost);
        p.setProperty("httpEnabled",        Boolean.toString(httpEnabled));
        p.setProperty("httpApiKey",          httpApiKey);
        p.setProperty("priceElasticity",     Double.toString(priceElasticity));
        p.setProperty("priceMinFactor",     Double.toString(priceMinFactor));
        p.setProperty("priceMaxFactor",     Double.toString(priceMaxFactor));
        p.setProperty("allowNegativeBalance", Boolean.toString(allowNegativeBalance));
        p.setProperty("wsEnabled",            Boolean.toString(wsEnabled));
        p.setProperty("wsUrl",                wsUrl);
        p.setProperty("wsBotToken",           wsBotToken);
        p.setProperty("wsServerId",            wsServerId);
        p.setProperty("wsSnapshotIntervalSec", Integer.toString(wsSnapshotIntervalSec));
        p.setProperty("wsReconnectMinMs",      Integer.toString(wsReconnectMinMs));
        p.setProperty("wsReconnectMaxMs",      Integer.toString(wsReconnectMaxMs));
        p.setProperty("wsRequestTimeoutMs",    Integer.toString(wsRequestTimeoutMs));
        return p;
    }

    private static double parse(String v, double fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    public String defaultCurrency()      { return defaultCurrency; }
    public double startingBalance()      { return startingBalance; }
    public Path   databaseFile()         { return databaseFile; }
    public int    httpPort()             { return httpPort; }
    public String httpBindHost()         { return httpBindHost; }
    public boolean httpEnabled()         { return httpEnabled; }
    public String httpApiKey()           { return httpApiKey; }
    public double priceElasticity()      { return priceElasticity; }
    public double priceMinFactor()       { return priceMinFactor; }
    public double priceMaxFactor()       { return priceMaxFactor; }
    public boolean allowNegativeBalance(){ return allowNegativeBalance; }

    // v0.2.0: WebSocket reverse-tunnel.
    public boolean wsEnabled()            { return wsEnabled; }
    public String  wsUrl()               { return wsUrl; }
    public String  wsBotToken()          { return wsBotToken; }
    public String  wsServerId()          { return wsServerId; }
    public int     wsSnapshotIntervalSec(){ return wsSnapshotIntervalSec; }
    public int     wsReconnectMinMs()    { return wsReconnectMinMs; }
    public int     wsReconnectMaxMs()    { return wsReconnectMaxMs; }
    public int     wsRequestTimeoutMs()  { return wsRequestTimeoutMs; }

    public ChaosLedgerConfig setDefaultCurrency(String v)     { this.defaultCurrency = v; return this; }
    public ChaosLedgerConfig setStartingBalance(double v)     { this.startingBalance = v; return this; }
    public ChaosLedgerConfig setDatabaseFile(Path v)          { this.databaseFile = v; return this; }
    public ChaosLedgerConfig setHttpPort(int v)               { this.httpPort = v; return this; }
    public ChaosLedgerConfig setHttpBindHost(String v)        { this.httpBindHost = v; return this; }
    public ChaosLedgerConfig setHttpEnabled(boolean v)        { this.httpEnabled = v; return this; }
    public ChaosLedgerConfig setHttpApiKey(String v)          { this.httpApiKey = v; return this; }
    public ChaosLedgerConfig setPriceElasticity(double v)     { this.priceElasticity = v; return this; }
    public ChaosLedgerConfig setPriceMinFactor(double v)      { this.priceMinFactor = v; return this; }
    public ChaosLedgerConfig setPriceMaxFactor(double v)      { this.priceMaxFactor = v; return this; }
    public ChaosLedgerConfig setAllowNegativeBalance(boolean v){ this.allowNegativeBalance = v; return this; }

    // v0.2.0: WebSocket reverse-tunnel.
    public ChaosLedgerConfig setWsEnabled(boolean v)         { this.wsEnabled = v; return this; }
    public ChaosLedgerConfig setWsUrl(String v)              { this.wsUrl = v; return this; }
    public ChaosLedgerConfig setWsBotToken(String v)         { this.wsBotToken = v; return this; }
    public ChaosLedgerConfig setWsServerId(String v)         { this.wsServerId = v; return this; }
    public ChaosLedgerConfig setWsSnapshotIntervalSec(int v){ this.wsSnapshotIntervalSec = v; return this; }
    public ChaosLedgerConfig setWsReconnectMinMs(int v)     { this.wsReconnectMinMs = v; return this; }
    public ChaosLedgerConfig setWsReconnectMaxMs(int v)     { this.wsReconnectMaxMs = v; return this; }
    public ChaosLedgerConfig setWsRequestTimeoutMs(int v)   { this.wsRequestTimeoutMs = v; return this; }
}
