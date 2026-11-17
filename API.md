# ChaosLedger HTTP API

Встроенный HTTP-API сервера ChaosLedger. Реализован на `com.sun.net.httpserver.HttpServer` (класс `dev.chaosledger.chaosledger.http.HttpApiServer`), крутится отдельным daemon-потоком с однопоточным executor'ом (`ChaosLedger-HTTP`), чтобы все записи сериализовались относительно серверного тика Minecraft. Все маршруты смонтированы под `/api/*`.

---

## Содержание

1. [Конфигурация и запуск](#конфигурация-и-запуск)
2. [Аутентификация](#аутентификация)
3. [Общие конвенции](#общие-конвенции)
4. [Формат ошибок](#формат-ошибок)
5. [Быстрая сводка маршрутов](#быстрая-сводка-маршрутов)
6. [Типы данных (DTO)](#типы-данных-dto)
7. [Health](#health)
8. [Currencies](#currencies)
9. [Accounts](#accounts)
10. [Transactions](#transactions)
11. [Market](#market)
12. [Quests](#quests)
13. [Rewards](#rewards)
14. [Audit](#audit)

---

## Конфигурация и запуск

Сервер читает параметры из `ChaosLedgerConfig` (файл `chaosledger.properties` рядом с БД). Поля, влияющие на HTTP:

| Свойство          | По умолчанию | Описание                                                                |
| ----------------- | ------------ | ----------------------------------------------------------------------- |
| `httpEnabled`     | `true`       | Поднимать ли HTTP-сервер вообще.                                        |
| `httpBindHost`    | `127.0.0.1`  | Хост привязки. Держите `127.0.0.1`, если фронт идёт через reverse-proxy.|
| `httpPort`        | `8775`       | TCP-порт.                                                               |
| `httpApiKey`      | `""`         | API-ключ. Пустая строка = открытый режим (только для разработки).       |

Сервер запускается вместе с модом (см. `ChaosLedgerMod` / `Standalone`). `GET /api/health` работает без ключа — удобно для liveness-проб reverse-proxy.

---

## Аутентификация

Фильтр `AuthFilter` навешен на каждый контекст. Логика:

1. `OPTIONS` (CORS pre-flight) и `GET /api/health` пропускаются без проверки.
2. Если в конфиге `httpApiKey` пустой/`null` — режим **open**, ключ не требуется.
3. Иначе запрос должен содержать заголовок `X-Api-Key`, строго равный настроенному ключу.

При несовпадении — `401`:

```json
{ "error": "Unauthorized", "status": 401 }
```

Пример:

```bash
curl -H "X-Api-Key: $API_KEY" http://127.0.0.1:8775/api/currencies
```

---

## Общие конвенции

- **Content-Type**: `application/json; charset=utf-8` на всех ответах, включая ошибки.
- **Тело запроса**: JSON (UTF-8). Пустое тело трактуется как `{}`.
- **Сериализация**: Gson с pretty-print. Числа `long` (время, id) передаются как числа, не строки. `UUID` — канонической строкой. Опциональные поля (`fromUuid`, `reference`, `completedAt` и т.п.) присутствуют со значением `null`, если данных нет.
- **CORS**: разрешены все источники (`Access-Control-Allow-Origin: *`), методы `GET, POST, PUT, DELETE, OPTIONS`, заголовки `Content-Type, X-Api-Key`. Это сделано для прямой работы веб-панели без прокси; в production ставьте reverse-proxy и сужайте CORS.
- **Статус-коды**:
  - `200 OK` — успешное чтение/мутация без создания.
  - `201 Created` — сущность создана/обновлена (define-currency, pay/grant/charge, define-market-item, define-quest).
  - `400 Bad Request` — бизнес-ошибка (`EconomyException`, `StorageException`, `QuestException`, `RewardException`) либо некорректный UUID.
  - `401 Unauthorized` — неверный/отсутствует `X-Api-Key`.
  - `404 Not Found` — неизвестный путь маршрутизации.
  - `405 Method Not Allowed` — только у `/api/audit` для не-GET.
  - `501 Not Implemented` — `GET /api/accounts` без UUID (нужен `/api/accounts/{uuid}`).
- **Поток выполнения**: однопоточный executor `ChaosLedger-HTTP`. Запросы обрабатываются строго последовательно, поэтому write-операции атомарны относительно друг друга, но длинный запрос заблокирует остальных — держите обработку короткой.

---

## Формат ошибок

Все ошибки единообразны:

```json
{ "error": "<сообщение>", "status": <код> }
```

Доменные исключения прокидывают свой `getMessage()` как `"error"`. `IllegalArgumentException` (обычно кривой UUID) оборачивается в `"Bad request: ..."` или `"Bad UUID: ..."`.

---

## Быстрая сводка маршрутов

| Метод  | Путь                                          | Назначение                                   | Тело / Параметры                          |
| ------ | --------------------------------------------- | -------------------------------------------- | ----------------------------------------- |
| GET    | `/api/health`                                 | Liveness-проба (без auth)                    | —                                         |
| GET    | `/api/currencies`                             | Список валют                                 | —                                         |
| POST   | `/api/currencies`                             | Создать/обновить валюту                      | `CurrencyDto`                             |
| GET    | `/api/accounts/top/{code}?limit=10`           | Топ-счета по валюте                          | `limit` (default 10)                     |
| GET    | `/api/accounts/{uuid}`                        | Все счета игрока                            | —                                         |
| GET    | `/api/accounts/{uuid}/{code}`                 | Один счёт                                   | —                                         |
| POST   | `/api/transactions/pay`                       | Перевод между игроками                       | `PayRequest`                              |
| POST   | `/api/transactions/grant`                      | Эмиссия игроку                              | `GrantRequest`                            |
| POST   | `/api/transactions/charge`                     | Списание с игрока                           | `ChargeRequest`                           |
| GET    | `/api/transactions/{uuid}?limit=20`           | История игрока                              | `limit` (default 20)                     |
| GET    | `/api/transactions/recent/{code}?limit=20`     | Последние транзакции по валюте              | `limit` (default 20)                     |
| GET    | `/api/market`                                 | Все рыночные предметы                       | —                                         |
| POST   | `/api/market`                                 | Задать/обновить рыночный предмет            | `DefineMarketItemRequest`                 |
| GET    | `/api/market/{itemId}`                        | Один предмет                                | —                                         |
| POST   | `/api/market/buy`                             | Купить предмет                              | `MarketTradeRequest`                      |
| POST   | `/api/market/sell`                            | Продать предмет                             | `MarketTradeRequest`                      |
| GET    | `/api/market/{itemId}/history?limit=50`       | История цены                                | `limit` (default 50)                     |
| GET    | `/api/quests`                                 | Все квесты                                  | —                                         |
| POST   | `/api/quests`                                 | Создать/обновить квест                      | `DefineQuestRequest`                      |
| GET    | `/api/quests/{id}`                            | Один квест                                  | —                                         |
| DELETE | `/api/quests/{id}`                            | Удалить квест                               | —                                         |
| GET    | `/api/quests/progress/{uuid}`                 | Прогресс игрока по всем квестам             | —                                         |
| GET    | `/api/quests/progress/{uuid}/{id}`            | Прогресс по одному квесту                    | —                                         |
| POST   | `/api/quests/available`                       | Сделать квест доступным игроку              | `QuestActionRequest`                      |
| POST   | `/api/quests/start`                           | Начать квест                                | `QuestActionRequest`                      |
| POST   | `/api/quests/claim`                           | Забрать награду                             | `QuestActionRequest`                      |
| POST   | `/api/quests/fail`                            | Провалить квест                             | `QuestActionRequest`                      |
| POST   | `/api/quests/event`                           | Заинжестить игровое событие                 | `QuestEventRequest`                       |
| GET    | `/api/rewards/{uuid}?limit=20`                | История наград игрока                       | `limit` (default 20)                     |
| POST   | `/api/rewards/grant`                          | Выдать ad-hoc награду (admin)               | `RewardGrantRequest`                      |
| GET    | `/api/audit?limit=50`                         | Последние audit-записи                      | `limit` (default 50)                     |
| GET    | `/api/audit/{uuid}?limit=50`                  | Audit-записи конкретного актёра             | `limit` (default 50)                     |

---

## Типы данных (DTO)

Все DTO — Java-records из `dev.chaosledger.chaosledger.http.dto.Dtos`. Ниже их wire-формат.

### `MoneyDto`
```json
{ "amount": 150.0, "currency": "CC" }
```

### `CurrencyDto`
```json
{
  "code": "CC",
  "displayName": "Chaos Coin",
  "symbol": "₡",
  "defaultStarting": 100.0
}
```

### `AccountDto`
```json
{
  "id": 7,
  "ownerUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "ownerName": "steve",
  "currency": "CC",
  "balance": { "amount": 250.0, "currency": "CC" },
  "createdAt": 1790000000000,
  "updatedAt": 1790000000000
}
```

### `TransactionDto`
```json
{
  "id": 42,
  "fromUuid": "e1b9a2d0-...-0001",
  "toUuid":   "e1b9a2d0-...-0002",
  "amount":   { "amount": 50.0, "currency": "CC" },
  "reason": "trade",
  "category": "TRANSFER",
  "reference": "txn-abc-123",
  "createdAt": 1790000000000
}
```
`fromUuid`/`toUuid`/`reference` могут быть `null` (например, у `grant` нет `fromUuid`, у `charge` нет `toUuid`).

### `MarketItemDto`
```json
{
  "itemId": "minecraft:diamond",
  "displayName": "Diamond",
  "basePrice": { "amount": 100.0, "currency": "CC" },
  "stock": 12.0,
  "demand": 3.0,
  "supply": 1.0,
  "lastPrice": { "amount": 118.5, "currency": "CC" },
  "updatedAt": 1790000000000
}
```

### `PricePointDto`
```json
{ "price": 118.5, "recordedAt": 1790000000000 }
```

### `ObjectiveDto`
```json
{
  "id": "kill_zombies",
  "type": "KILL",
  "target": "minecraft:zombie",
  "required": 10.0,
  "description": "Убей 10 зомби"
}
```

### `RewardDto`
Тип `"MONEY"`:
```json
{ "type": "MONEY", "money": { "amount": 500.0, "currency": "CC" }, "itemId": null, "count": 0, "label": null }
```
Тип `"ITEM"`:
```json
{ "type": "ITEM", "money": null, "itemId": "minecraft:diamond", "count": 3, "label": null }
```
Поле `type` строго `"MONEY"` или `"ITEM"` (значение `Reward.Type.name()`). При неверном значении — `IllegalArgumentException` → `400`.

### `QuestDto`
```json
{
  "id": "first_blood",
  "title": "First Blood",
  "description": "Убей 10 зомби",
  "objectives": [ { "id": "kill_zombies", "type": "KILL", "target": "minecraft:zombie", "required": 10.0, "description": "..." } ],
  "reward": { "type": "MONEY", "money": { "amount": 500.0, "currency": "CC" }, "itemId": null, "count": 0, "label": null },
  "repeatable": true,
  "cooldownSec": 3600,
  "createdAt": 1790000000000,
  "updatedAt": 1790000000000
}
```

### `QuestProgressDto`
```json
{
  "id": 99,
  "playerUuid": "e1b9a2d0-...-0001",
  "questId": "first_blood",
  "state": "ACTIVE",
  "progress": [3.0],
  "startedAt": 1790000000000,
  "completedAt": null,
  "claimedAt": null
}
```
`state` — имя `QuestState` enum (`AVAILABLE`, `ACTIVE`, `COMPLETED`, `CLAIMED`, `FAILED` и т.д. в зависимости от движка). `completedAt`/`claimedAt` — `Long`, могут быть `null`.

### `AuditEntryDto`
```json
{
  "id": 5,
  "actorUuid": "e1b9a2d0-...-0001",
  "actorName": "steve",
  "action": "PAY",
  "target": "e1b9a2d0-...-0002",
  "payloadJson": "{\"amount\":50.0,\"currency\":\"CC\"}",
  "createdAt": 1790000000000
}
```
`actorUuid`/`actorName`/`target`/`payloadJson` могут быть `null`.

---

## Health

### `GET /api/health`
Liveness-проба. **Не требует `X-Api-Key`.**

**Ответ `200`:**
```json
{
  "status": "ok",
  "mod": "ChaosLedger",
  "version": "1.0.0",
  "serverTime": 1790000000000
}
```

```bash
curl http://127.0.0.1:8775/api/health
```

---

## Currencies

### `GET /api/currencies`
Список всех зарегистрированных валют.

**Ответ `200`:** массив `CurrencyDto`.

```bash
curl -H "X-Api-Key: $API_KEY" http://127.0.0.1:8775/api/currencies
```

### `POST /api/currencies`
Создать или обновить валюту. Если `code` уже существует — обновляются `displayName`, `symbol`, `defaultStarting`.

**Тело:** `CurrencyDto` (все поля).

**Ответ `201`:** созданный/обновлённый `CurrencyDto`.

```bash
curl -X POST -H "X-Api-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"code":"CC","displayName":"Chaos Coin","symbol":"₡","defaultStarting":100.0}' \
  http://127.0.0.1:8775/api/currencies
```

---

## Accounts

> `GET /api/accounts` без UUID намеренно возвращает `501` с подсказкой использовать `/api/accounts/<uuid>`.

### `GET /api/accounts/top/{code}?limit=10`
Топ счетов по указанной валюте.

- `limit` опционален, по умолчанию `10`.

**Ответ `200`:** массив `AccountDto`.

```bash
curl -H "X-Api-Key: $API_KEY" "http://127.0.0.1:8775/api/accounts/top/CC?limit=5"
```

### `GET /api/accounts/{uuid}`
Все счета игрока (по одной записи на валюту).

**Ответ `200`:** массив `AccountDto`. Если у игрока ещё нет счёта в валюте по умолчанию — движок автосоздаст его при обращении через `accountOf`.

```bash
curl -H "X-Api-Key: $API_KEY" \
  http://127.0.0.1:8775/api/accounts/e1b9a2d0-0000-0000-0000-000000000001
```

### `GET /api/accounts/{uuid}/{code}`
Один конкретный счёт игрока в валюте `code`. При отсутствии — движок создаёт счёт со стартовым балансом (`defaultStarting` валюты) и возвращает его.

**Ответ `200`:** `AccountDto`.

**Ошибки:** `400` — неизвестная валюта (`EconomyException`), некорректный UUID (`Bad UUID`).

```bash
curl -H "X-Api-Key: $API_KEY" \
  http://127.0.0.1:8775/api/accounts/e1b9a2d0-0000-0000-0000-000000000001/CC
```

---

## Transactions

### `POST /api/transactions/pay`
Перевод `amount` валюты `currency` от `fromUuid` к `toUuid`. Баланс отправителя не должен уйти в минус, если в конфиге `allowNegativeBalance=false` (иначе `EconomyException` → `400`).

**Тело:** `PayRequest`
```json
{
  "fromUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "fromName": "steve",
  "toUuid":   "e1b9a2d0-0000-0000-0000-000000000002",
  "toName":   "alex",
  "currency": "CC",
  "amount": 50.0,
  "reason": "trade"
}
```

**Ответ `201`:** `TransactionDto` созданной транзакции (`category="TRANSFER"`, `reference` если назначен движком).

```bash
curl -X POST -H "X-Api-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"fromUuid":"e1b9a2d0-0000-0000-0000-000000000001","fromName":"steve",
       "toUuid":"e1b9a2d0-0000-0000-0000-000000000002","toName":"alex",
       "currency":"CC","amount":50.0,"reason":"trade"}' \
  http://127.0.0.1:8775/api/transactions/pay
```

### `POST /api/transactions/grant`
Эмиссия денег игроку (увеличивает общий объём валюты). Используется админкой и системой наград.

**Тело:** `GrantRequest`
```json
{
  "playerUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "playerName": "steve",
  "currency":   "CC",
  "amount": 500.0,
  "reason": "quest_reward:first_blood"
}
```

**Ответ `201`:** `TransactionDto` (`fromUuid=null`, `category="GRANT"`).

### `POST /api/transactions/charge`
Списание денег с игрока (обратное к `grant`).

**Тело:** `ChargeRequest`
```json
{
  "playerUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "playerName": "steve",
  "currency":   "CC",
  "amount": 25.0,
  "reason": "fine",
  "category": "ADMIN"
}
```
`category` опционален — если `null`, подставляется `"ADMIN"`.

**Ответ `201`:** `TransactionDto` (`toUuid=null`).

**Ошибки:** `400` — недостаточно средств (если отрицательные балансы запрещены), неизвестная валюта, некорректный UUID.

### `GET /api/transactions/{uuid}?limit=20`
Последние транзакции игрока (входящие и исходящие).

- `limit` опционален, по умолчанию `20`.

**Ответ `200`:** массив `TransactionDto` (свежие первыми).

```bash
curl -H "X-Api-Key: $API_KEY" \
  "http://127.0.0.1:8775/api/transactions/e1b9a2d0-0000-0000-0000-000000000001?limit=5"
```

### `GET /api/transactions/recent/{code}?limit=20`
Последние транзакции по конкретной валюте (по всем игрокам).

- `limit` опционален, по умолчанию `20`.

**Ответ `200`:** массив `TransactionDto`.

---

## Market

### `GET /api/market`
Все рыночные предметы (одна запись на `itemId`).

**Ответ `200`:** массив `MarketItemDto`.

### `POST /api/market`
Задать/обновить рыночный предмет. `basePrice` задаётся через отдельные поля `basePrice` (число) и `currency`; внутри движка они упаковываются в `Money`. `lastPrice` при создании приравнивается к `basePrice`, `demand`/`supply` сбрасываются в `0`.

**Тело:** `DefineMarketItemRequest`
```json
{
  "itemId": "minecraft:diamond",
  "displayName": "Diamond",
  "currency": "CC",
  "basePrice": 100.0,
  "stock": 0.0
}
```

**Ответ `201`:** `MarketItemDto` (с проставленными `lastPrice`, `demand=0`, `supply=0`, `updatedAt`).

### `GET /api/market/{itemId}`
Один предмет по его `itemId` (например `minecraft:diamond`).

**Ответ `200`:** `MarketItemDto`.

**Ошибки:** `400` — `{"error":"Unknown item: <itemId>","status":400}` если предмет не зарегистрирован.

```bash
curl -H "X-Api-Key: $API_KEY" \
  http://127.0.0.1:8775/api/market/minecraft:diamond
```

### `POST /api/market/buy`
Купить `quantity` единиц предмета за текущую цену (`lastPrice`). Двигает `demand` вверх и пересчитывает `lastPrice` по эластичности из конфига (`priceElasticity`, `priceMinFactor`, `priceMaxFactor`). Списывает деньги с игрока в валюте предмета.

**Тело:** `MarketTradeRequest`
```json
{
  "playerUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "playerName": "steve",
  "itemId": "minecraft:diamond",
  "quantity": 3.0
}
```

**Ответ `200`:** обновлённый `MarketItemDto` (новая цена, спрос, запас).

**Ошибки:** `400` — неизвестный предмет, недостаточно средств, отрицательная `quantity`.

### `POST /api/market/sell`
Продать `quantity` единиц предмета. Двигает `supply` вверх, `lastPrice` падает. Начисляет деньги игроку.

**Тело/Ответ:** те же, что у `/buy` (только `200`, не `201`).

### `GET /api/market/{itemId}/history?limit=50`
История цены предмета (серии наблюдений).

- `limit` опционален, по умолчанию `50`.

**Ответ `200`:** массив `PricePointDto` (точки `{price, recordedAt}`).

```bash
curl -H "X-Api-Key: $API_KEY" \
  "http://127.0.0.1:8775/api/market/minecraft:diamond/history?limit=100"
```

---

## Quests

> Важно: маршруты `/api/quests/available|start|claim|fail|event` сматчены как точные пути **до** разбиения по `/{id}`. Поэтому id квеста, буквально равный `progress`, `available`, `start`, `claim`, `fail` или `event`, нельзя получить через `GET /api/quests/{id}` — выбирайте другие id.

### `GET /api/quests`
Все квесты.

**Ответ `200`:** массив `QuestDto`.

### `POST /api/quests`
Создать/обновить квест (upsert по `id`). `createdAt`/`updatedAt` проставляются сервером (текущее время).

**Тело:** `DefineQuestRequest`
```json
{
  "id": "first_blood",
  "title": "First Blood",
  "description": "Убей 10 зомби",
  "objectives": [
    { "id": "kill_zombies", "type": "KILL", "target": "minecraft:zombie", "required": 10.0, "description": "Убей зомби" }
  ],
  "reward": { "type": "MONEY", "money": { "amount": 500.0, "currency": "CC" }, "itemId": null, "count": 0, "label": null },
  "repeatable": true,
  "cooldownSec": 3600
}
```

**Ответ `201`:** `QuestDto` (с серверными `createdAt`/`updatedAt`).

**Ошибки:** `400` — если `reward` равен `null` (`"Quest reward is required"`) или `reward.type` не `"MONEY"`/`"ITEM"`.

### `GET /api/quests/{id}`
Один квест.

**Ответ `200`:** `QuestDto`.

**Ошибки:** `400` — `{"error":"Unknown quest: <id>","status":400}`.

### `DELETE /api/quests/{id}`
Удалить квест по `id`.

**Ответ `200`:** `{}`.

### `GET /api/quests/progress/{uuid}`
Прогресс игрока по всем квестам, которые он когда-либо затрагивал.

**Ответ `200`:** массив `QuestProgressDto`.

### `GET /api/quests/progress/{uuid}/{id}`
Прогресс игрока по конкретному квесту.

**Ответ `200`:** `QuestProgressDto`.

### `POST /api/quests/available`
Сделать квест доступным игроку (переход прогресса в `AVAILABLE`). Игрок ещё не начал квест, но видит его в списке доступных.

**Тело:** `QuestActionRequest`
```json
{ "playerUuid": "e1b9a2d0-0000-0000-0000-000000000001", "playerName": "steve", "questId": "first_blood" }
```

**Ответ `200`:** `QuestProgressDto`.

### `POST /api/quests/start`
Игрок начинает квест (переход `AVAILABLE → ACTIVE`). Стартер прогресса по целям — нули.

**Тело/Ответ:** как у `available` (`200 QuestProgressDto`).

### `POST /api/quests/claim`
Забрать награду за завершённый квест. Движок проверяет, что квест `COMPLETED`, и начисляет награду через `RewardEngine` (деньги на счёт или предметы в инвентарь). `playerName` обязателен — нужен для инвентаря и audit.

**Тело:** `QuestActionRequest`.

**Ответ `200`:** `{}`.

**Ошибки:** `400` — квест не завершён, квест не найден, не выдаётся повторно (если `repeatable=false` и уже был `CLAIMED`).

### `POST /api/quests/fail`
Принудительно провалить активный квест (`ACTIVE → FAILED`).

**Тело:** `QuestActionRequest`.

**Ответ `200`:** `{}`.

### `POST /api/quests/event`
Заинжестить игровое событие — двигает прогресс всех активных квестов, чьи `objective.type`/`target` совпадают. Например событие `KILL`/`minecraft:zombie`/`amount=1` инкрементит цели «убей N зомби» у всех игроков.

**Тело:** `QuestEventRequest`
```json
{
  "playerUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "playerName": "steve",
  "type": "KILL",
  "target": "minecraft:zombie",
  "amount": 1.0
}
```

**Ответ `200`:** `{}` (событие обработано асинхронно движком; конечный прогресс читайте через `/api/quests/progress/{uuid}`).

---

## Rewards

### `GET /api/rewards/{uuid}?limit=20`
История выданных игроку наград.

- `limit` опционален, по умолчанию `20`.

**Ответ `200`:** массив `RewardDto`.

```bash
curl -H "X-Api-Key: $API_KEY" \
  "http://127.0.0.1:8775/api/rewards/e1b9a2d0-0000-0000-0000-000000000001?limit=5"
```

### `POST /api/rewards/grant`
Ad-hoc выдача награды администратором (минуя квесты). `questId` опционален — если есть, привязывается к audit-записи.

**Тело:** `RewardGrantRequest` (определён в `RewardsHandler`)
```json
{
  "playerUuid": "e1b9a2d0-0000-0000-0000-000000000001",
  "playerName": "steve",
  "questId": null,
  "reward": { "type": "ITEM", "money": null, "itemId": "minecraft:diamond", "count": 3, "label": null }
}
```

**Ответ `200`:** `{}`.

**Поведение награды:**
- `MONEY` — начисляет `reward.money` на счёт игрока в указанной валюте (через `EconomyEngine.grant`).
- `ITEM` — вставляет `count` штук `itemId` в инвентарь игрока через `ServerItemDelivery`; если инвентарь полон, предметы не теряются — `RewardEngine` решает по своей логике fallback (подробности в `RewardEngine`).

**Ошибки:** `400` — неверный `reward.type`, игрок не в сети (для item-награды), некорректный UUID.

---

## Audit

> Этот ресурс принимает только `GET`. Любой другой метод — `405 Method not allowed`.

### `GET /api/audit?limit=50`
Последние audit-записи по всем актёрам.

- `limit` опционален, по умолчанию `50`.

**Ответ `200`:** массив `AuditEntryDto` (свежие первыми).

```bash
curl -H "X-Api-Key: $API_KEY" "http://127.0.0.1:8775/api/audit?limit=100"
```

### `GET /api/audit/{uuid}?limit=50`
Audit-записи конкретного актёра (по `actorUuid`).

- `limit` опционален, по умолчанию `50`.

**Ответ `200`:** массив `AuditEntryDto`.

**Ошибки:** `400` — некорректный UUID.

---

## Замечания по интеграции

- **Однопоточный executor.** `ChaosLedger-HTTP` обрабатывает запросы строго последовательно. Плюс — атомарность write-операций; минус — один медленный запрос блокирует остальных. Не делайте тяжёлых запросов; для批量-операций бейте на пачки.
- **Поток vs серверный тик.** Handler'ы вызывают движки напрямую с HTTP-потока, а движки спроектированы single-writer-safe на этом же executor'е. Если добавляете мутации, которые должны идти строго на серверном тике Minecraft (например, выдача предметов), маршальте их на server thread внутри `RewardEngine`/`ServerItemDelivery`, как это уже сделано.
- **Reverse-proxy.** В production ставьте перед API nginx/Caddy с TLS, ограничивайте CORS, и держите `httpBindHost=127.0.0.1`. Внешний мир не должен видеть порт `8775` напрямую.
- **Версионирование.** Версия мода отдаётся только в `/api/health`. В URL нет `/v1/` — при изменении контракта DTO меняйте поле `version` и читайте его на стороне клиента.
