# ChaosLedger WebSocket Reverse-Tunnel Protocol

**Версия протокола:** `1.0`
**Статус:** контракт (спецификация) — под него параллельно пишутся mod-side WS-клиент и bot-side WS-сервер.

---

## 1. Почему так, а не иначе

Exaroton (как Aternos) открывает наружу **только** Minecraft-порт. Любой TCP-порт, на котором мод поднял бы `HttpServer` (нынешний `8775`), слушает на localhost внутри контейнера MC и **недоступен** с BotHost.

```
[BotHost:8000 бот]  ──HTTP──>  dibilki.exaroton.me:8775   ❌ Exaroton режет входящие не-MC порты
```

Exaroton режет **входящие**, но не **исходящие**. Поэтому соединение разворачивается наоборот: мод сам подключается к боту как WS-клиент, и бот шлёт команды через это уже установленное соединение.

```
[мод ChaosLedger inside MC process]
        │
        │  WS outbound (initiator)        ── инициатор, не listener
        ▼
wss://your-bot-host/ws/chaosledger        (FastAPI WebSocket endpoint на BotHost)
        │
        │  HTTP /ledger, /pay, /buy ...   (Discord-бот читает из in-memory кеша)
        ▼
[Discord-команды пользователей]
```

Два направления трафика через один WS:

| Направление               | Кто инициатор | Что несёт                                   |
| ------------------------- | ------------- | ------------------------------------------- |
| Mod → Bot (snapshot push) | мод           | period-снимок состояния (балансы, рынок, прогресс квестов) |
| Bot → Mod (command)       | бот           | запрос на мутацию (`pay`/`grant`/`charge`/`buy`/`sell`/`quest_*`/`reward_grant`) |
| Mod → Bot (command reply) | мод           | результат выполнения команды (success/error + payload) |

---

## 2. Соединение

### 2.1 Endpoint

Бот поднимает WS-эндпоинт (FastAPI `WebSocket`):

```
wss://<bot-host>/ws/chaosledger
```

В dev — `ws://` без TLS; в prod — обязательно `wss://` (Termination TLS на reverse-proxy перед FastAPI).

### 2.2 Подключение мода

Мод — WS-клиент. При старте MC-сервера открывает соединение и держит его постоянно. Параметры берёт из `chaosledger.properties`:

| Свойство               | По умолчанию | Назначение                                             |
| ---------------------- | ------------ | ------------------------------------------------------ |
| `wsEnabled`            | `false`      | Включить reverse-tunnel (off по умолчанию — обратно совместимо). |
| `wsUrl`                | `""`         | URL бота: `wss://bot-host/ws/chaosledger`.             |
| `wsBotToken`           | `""`         | Bearer-токен авторизации мода → бот.                   |
| `wsServerId`           | `""`         | Человекочитаемый id сервера (для логов бота; не auth). |
| `wsSnapshotIntervalSec`| `10`         | Как часто мод пушит snapshot.                          |
| `wsReconnectMinMs`     | `1000`       | Минимальная задержка реконнекта (exponential backoff). |
| `wsReconnectMaxMs`     | `30000`      | Кап реконнекта.                                         |
| `wsRequestTimeoutMs`   | `5000`       | Timeout на ожидание reply на команду.                  |

### 2.3 Авторизация (handshake)

Сразу после открытия WS-фрейма мод шлёт `hello`-сообщение (см. §3.1). Бот валидирует `token`, и шлёт либо `hello_ack`, либо закрывает соединение с кодом `4001` (см. §6).

Альтернатива: передавать токен в `Sec-WebSocket-Protocol` subprotocol или в query-параметре `?token=...`. Query — проще, но токен попадает в логи reverse-proxy; `Sec-WebSocket-Protocol` чище. В v1 используем **тело `hello`** как единственный способ — меньше краевых случаев, проще отлаживать.

---

## 3. Формат сообщений

Все сообщения — JSON-объекты, один на WS-текстовый фрейм, UTF-8. Общая обёртка:

```json
{
  "v": 1,
  "type": "<message-type>",
  "id":   "<uuid-string, optional — для request/reply корреляции>",
  "ts":   1790000000000,
  "data": { ... }
}
```

| Поле   | Тип      | Назначение                                                        |
| ------ | -------- | ---------------------------------------------------------------- |
| `v`    | int      | Версия протокола. `1`. На несовпадении — close `4002`.            |
| `type` | string   | Тип сообщения. Обязателен.                                       |
| `id`   | string   | UUIDv4. Обязателен для `request.*` и `reply.*`; опускается в push. |
| `ts`   | long     | Unix-millis отправителя. Для диагностики дрейфа часов.            |
| `data` | object   | Payload, специфичный для `type`.                                 |

### 3.1 Типы сообщений

#### `hello` — мод → бот (первое сообщение)
```json
{
  "v": 1, "type": "hello", "ts": 1790000000000,
  "data": {
    "token": "<wsBotToken>",
    "serverId": "dibilki",
    "modVersion": "1.0.0",
    "mcVersion": "26.3",
    "protocol": 1,
    "capabilities": ["snapshot", "commands", "audit"]
  }
}
```

#### `hello_ack` — бот → мод (после успешной авторизации)
```json
{
  "v": 1, "type": "hello_ack", "ts": 1790000000000,
  "data": {
    "botVersion": "3.14.0",
    "serverTime": 1790000000000,
    "snapshotIntervalSec": 10
  }
}
```

#### `snapshot` — мод → бот (periodic push)
```json
{
  "v": 1, "type": "snapshot", "ts": 1790000000000,
  "data": {
    "seq": 4287,
    "currencies": [ {"code":"CC","displayName":"Chaos Coin","symbol":"₡","defaultStarting":100.0} ],
    "accounts":   [ {"ownerUuid":"e1b9a2d0-...-0001","ownerName":"steve","currency":"CC","balance":{"amount":250.0,"currency":"CC"}} ],
    "market":     [ {"itemId":"minecraft:diamond","lastPrice":{"amount":118.5,"currency":"CC"},"stock":12.0,"demand":3.0,"supply":1.0,"updatedAt":1790000000000} ],
    "quests":     [ {"id":"first_blood","title":"First Blood","repeatable":true,"cooldownSec":3600} ],
    "progress":   [ {"playerUuid":"e1b9a2d0-...-0001","questId":"first_blood","state":"ACTIVE","progress":[3.0]} ],
    "audit":      [ {"id":5,"actorUuid":"e1b9a2d0-...-0001","action":"PAY","target":"e1b9a2d0-...-0002","createdAt":1790000000000} ]
  }
}
```

- `seq` монотонно растёт, чтобы бот мог детектить пропуски (если нужно — запросить ресинк).
- Массивы — **только изменённые с прошлого snapshot** если бот в `hello_ack.capabilities` заявил `delta-snapshot`; иначе полные. В v1 — всегда полные, дельты отложены.
- `audit` — последние N записей (N = настраивается на боте, default 20), чтобы не гонять всю историю.

#### `request.*` — бот → мод (команда)
Общий формат:
```json
{
  "v": 1, "type": "request.pay", "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "ts": 1790000000000,
  "data": { ...payload... }
}
```

`type` имеет форму `request.<action>`. Список действий — см. §4.

#### `reply.*` — мод → бот (ответ на команду)
```json
{
  "v": 1, "type": "reply.pay", "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "ts": 1790000000000,
  "data": {
    "ok": true,
    "result": { ... }
  }
}
```
Или ошибка:
```json
{
  "v": 1, "type": "reply.pay", "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "ts": 1790000000000,
  "data": {
    "ok": false,
    "error": "INSUFFICIENT_FUNDS",
    "message": "Player steve has only 30.00 CC, needs 50.00"
  }
}
```

- `id` совпадает с `id` исходного `request.*`.
- `ok=true` → `result` (DTO из существующего `Dtos.java`).
- `ok=false` → `error` (машинный код, см. §5) + `message` (человекочитаемое).

#### `ping` / `pong` — обе стороны
Приложенческий heartbeat, не путать с WS-protocol-level ping/pong:
```json
{ "v": 1, "type": "ping", "ts": 1790000000000, "data": {} }
{ "v": 1, "type": "pong", "ts": 1790000000000, "data": {} }
```
Шлёт та сторона, которая хочет проверить живость второй. Если за `wsRequestTimeoutMs` не получили ни `pong`, ни snapshot — соединение считается мёртвым, мод начинает реконнект.

---

## 4. Команды (request → reply)

Каждая команда имеет тип `request.<action>` и `reply.<action>`. Payload `request` и поле `result` в `reply` зеркалируют контракт существующего HTTP-API (см. `API.md`), чтобы переиспользовать DTO из `Dtos.java`.

| Action          | request.data                                | reply.result (ok=true)        | Аналог HTTP                       |
| --------------- | ------------------------------------------- | ----------------------------- | --------------------------------- |
| `pay`           | `PayRequest`                                | `TransactionDto`              | `POST /api/transactions/pay`       |
| `grant`         | `GrantRequest`                              | `TransactionDto`              | `POST /api/transactions/grant`     |
| `charge`        | `ChargeRequest`                             | `TransactionDto`              | `POST /api/transactions/charge`    |
| `market_buy`    | `MarketTradeRequest`                        | `MarketItemDto`               | `POST /api/market/buy`             |
| `market_sell`   | `MarketTradeRequest`                        | `MarketItemDto`               | `POST /api/market/sell`            |
| `market_define` | `DefineMarketItemRequest`                  | `MarketItemDto`               | `POST /api/market`                 |
| `quest_start`   | `QuestActionRequest`                        | `QuestProgressDto`            | `POST /api/quests/start`          |
| `quest_claim`   | `QuestActionRequest`                        | `{}`                          | `POST /api/quests/claim`           |
| `quest_fail`    | `QuestActionRequest`                        | `{}`                          | `POST /api/quests/fail`            |
| `quest_available`| `QuestActionRequest`                       | `QuestProgressDto`            | `POST /api/quests/available`       |
| `quest_event`   | `QuestEventRequest`                         | `{}`                          | `POST /api/quests/event`           |
| `reward_grant`  | `RewardGrantRequest`                        | `{}`                          | `POST /api/rewards/grant`          |
| `currency_define` | `CurrencyDto`                            | `CurrencyDto`                 | `POST /api/currencies`             |

Чтения (`GET /api/...`) отдельными командами не нужны — бот живёт на push-snapshot'ах. Исключение — explicit refresh по требованию (см. §4.1).

### 4.1 `request.snapshot` — бот → мод (on-demand refresh)
```json
{ "v":1, "type":"request.snapshot", "id":"<uuid>", "ts":1790000000000, "data":{} }
```
reply.result = полный `snapshot.data` (как в §3.1). Нужен после реконнекта бота или при пропуске `seq`.

---

## 5. Коды ошибок

Машинные строки в `reply.*.data.error` (не HTTP-статусы — их тут нет). Сообщения в `message` — человекочитаемые, для логов/пользователя.

| Код                       | Когда                                            |
| ------------------------- | ------------------------------------------------ |
| `UNAUTHORIZED`            | Неверный `token` в `hello`. Close `4001`.        |
| `PROTOCOL_VERSION`        | Несовпадение `v`. Close `4002`.                  |
| `UNKNOWN_ACTION`          | Бот прислал `request.<unknown>`.                 |
| `BAD_REQUEST`             | Невалидный payload (кривой UUID, негорный JSON). |
| `ECONOMY`                 | `EconomyException` (мало средств, нет валюты).  |
| `STORAGE`                 | `StorageException` (БД).                       |
| `QUEST`                   | `QuestException` (квест не найден, не завершён).|
| `REWARD`                  | `RewardException` (игрок оффлайн для item).    |
| `INTERNAL`                | Прочее.                                          |

Соответствие HTTP-маппингу (для совместимости с существующей логикой бота):
- `BAD_REQUEST` ↔ `400`
- `ECONOMY`/`STORAGE`/`QUEST`/`REWARD` ↔ `400` (бизнес-ошибка)
- `INTERNAL` ↔ `500`

---

## 6. WS close codes

Стандартные WS close code'ы (RFC 6455) с расширенным смыслом:

| Code  | Meaning                  | Кто закрывает | Когда                                    |
| ----- | ------------------------ | ------------ | ---------------------------------------- |
| 1000  | Normal closure           | обе           | Корректное завершение.                    |
| 4001  | Unauthorized             | бот           | Неверный `token` в `hello`.               |
| 4002  | Protocol version         | обе           | `v` не равен 1.                          |
| 4003  | Unknown action           | мод           | Бот прислал неизвестный `request.*`.       |
| 4004  | Bad payload              | мод           | Невалидный JSON/DTO в `request.*`.         |
| 4010  | Reconnect requested      | бот           | Бот перезагружается, просит переподключиться. |
| 4011   | Server shutting down     | мод           | MC-сервер останавливается.               |
| 4029  | Rate limited             | бот           | Мод флудит командами.                     |
| 4500+ | Internal errors          | обе           | Необработанное исключение.                |

На любом close, кроме `4001` (токен неверный — реконнект бесполезен до смены конфига), мод стартует exponential backoff: `min(wsReconnectMinMs * 2^n, wsReconnectMaxMs)` с jitter ±20%.

---

## 7. Жизненный цикл соединения

```
[mod boot]
    │
    ├─ WS connect to wsUrl
    │
    ├─ send hello { token, serverId, ... }
    │
    ├─ recv hello_ack ──── NO ──→ close 4001 → log, backoff, retry
    │       │
    │      YES
    │       │
    ├─ (loop) ─── every wsSnapshotIntervalSec ──→ send snapshot
    │       │
    │       ├─ recv request.* ──→ execute on HTTP-thread → send reply.*
    │       │
    │       └─ recv ping ──→ send pong
    │
    ├─ on close: log code, backoff, reconnect
    │
    └─ on mod shutdown: send close 4011, gracefully close
```

**Threading на стороне мода (важно!):**
- WS-клиент живёт на своём daemon-потоке (`ChaosLedger-WS`), separate от `ChaosLedger-HTTP` executor'а.
- Snapshot собирается на WS-потоке из in-memory кеша движков (движки single-writer-safe на HTTP executor'е; чтение из другого потока должно идти через их public read-API, не через внутренние поля).
- **Команды выполняются на `ChaosLedger-HTTP` executor'е** (или на MC server thread для write-операций, затрагивающих инвентарь — как `reward_grant`/`quest_claim`). WS-поток только принимает `request`, кладёт в очередь, и шлёт `reply` когда executor обработал. Это сохраняет текущую модель атомарности write'ов.
- Подробно threading-модель и реализация очереди — в §8.

---

## 8. Threading-модель (для реализации)

Мод добавляет класс `WsTunnelClient`:

```
WsTunnelClient (thread: ChaosLedger-WS)
  ├── connect loop (backoff)
  ├── send loop  (snapshot timer + reply queue)
  ├── recv loop  (request → submit to executor → reply)
  └── snapshot builder (calls economy.* read APIs)
```

Взаимодействие с существующими executor'ами:

```
recv loop ─── request ──> HttpExecutor.submit(() -> {
    try { result = dispatch(request) } catch (DomainException e) { err = ... }
    wsSendQueue.put(reply.ok / reply.err)
})
```

Где `dispatch(request)` — таблица `request.type → engine.method`, переиспользующая те же вызовы, что и HTTP-handler'ы. Таким образом:
- Все write-операции по-прежнему сериализованы на `ChaosLedger-HTTP`.
- WS-поток никогда не касается движков напрямую — только через `HttpExecutor.submit`.
- Snapshot собирается read-only — допустимо с WS-потока, т.к. движки предоставляют thread-safe read-API (`economy.accountsOf`, `market.all`, `quests.all` и т.п.).

**Caveat:** нужно убедиться, что read-API движков реально thread-safe (нет unsynchronized lazy-init, нет чтения mutable-коллекций напрямую). Это код-ревью задача при реализации — в текущем коде движки используют `synchronized`-блоки на write, но read может идти без блокировки. Если выяснится, что read гоняет с write — добавить `synchronized` или `ReadWriteLock` на чтение.

---

## 9. Bot-side: что меняется

### 9.1 Сейчас
```
cl_client.py:    httpx → http://exaroton-host:8775/api/*  (НЕ РАБОТАЕТ, порт закрыт)
cl_routes.py:    FastAPI routes → вызывает cl_client → возвращает Discord-ответ
```

### 9.2 Становится
```
cl_ws.py:        FastAPI WebSocket /ws/chaosledger → принимает соединение от мода
                 ├── handshake (validate token)
                 ├── snapshot handler → updates in-memory cache (dataclass/TypedDict)
                 ├── request sender → ws.send_json(request.*) → awaits reply by id
                 └── reconnect tracking
cl_cache.py:     thread-safe in-memory cache (snapshot-последний)
cl_routes.py:    FastAPI routes → читает из cl_cache → возвращает Discord-ответ
                 (минимальные изменения: вместо cl_client.pay() — cl_command.pay())
```

### 9.3 Migration path
- `cl_client.py` не удаляется — оставляется как fallback для **dev-режима** (локальный MC без WS). Выбор клиента в `cl_routes.py` через feature-flag `CL_TRANSPORT=ws|http`.
- `cl_routes.py` сигнатуры методов (`pay`, `buy`, `quest_action`) не меняются — меняется только имплементация под капотом (`cl_command` вместо `cl_client`).

---

## 9.4 Что нужно от тебя

Прежде чем я начну писать код (любую из сторон), мне нужно знать:

1. **Код бота.** В workspace его нет. Без него bot-side я не напишу (не знаю текущую структуру `cl_client`/`cl_routes`, FastAPI-app layout, auth-модель). Пришли бота — и я сделаю `cl_ws.py` + `cl_cache.py` + адаптацию `cl_routes.py` под контракт.
2. **Green-light на mod-side WS-клиент.** Это ~250 строк Java (`WsTunnelClient`, конфиг-поля, `WS Json payload records`, dispatch-таблица) поверх `java-websocket` или `okhttp`. Я могу написать его вслепую, но **собрать/проверить не смогу** — sandbox без fabric-loom/MC-зависимостей. Это значит: код может содержать маппинг-ошибки 26.3 (как уже было трижды), и их поймает только твой CI. Готов ли ты к этому, или хочешь сначала другой путь?

## 10. Реалистичные альтернативы (если WS-туннель не хочется)

Ради полноты, без рекомендации:

| Вариант                                    | Реалистичность на Exaroton               |
| ------------------------------------------ | ---------------------------------------- |
| File-based sync (мод пишет JSON, бот читает по SFTP/scp) | Сработает, но Exaroton не даёт SFTP на shared; нужен отдельный storage (S3, gist) — костыльно. |
| Push в webhook бота (мод → HTTPS POST на BotHost каждые 10с) | Работает (исходящие HTTPS открыты), но команды от бота к моду не пройдут — нет обратного канала. Только односторонний snapshot. |
| Discord-bot как MC-клиент (bot логинится на `dibilki.exaroton.me:25565` MC-портом) | 25565 открыт, но это MC-протокол, не HTTP; нужен whole MC-клиент в боте (`pycraft`/`mineflayer`-аналог на Python) — overkill и хрупко. |
| Перенос мода на VPS, где порты открыты      | Решает всё, но это уже не Exaroton.     |

WS reverse-tunnel остаётся наиболее прагматичным: one connection, bidirectional, работает на shared-хостинге, использует только разрешённые исходящие.

---

## 11. Чеклист реализации (когда стартуем)

### Mod-side (`WsTunnelClient.java` + `WsConfig` fields)
- [ ] Зависимость: `org.java-websocket:Java-WebSocket:1.5.x` (или `okhttp`, см. §12).
- [ ] Конфиг-поля: `wsEnabled`, `wsUrl`, `wsBotToken`, `wsServerId`, `wsSnapshotIntervalSec`, `wsReconnectMinMs`, `wsReconnectMaxMs`, `wsRequestTimeoutMs`.
- [ ] `WsTunnelClient` класс: connect loop с backoff, send/recv loops.
- [ ] `hello`/`hello_ack` handshake.
- [ ] `snapshot` builder (переиспользует read-API движков).
- [ ] Dispatch-таблица `request.type → engine.method` (см. §4) — те же вызовы, что в HTTP-handler'ах.
- [ ] Reply с корреляцией по `id`, timeout `wsRequestTimeoutMs`.
- [ ] Graceful shutdown (close 4011).
- [ ] Логи: connect/disconnect/reconnect/snapshot-stats на INFO, payload на DEBUG.

### Bot-side (`cl_ws.py` + `cl_cache.py`)
- [ ] FastAPI `WebSocket /ws/chaosledger` endpoint.
- [ ] Handshake: validate `token`, send `hello_ack`.
- [ ] Snapshot handler → `cl_cache.update(snapshot.data)`.
- [ ] Command sender: `await ws.send_json(request.*)`, `await reply by id` (asyncio.Future per id).
- [ ] `cl_cache.py`: thread-safe (asyncio.Lock) in-memory cache, TTL по `seq`.
- [ ] `cl_routes.py`: replace `cl_client.*` → `cl_command.*`, same signatures.
- [ ] Reconnect tracking + alert в Discord, когда мод > N сек не пушит.
- [ ] Логи: connect/disconnect/snapshot-stats/command-latency.

### Shared
- [ ] Тесты: roundtrip `pay`/`buy`/`quest_claim` через WS, проверка snapshot обновился.
- [ ] Документация: обновить `API.md` секцией «WS transport (Exaroton)».
- [ ] Versioning: bump mod `1.0.0 → 1.1.0` (WS — additive), bot `3.13.x → 3.14.0`.
