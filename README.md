# ChaosLedger

> Хаотичное комьюнити снаружи, строгий гроссбух внутри.

ChaosLedger — авторитетный серверный мод для **Minecraft 26.3** на **Fabric**, который
хранит и обрабатывает:

- экономику сервера (счета, валюты, транзакции),
- динамические цены и рынок,
- спрос/предложение,
- квесты и прогресс квестов (state-machine),
- награды,
- историю транзакций,
- аудит действий.

Веб-панель не является источником истины — Minecraft-сервер с этим модом главный.
Веб-панель читает данные и отправляет админские действия через встроенный HTTP API.

## Стек

| Компонент          | Технология                                   |
|--------------------|----------------------------------------------|
| Minecraft          | 26.3                                         |
| Modloader          | Fabric Loader 0.17.x                         |
| Java               | 25                                           |
| Build tool         | Gradle 8.14 (wrapper включён)                |
| Fabric Loom        | 1.17.x                                       |
| Fabric API         | ветка под Minecraft 26.3                     |
| JSON               | Gson (входит в дистрибутив Minecraft)        |
| HTTP API           | встроенный `com.sun.net.httpserver.HttpServer` |
| Основная БД        | SQLite (`sqlite-jdbc` встроен в jar)         |
| Команды            | Brigadier                                    |
| GUI                | vanilla chest через ScreenHandler            |
| Предметы           | vanilla + Data Components                    |

## Структура проекта

```
chaosledger/
├── build.gradle, settings.gradle, gradle.properties
├── gradlew, gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── README.md, BUILD.md, LICENSE
└── src/main/
    ├── java/dev/chaosledger/chaosledger/
    │   ├── ChaosLedger.java              — глобальный доступ к config/services
    │   ├── ChaosLedgerConfig.java        — конфиг (валюта, порт HTTP, эластичность цен)
    │   ├── ChaosLedgerMod.java           — Fabric ModInitializer
    │   ├── ChaosLedgerServices.java      — DI-контейнер движков и репозиториев
    │   ├── audit/                        — AuditEntry, AuditLogger
    │   ├── bootstrap/Standalone.java    — запуск БД+HTTP без Minecraft (для dev)
    │   ├── command/ChaosLedgerCommands  — Brigadier /cl ...
    │   ├── economy/                     — Account, Currency, Money, Transaction,
    │   │                                   MarketItem, PriceEngine, EconomyEngine,
    │   │                                   MarketEngine, EconomyException
    │   ├── gui/LedgerScreenHandler      — chest GUI с балансами
    │   ├── http/                        — HttpApiServer, AuthFilter, HttpJson, HttpSupport,
    │   │                                 handler/ (Accounts, Transactions, Market, Quests,
    │   │                                 Rewards, Currencies, Audit, Health), dto/Dtos
    │   ├── item/                         — ModComponents, ModItems (ledger_coin),
    │   │                                   ServerItemDelivery
    │   ├── quest/                        — Quest, QuestState, QuestObjective, QuestEvent,
    │   │                                   QuestProgress, QuestEngine, QuestException
    │   ├── reward/                       — Reward, RewardEngine, RewardException, ItemDelivery
    │   └── storage/                     — Database, Schema, StorageException,
    │                                     CurrencyRepository, AccountRepository,
    │                                     TransactionRepository, MarketRepository,
    │                                     QuestRepository, QuestProgressRepository,
    │                                     RewardRepository, AuditRepository
    └── resources/
        ├── fabric.mod.json
        └── assets/chaosledger/
            ├── lang/{en_us,ru_ru}.json
            └── models/item/ledger_coin.json
```

## Архитектурный принцип

```
Minecraft Server + ChaosLedger
        │
        ├── Economy Engine   (счета, валюты, переводы, гранты, чарджи)
        ├── Quest Engine     (state-machine: LOCKED → AVAILABLE → ACTIVE → COMPLETED → CLAIMED)
        ├── Reward Engine    (MONEY / ITEM, выдача через EconomyEngine + ItemDelivery)
        ├── Market Engine    (покупка/продажа, PriceEngine пересчитывает цену по спросу/предложению)
        ├── Storage Layer    (SQLite, репозитории, транзакционность через Connection.commit)
        └── Embedded HTTP API (com.sun.net.httpserver, single-thread executor)
                ▲
                │
        Web Panel / Admin UI
```

## Команды

```
/cl balance [currency]
/cl pay <player> <amount> [currency] [reason...]
/cl transactions [limit]
/cl quest list
/cl quest start <questId>
/cl quest claim <questId>
/cl quest progress <questId>
/cl admin grant <player> <amount> [currency] [reason...]   (OP 2+)
/cl admin charge <player> <amount> [currency] [reason...] (OP 2+)
/cl admin top [currency] [limit]                          (OP 2+)
/cl admin reload                                          (OP 2+)
/cl help
```

## HTTP API

Базовый URL: `http://127.0.0.1:8775/api` (по умолчанию). Заголовок `X-Api-Key` для авторизации,
если `httpApiKey` задан в конфиге. Подробности по каждому endpoint — в `BUILD.md` и в
`HttpApiServer.routes()`.

## Версии

- `minecraft_version=26.3`
- `loader_version=0.17.0`
- `loom_version=1.17.5`
- `fabric_version=0.130.0+26.3`
- `java_version=25`

> Версии mappings/Fabric API для MC 26.3 могут быть ещё не опубликованы в момент первого
> релиза. Если сборка падает с ошибкой резолва зависимости — подставьте актуальные
> build-номера в `gradle.properties` и пересоберите.

## Лицензия

MIT — см. `LICENSE`.
