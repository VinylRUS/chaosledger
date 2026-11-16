package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.economy.EconomyException;
import dev.chaosledger.chaosledger.economy.MarketEngine;
import dev.chaosledger.chaosledger.economy.MarketItem;
import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.http.dto.Dtos.DefineMarketItemRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.MarketItemDto;
import dev.chaosledger.chaosledger.http.dto.Dtos.MarketTradeRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.PricePointDto;
import dev.chaosledger.chaosledger.storage.StorageException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * {@code GET    /api/market}                       — list all market items.
 * {@code GET    /api/market/{itemId}}              — single item.
 * {@code POST   /api/market}                       — define / update a market item.
 * {@code POST   /api/market/buy}                    — buy items.
 * {@code POST   /api/market/sell}                   — sell items.
 * {@code GET    /api/market/{itemId}/history?limit=N}
 */
public final class MarketHandler implements HttpHandler {

    private final MarketEngine market;

    public MarketHandler(MarketEngine market) {
        this.market = market;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = HttpSupport.path(ex);
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) {
                HttpJson.ok(ex, new JsonObject());
                return;
            }
            if (path.equals("/api/market") && method.equals("GET")) {
                List<MarketItemDto> items = market.all().stream().map(MarketItemDto::of).toList();
                HttpJson.ok(ex, items);
                return;
            }
            if (path.equals("/api/market") && method.equals("POST")) {
                DefineMarketItemRequest req = GSON.fromJson(HttpSupport.readBody(ex), DefineMarketItemRequest.class);
                Money base = new Money(req.basePrice(), req.currency());
                MarketItem item = new MarketItem(req.itemId(), req.displayName(), base,
                        req.stock(), 0d, 0d, base, System.currentTimeMillis());
                market.define(item);
                HttpJson.created(ex, MarketItemDto.of(item));
                return;
            }
            if (path.equals("/api/market/buy") && method.equals("POST")) {
                MarketTradeRequest req = GSON.fromJson(HttpSupport.readBody(ex), MarketTradeRequest.class);
                HttpJson.ok(ex, MarketItemDto.of(market.buy(req.playerUuid(), req.playerName(), req.itemId(), req.quantity())));
                return;
            }
            if (path.equals("/api/market/sell") && method.equals("POST")) {
                MarketTradeRequest req = GSON.fromJson(HttpSupport.readBody(ex), MarketTradeRequest.class);
                HttpJson.ok(ex, MarketItemDto.of(market.sell(req.playerUuid(), req.playerName(), req.itemId(), req.quantity())));
                return;
            }
            String[] parts = path.substring("/api/market".length()).split("/");
            if (parts.length == 2 && method.equals("GET")) {
                String itemId = parts[1];
                HttpJson.ok(ex, MarketItemDto.of(market.item(itemId)
                        .orElseThrow(() -> new EconomyException("Unknown item: " + itemId))));
                return;
            }
            if (parts.length == 3 && "history".equals(parts[2]) && method.equals("GET")) {
                String itemId = parts[1];
                int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 50);
                double[][] rows = market.history(itemId, limit).toArray(double[][]::new);
                List<PricePointDto> points = Arrays.stream(rows)
                        .map(r -> new PricePointDto(r[0], (long) r[1]))
                        .toList();
                HttpJson.ok(ex, points);
                return;
            }
            HttpJson.error(ex, 404, "Unknown path: " + path);
        } catch (EconomyException | StorageException e) {
            HttpJson.error(ex, 400, e.getMessage());
        }
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
