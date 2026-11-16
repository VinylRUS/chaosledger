package dev.chaosledger.chaosledger.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Small helpers shared by every {@link com.sun.net.httpserver.HttpHandler}:
 * parsing the request body, looking up the {@code X-Api-Key} header, and
 * enforcing authentication before the handler logic runs.
 */
public final class HttpSupport {

    private HttpSupport() {}

    /** Read the request body as a UTF-8 string (empty if no body). */
    public static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Parse the body as a JSON object, returning an empty object on empty body. */
    public static JsonObject readJson(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        if (body.isBlank()) return new JsonObject();
        return JsonParser.parseString(body).getAsJsonObject();
    }

    public static Optional<String> apiKey(HttpExchange ex) {
        return Optional.ofNullable(ex.getRequestHeaders().getFirst("X-Api-Key"));
    }

    /** Returns {@code true} when the request passes the API key check. */
    public static boolean isAuthorised(HttpExchange ex, String expectedKey) {
        // Empty key in config = open mode (handy for dev; never recommended for production).
        if (expectedKey == null || expectedKey.isBlank()) return true;
        String supplied = apiKey(ex).orElse(null);
        return expectedKey.equals(supplied);
    }

    public static String path(HttpExchange ex) {
        return ex.getRequestURI().getPath();
    }
}
