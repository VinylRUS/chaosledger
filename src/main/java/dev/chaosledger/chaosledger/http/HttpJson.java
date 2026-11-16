package dev.chaosledger.chaosledger.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Helpers for writing JSON responses back to {@link HttpExchange}.
 */
public final class HttpJson {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HttpJson() {}

    public static void writeJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = (body instanceof String s ? s : GSON.toJson(body)).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Api-Key");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void error(HttpExchange ex, int status, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        obj.addProperty("status", status);
        writeJson(ex, status, obj);
    }

    public static void ok(HttpExchange ex, Object body) throws IOException {
        writeJson(ex, HttpURLConnection.HTTP_OK, body);
    }

    public static void created(HttpExchange ex, Object body) throws IOException {
        writeJson(ex, HttpURLConnection.HTTP_CREATED, body);
    }
}
