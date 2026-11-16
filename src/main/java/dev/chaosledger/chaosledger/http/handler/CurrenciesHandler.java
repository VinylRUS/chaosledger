package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.economy.Currency;
import dev.chaosledger.chaosledger.economy.EconomyEngine;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.http.dto.Dtos.CurrencyDto;

import java.io.IOException;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * {@code GET  /api/currencies}        — list currencies.
 * {@code POST /api/currencies}        — define / update a currency.
 */
public final class CurrenciesHandler implements HttpHandler {

    private final EconomyEngine economy;

    public CurrenciesHandler(EconomyEngine economy) {
        this.economy = economy;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = HttpSupport.path(ex);
        String method = ex.getRequestMethod();
        if (method.equals("OPTIONS")) {
            HttpJson.ok(ex, new JsonObject());
            return;
        }
        if (path.equals("/api/currencies") && method.equals("GET")) {
            HttpJson.ok(ex, economy.currencies().stream().map(CurrencyDto::of).toList());
            return;
        }
        if (path.equals("/api/currencies") && method.equals("POST")) {
            CurrencyDto dto = GSON.fromJson(HttpSupport.readBody(ex), CurrencyDto.class);
            Currency currency = new Currency(dto.code(), dto.displayName(), dto.symbol(), dto.defaultStarting());
            economy.defineCurrency(currency);
            HttpJson.created(ex, CurrencyDto.of(currency));
            return;
        }
        HttpJson.error(ex, 404, "Unknown path: " + path);
    }
}
