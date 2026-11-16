package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.economy.EconomyException;
import dev.chaosledger.chaosledger.economy.EconomyEngine;
import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.http.dto.Dtos.PayRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.GrantRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.ChargeRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.TransactionDto;
import dev.chaosledger.chaosledger.storage.StorageException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * {@code POST   /api/transactions/pay}            — transfer money between players.
 * {@code POST   /api/transactions/grant}          — mint money to a player.
 * {@code POST   /api/transactions/charge}         — burn money from a player.
 * {@code GET    /api/transactions/{uuid}?limit=N} — player's recent transactions.
 * {@code GET    /api/transactions/recent/{code}?limit=N}
 */
public final class TransactionsHandler implements HttpHandler {

    private final EconomyEngine economy;

    public TransactionsHandler(EconomyEngine economy) {
        this.economy = economy;
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
            if (path.equals("/api/transactions/pay") && method.equals("POST")) {
                PayRequest req = GSON.fromJson(HttpSupport.readBody(ex), PayRequest.class);
                Money amount = new Money(req.amount(), req.currency());
                TransactionDto tx = TransactionDto.of(economy.pay(
                        req.fromUuid(), req.fromName(), req.toUuid(), req.toName(),
                        req.currency(), amount, req.reason()));
                HttpJson.created(ex, tx);
                return;
            }
            if (path.equals("/api/transactions/grant") && method.equals("POST")) {
                GrantRequest req = GSON.fromJson(HttpSupport.readBody(ex), GrantRequest.class);
                Money amount = new Money(req.amount(), req.currency());
                TransactionDto tx = TransactionDto.of(
                        economy.grant(req.playerUuid(), req.playerName(), req.currency(), amount, req.reason()));
                HttpJson.created(ex, tx);
                return;
            }
            if (path.equals("/api/transactions/charge") && method.equals("POST")) {
                ChargeRequest req = GSON.fromJson(HttpSupport.readBody(ex), ChargeRequest.class);
                Money amount = new Money(req.amount(), req.currency());
                String category = req.category() == null ? "ADMIN" : req.category();
                TransactionDto tx = TransactionDto.of(economy.charge(
                        req.playerUuid(), req.playerName(), req.currency(), amount, req.reason(), category));
                HttpJson.created(ex, tx);
                return;
            }
            String[] parts = path.substring("/api/transactions".length()).split("/");
            if (parts.length == 2 && method.equals("GET")) {
                UUID uuid = UUID.fromString(parts[1]);
                int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 20);
                List<TransactionDto> txs = economy.history(uuid, limit).stream()
                        .map(TransactionDto::of).toList();
                HttpJson.ok(ex, txs);
                return;
            }
            if (parts.length == 3 && "recent".equals(parts[1]) && method.equals("GET")) {
                String code = parts[2];
                int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 20);
                List<TransactionDto> txs = economy.recent(code, limit).stream()
                        .map(TransactionDto::of).toList();
                HttpJson.ok(ex, txs);
                return;
            }
            HttpJson.error(ex, 404, "Unknown path: " + path);
        } catch (EconomyException | StorageException e) {
            HttpJson.error(ex, 400, e.getMessage());
        } catch (IllegalArgumentException e) {
            HttpJson.error(ex, 400, "Bad request: " + e.getMessage());
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
