# ChaosLedger

> Хаотичное комьюнити снаружи, строгий гроссбух внутри.

[![Build](https://github.com/OWNER/chaosledger/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/OWNER/chaosledger/actions/workflows/build.yml)
[![Release](https://github.com/OWNER/chaosledger/actions/workflows/release.yml/badge.svg)](https://github.com/OWNER/chaosledger/actions/workflows/release.yml)
[![Latest release](https://img.shields.io/github/v/release/OWNER/chaosledger?include_prereleases&label=release)](https://github.com/OWNER/chaosledger/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.17.x-lightgrey.svg)](https://fabricmc.net/)

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

## Установка

### Из GitHub Releases (проще всего)

1. Перейдите на страницу [Releases](https://github.com/OWNER/chaosledger/releases).
2. Скачайте `chaosledger-<version>.jar` из последнего релиза.
3. Убедитесь, что в `mods/` вашего Fabric-сервера лежит **Fabric API** для Minecraft 26.3.
4. Скопируйте `chaosledger-<version>.jar` в `<server>/mods/`.
5. Запустите сервер. При первом старте создастся:
   - `<server>/chaosledger.db` — SQLite база,
   - `<server>/config/chaosledger/chaosledger.properties` — конфигурация.

Подробнее о конфигурации и standalone-запуске — в [BUILD.md](BUILD.md).

### Из исходников

```bash
git clone https://github.com/OWNER/chaosledger.git
cd chaosledger
# JDK 25 required
./gradlew build
# Результат: build/libs/chaosledger-<version>.jar
```

## Релизы

Релизы полностью автоматизированы тегами. Чтобы опубликовать новую версию:

```bash
git tag v0.2.0
git push origin v0.2.0
```

Workflow [release.yml](.github/workflows/release.yml) соберёт jar с правильной
версией, создаст GitHub Release с автогенерированным changelog и приложит артефакты.
Pre-release можно запустить вручную из вкладки *Actions → Release → Run workflow*.

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
- `loader_version=0.20.1`
- `loom_version=1.19-SNAPSHOT`
- `fabric_api_version=0.166.2+26.3`
- `java_version=25`
- **Gradle wrapper: 9.8.2** (Gradle 8.x не умеет читать Java 25 class files → `Unsupported class file major version 69`)
- mappings: **нет строки mappings** — MC 26.x поставляется разнеобфусцированным, Loom 1.19 потребляет Mojang names нативно

> Minecraft 26.x уже поставляется разнеобфусцированным (с 26.1), поэтому **Yarn
> устарел** и больше не обновляется, а Parchment для 26.x не нужен (нет обфускации,
> которую покрывать). Имена Mojang — канонический стандарт, Loom потребляет их
> напрямую без какой-либо `mappings`-строки в `build.gradle`. Это совпадает с
> официальным `fabric-example-mod` веткой 26.3.
>
> Версии Fabric API/Loom для MC 26.3 могут быть ещё не опубликованы в момент первого
> релиза. Если сборка падает с ошибкой резолва зависимости — подставьте актуальные
> build-номера в `gradle.properties` (см. [fabricmc.net/develop](https://fabricmc.net/develop)).
> build-номера в `gradle.properties`.

## Вклад в проект

- Хотите предложить фичу или завести баг? Откройте issue через один из шаблонов
  [Bug report](.github/ISSUE_TEMPLATE/bug_report.yml) /
  [Feature request](.github/ISSUE_TEMPLATE/feature_request.yml).
- Хотите законтрибьютить код? Прочитайте [CONTRIBUTING.md](CONTRIBUTING.md) —
  там описаны конвенции коммитов, стиль кода и процесс PR.
- Все участники соблюдают [Code of Conduct](CODE_OF_CONDUCT.md).
- Зависимости и CI-экшены обновляются автоматически через [Dependabot](.github/dependabot.yml).

## Структура репозитория

```
chaosledger/
├── .github/
│   ├── workflows/      — build.yml (CI), release.yml (релизы по тегам)
│   ├── ISSUE_TEMPLATE/ — bug_report, feature_request
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── FUNDING.yml
│   └── dependabot.yml
├── build.gradle, settings.gradle, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── README.md, BUILD.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md, LICENSE
└── src/main/...
```

> ⚠️ Перед первым пушем замените `OWNER` в бейджах и ссылках README/CONTRIBUTING
> на свой GitHub-username (или организацию), а email'ы в CONTRIBUTING.md и
> CODE_OF_CONDUCT.md на реальные. `gradle-wrapper.jar` коммитится в репозиторий
> (стандартная практика Fabric-модов) — CI дополнительно валидирует его через
> `gradle/actions/wrapper-validation`.

## Лицензия

[MIT](LICENSE) — © 2026 ChaosLedger Project. Используйте, форкайте, продавайте —
просто оставьте копирайт.

