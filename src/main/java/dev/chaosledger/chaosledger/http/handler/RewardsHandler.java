package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.http.dto.Dtos.RewardDto;
import dev.chaosledger.chaosledger.reward.Reward;
import dev.chaosledger.chaosledger.reward.RewardEngine;
import dev.chaosledger.chaosledger.reward.RewardException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * {@code GET  /api/rewards/{uuid}?limit=N}   — recent granted rewards for a player.
 * {@code POST /api/rewards/grant}            — grant an ad-hoc reward (admin).
 */
public final class RewardsHandler implements HttpHandler {

    private final RewardEngine rewards;

    public RewardsHandler(RewardEngine rewards) {
        this.rewards = rewards;
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
            if (path.equals("/api/rewards/grant") && method.equals("POST")) {
                RewardGrantRequest req = GSON.fromJson(HttpSupport.readBody(ex), RewardGrantRequest.class);
                Reward reward = toReward(req.reward());
                rewards.grant(req.playerUuid(), req.playerName(), reward, req.questId());
                HttpJson.ok(ex, new JsonObject());
                return;
            }
            String[] parts = path.substring("/api/rewards".length()).split("/");
            if (parts.length == 2 && method.equals("GET")) {
                UUID uuid = UUID.fromString(parts[1]);
                int limit = parseInt(ex.getRequestURI().getQuery(), "limit", 20);
                List<RewardDto> rows = rewards.history(uuid, limit).stream().map(RewardDto::of).toList();
                HttpJson.ok(ex, rows);
                return;
            }
            HttpJson.error(ex, 404, "Unknown path: " + path);
        } catch (RewardException e) {
            HttpJson.error(ex, 400, e.getMessage());
        } catch (IllegalArgumentException e) {
            HttpJson.error(ex, 400, "Bad request: " + e.getMessage());
        }
    }

    private Reward toReward(RewardDto dto) {
        return switch (Reward.Type.valueOf(dto.type())) {
            case MONEY -> Reward.money(new dev.chaosledger.chaosledger.economy.Money(
                    dto.money().amount(), dto.money().currency()));
            case ITEM  -> Reward.item(dto.itemId(), dto.count());
        };
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

    /** Local request DTO — quest_id is optional for ad-hoc grants. */
    public record RewardGrantRequest(UUID playerUuid, String playerName, String questId, RewardDto reward) {}
}
