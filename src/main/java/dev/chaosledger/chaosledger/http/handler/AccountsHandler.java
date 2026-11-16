package dev.chaosledger.chaosledger.http.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.economy.EconomyException;
import dev.chaosledger.chaosledger.economy.EconomyEngine;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.storage.StorageException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static dev.chaosledger.chaosledger.http.dto.Dtos.AccountDto;

/**
 * {@code GET /api/accounts}                 — list every account.
 * {@code GET /api/accounts/{uuid} }         — list every account for a player.
 * {@code GET /api/accounts/{uuid}/{code}}   — a single account.
 * {@code GET /api/accounts/top/{code}?limit=N}
 */
public final class AccountsHandler implements HttpHandler {

    private final EconomyEngine economy;

    public AccountsHandler(EconomyEngine economy) {
        this.economy = economy;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = HttpSupport.path(ex);
            String[] parts = path.substring("/api/accounts".length()).split("/");
            // ["", "{uuid}", "{code}"] or ["", "top", "{code}"]

            if (parts.length == 1) {
                HttpJson.error(ex, 501, "Use /api/accounts/<uuid> for a player's accounts");
                return;
            }

            if ("top".equals(parts[1]) && parts.length >= 3) {
                String code = parts[2];
                int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 10);
                List<AccountDto> top = economy.top(code, limit).stream().map(AccountDto::of).toList();
                HttpJson.ok(ex, top);
                return;
            }

            UUID uuid = UUID.fromString(parts[1]);
            if (parts.length == 2) {
                List<AccountDto> accounts = economy.accountsOf(uuid).stream().map(AccountDto::of).toList();
                HttpJson.ok(ex, accounts);
                return;
            }
            if (parts.length == 3) {
                String code = parts[2];
                AccountDto account = AccountDto.of(economy.accountOf(uuid, uuid.toString(), code));
                HttpJson.ok(ex, account);
                return;
            }
            HttpJson.error(ex, 404, "Unknown path: " + path);
        } catch (EconomyException | StorageException e) {
            HttpJson.error(ex, 400, e.getMessage());
        } catch (IllegalArgumentException e) {
            HttpJson.error(ex, 400, "Bad UUID: " + e.getMessage());
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
