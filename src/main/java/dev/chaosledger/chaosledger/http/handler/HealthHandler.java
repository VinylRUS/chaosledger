package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.http.HttpJson;

import java.io.IOException;

/**
 * {@code GET /api/health} — liveness probe used by the web panel and the
 * reverse proxy in front of it. Returns a tiny JSON document with the mod
 * version and the server time.
 */
public final class HealthHandler implements HttpHandler {

    private final String version;

    public HealthHandler(String version) {
        this.version = version;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "ok");
        obj.addProperty("mod", "ChaosLedger");
        obj.addProperty("version", version);
        obj.addProperty("serverTime", System.currentTimeMillis());
        HttpJson.ok(ex, obj);
    }
}
