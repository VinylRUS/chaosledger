package dev.chaosledger.chaosledger.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.chaosledger.chaosledger.http.HttpJson;
import dev.chaosledger.chaosledger.http.HttpSupport;
import dev.chaosledger.chaosledger.http.dto.Dtos.DefineQuestRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.ObjectiveDto;
import dev.chaosledger.chaosledger.http.dto.Dtos.QuestActionRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.QuestDto;
import dev.chaosledger.chaosledger.http.dto.Dtos.QuestEventRequest;
import dev.chaosledger.chaosledger.http.dto.Dtos.QuestProgressDto;
import dev.chaosledger.chaosledger.http.dto.Dtos.RewardDto;
import dev.chaosledger.chaosledger.quest.Quest;
import dev.chaosledger.chaosledger.quest.QuestEngine;
import dev.chaosledger.chaosledger.quest.QuestEvent;
import dev.chaosledger.chaosledger.quest.QuestException;
import dev.chaosledger.chaosledger.quest.QuestObjective;
import dev.chaosledger.chaosledger.reward.Reward;

import java.io.IOException;
import java.util.List;

import static dev.chaosledger.chaosledger.http.HttpJson.GSON;

/**
 * {@code GET    /api/quests}                              — list quests.
 * {@code GET    /api/quests/{id}}                         — single quest.
 * {@code POST   /api/quests}                              — define / update a quest.
 * {@code DELETE /api/quests/{id}}                          — delete a quest.
 * {@code GET    /api/quests/progress/{uuid} }              — player progress for all quests.
 * {@code GET    /api/quests/progress/{uuid}/{id}}          — player progress for one quest.
 * {@code POST   /api/quests/start}                        — start a quest.
 * {@code POST   /api/quests/available}                    — make a quest available.
 * {@code POST   /api/quests/claim}                        — claim the reward.
 * {@code POST   /api/quests/fail}                         — fail an active quest.
 * {@code POST   /api/quests/event}                        — ingest a gameplay event.
 */
public final class QuestsHandler implements HttpHandler {

    private final QuestEngine quests;

    public QuestsHandler(QuestEngine quests) {
        this.quests = quests;
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

            if (path.equals("/api/quests") && method.equals("GET")) {
                HttpJson.ok(ex, quests.all().stream().map(QuestDto::of).toList());
                return;
            }
            if (path.equals("/api/quests") && method.equals("POST")) {
                DefineQuestRequest req = GSON.fromJson(HttpSupport.readBody(ex), DefineQuestRequest.class);
                List<QuestObjective> objs = req.objectives().stream()
                        .map(o -> new QuestObjective(o.id(), o.type(), o.target(), o.required(), o.description()))
                        .toList();
                Reward reward = toReward(req.reward());
                long now = System.currentTimeMillis();
                Quest quest = new Quest(req.id(), req.title(), req.description(), objs, reward,
                        req.repeatable(), req.cooldownSec(), now, now);
                quests.define(quest);
                HttpJson.created(ex, QuestDto.of(quest));
                return;
            }
            if (path.equals("/api/quests/event") && method.equals("POST")) {
                QuestEventRequest req = GSON.fromJson(HttpSupport.readBody(ex), QuestEventRequest.class);
                QuestEvent event = new QuestEvent(req.type(), req.target(), req.amount(),
                        req.playerUuid(), req.playerName());
                quests.ingest(event);
                HttpJson.ok(ex, new JsonObject());
                return;
            }
            if (path.equals("/api/quests/start") && method.equals("POST")) {
                QuestActionRequest req = GSON.fromJson(HttpSupport.readBody(ex), QuestActionRequest.class);
                HttpJson.ok(ex, QuestProgressDto.of(quests.start(req.playerUuid(), req.questId())));
                return;
            }
            if (path.equals("/api/quests/available") && method.equals("POST")) {
                QuestActionRequest req = GSON.fromJson(HttpSupport.readBody(ex), QuestActionRequest.class);
                HttpJson.ok(ex, QuestProgressDto.of(quests.makeAvailable(req.playerUuid(), req.questId())));
                return;
            }
            if (path.equals("/api/quests/claim") && method.equals("POST")) {
                QuestActionRequest req = GSON.fromJson(HttpSupport.readBody(ex), QuestActionRequest.class);
                quests.claim(req.playerUuid(), req.playerName(), req.questId());
                HttpJson.ok(ex, new JsonObject());
                return;
            }
            if (path.equals("/api/quests/fail") && method.equals("POST")) {
                QuestActionRequest req = GSON.fromJson(HttpSupport.readBody(ex), QuestActionRequest.class);
                quests.fail(req.playerUuid(), req.questId());
                HttpJson.ok(ex, new JsonObject());
                return;
            }

            String[] parts = path.substring("/api/quests".length()).split("/");
            if (parts.length == 3 && "progress".equals(parts[1]) && method.equals("GET")) {
                java.util.UUID uuid = java.util.UUID.fromString(parts[2]);
                HttpJson.ok(ex, quests.progressOf(uuid).stream().map(QuestProgressDto::of).toList());
                return;
            }
            if (parts.length == 4 && "progress".equals(parts[1]) && method.equals("GET")) {
                java.util.UUID uuid = java.util.UUID.fromString(parts[2]);
                HttpJson.ok(ex, QuestProgressDto.of(quests.progressOf(uuid, parts[3])));
                return;
            }
            if (parts.length == 2 && method.equals("GET")) {
                HttpJson.ok(ex, QuestDto.of(quests.quest(parts[1])
                        .orElseThrow(() -> new QuestException("Unknown quest: " + parts[1]))));
                return;
            }
            if (parts.length == 2 && method.equals("DELETE")) {
                quests.delete(parts[1]);
                HttpJson.ok(ex, new JsonObject());
                return;
            }
            HttpJson.error(ex, 404, "Unknown path: " + path);
        } catch (QuestException e) {
            HttpJson.error(ex, 400, e.getMessage());
        } catch (IllegalArgumentException e) {
            HttpJson.error(ex, 400, "Bad request: " + e.getMessage());
        }
    }

    private Reward toReward(RewardDto dto) {
        if (dto == null) {
            throw new QuestException("Quest reward is required");
        }
        return switch (Reward.Type.valueOf(dto.type())) {
            case MONEY  -> Reward.money(new dev.chaosledger.chaosledger.economy.Money(
                    dto.money().amount(), dto.money().currency()));
            case ITEM   -> Reward.item(dto.itemId(), dto.count());
        };
    }
}
