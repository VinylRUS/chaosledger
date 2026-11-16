package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.audit.AuditLogger;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.http.dto.Dtos.AuditEntryDto;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/audit?limit=N}                — recent audit entries.
 * {@code GET /api/audit/{uuid}?limit=N}         — recent entries for an actor.
 */
public final class AuditHandler implements HttpHandler {

    private final AuditLogger audit;

    public AuditHandler(AuditLogger audit) {
        this.audit = audit;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = HttpSupport.path(ex);
        String method = ex.getRequestMethod();
        if (method.equals("OPTIONS")) {
            HttpJson.ok(ex, new JsonObject());
            return;
        }
        if (!method.equals("GET")) {
            HttpJson.error(ex, 405, "Method not allowed");
            return;
        }
        if (path.equals("/api/audit")) {
            int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 50);
            List<AuditEntryDto> rows = audit.recent(limit).stream().map(AuditEntryDto::of).toList();
            HttpJson.ok(ex, rows);
            return;
        }
        String[] parts = path.substring("/api/audit".length()).split("/");
        if (parts.length == 2) {
            UUID uuid = UUID.fromString(parts[1]);
            int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 50);
            List<AuditEntryDto> rows = audit.forActor(uuid, limit).stream().map(AuditEntryDto::of).toList();
            HttpJson.ok(ex, rows);
            return;
        }
        HttpJson.error(ex, 404, "Unknown path: " + path);
    }

    private static int parseInt(String query, String key, int fallback) {
        if (query == null) return fallback;
        for (String kv : query.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2 && p[0].equals(key)) {
                try { return Integer.parseInt(p[1]); } catch (NumberFormatException ignore) { return fallback; }
            }
        }
        return fallback;
    }
}
