package dev.chaosledger.chaosledger.http.ws;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Dispatches a single WS {@code request.<action>} payload to the right engine
 * method, returning the same DTO the HTTP handlers would have written.
 *
 * <p>Implementations MUST run the engine call on the same single-thread
 * executor the HTTP handlers use, so writes stay serialised against any
 * in-flight HTTP request. The CompletableFuture completes normally with the
 * DTO (or {@code null} for empty 200 responses) and exceptionally with the
 * domain exception ({@code EconomyException} etc.) so the WS client can map
 * it to the {@code error} field of the reply.</p>
 */
public interface WsDispatcher {

    /**
     * @param action the part after {@code request.} (e.g. {@code "pay"},
     *               {@code "market_buy"}, {@code "quest_start"})
     * @param data   the request payload as parsed JSON (matches the request DTO
     *               of the corresponding HTTP route)
     * @return a future resolving to the DTO to serialise into {@code reply.result}
     */
    CompletableFuture<Object> dispatch(String action, JsonObject data);
}
