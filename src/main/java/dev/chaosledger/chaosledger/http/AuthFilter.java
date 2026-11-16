package dev.chaosledger.chaosledger.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Enforces the configured API key for every request except {@code /api/health}
 * and CORS pre-flight ({@code OPTIONS}).
 *
 * <p>If the config has an empty key the filter is a no-op — handy for local
 * development but never recommended for a public server.</p>
 */
public final class AuthFilter extends Filter {

    private final String expectedKey;

    public AuthFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    public String description() {
        return "ChaosLedger API key filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals("/api/health") || "OPTIONS".equals(method)) {
            chain.doFilter(exchange);
            return;
        }
        boolean open = expectedKey == null || expectedKey.isBlank();
        if (open || expectedKey.equals(exchange.getRequestHeaders().getFirst("X-Api-Key"))) {
            chain.doFilter(exchange);
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("error", "Unauthorized");
        obj.addProperty("status", 401);
        HttpJson.writeJson(exchange, 401, obj);
    }
}
