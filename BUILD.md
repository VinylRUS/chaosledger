# Сборка ChaosLedger

## Требования

- **JDK 25** (или новее). Проверьте: `java -version`.
- Интернет для скачивания Gradle wrapper'а и зависимостей Minecraft/Fabric (первый запуск).
- Около 1 ГБ свободной памяти для Gradle (`org.gradle.jvmargs=-Xmx2G` уже выставлено).

## Шаг 1. Получить `gradle-wrapper.jar`

В проекте уже есть `gradlew`, `gradlew.bat` и `gradle/wrapper/gradle-wrapper.properties`,
но сам бинарник `gradle-wrapper.jar` нельзя сгенерировать текстом. Получите его одним из способов:

**Вариант A — Gradle уже установлен локально** (рекомендуется):
```bash
cd chaosledger
gradle wrapper --gradle-version 8.14
```

**Вариант B — скачать из любого работающего Gradle-проекта или с центрального репозитория**:
```bash
curl -L -o chaosledger/gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.14.0/gradle/wrapper/gradle-wrapper.jar
```

После этого `./gradlew --version` должен работать.

## Шаг 2. Собрать .jar

Из корня проекта (`chaosledger/`):

```bash
./gradlew build
```

Готовый мод лежит в:

```
chaosledger/build/libs/chaosledger-0.1.0.jar
```

Этот файл и есть Fabric-мод, который кладётся в `mods/` сервера.

Дополнительно генерируется `chaosledger-0.1.0-sources.jar` — архив исходников.

## Шаг 3. Установить на сервер

1. Скопировать `chaosledger-0.1.0.jar` в `<server>/mods/`.
2. Убедиться, что в `mods/` лежит **Fabric API** той же ветки, что и Minecraft 26.3.
3. Запустить сервер. При первом старте создастся:
   - `<server>/chaosledger.db` — SQLite база,
   - `<server>/config/chaosledger/chaosledger.properties` — конфигурация.

## Шаг 4. Конфигурация

`<server>/config/chaosledger/chaosledger.properties`:

```properties
defaultCurrency=CC
startingBalance=100.0
databaseFile=chaosledger.db
httpEnabled=true
httpBindHost=127.0.0.1
httpPort=8775
httpApiKey=
priceElasticity=0.05
priceMinFactor=0.25
priceMaxFactor=4.0
allowNegativeBalance=false
```

- `httpApiKey=` (пусто) = режим без авторизации (только для локальной разработки).
  На проде обязательно задайте случайный ключ и проксируйте API через HTTPS.

## Шаг 5. Standalone-запуск (без Minecraft)

Полезно для тестирования БД и HTTP API без запуска Minecraft:

```bash
./gradlew shadowJar   # (опционально) собирает fat-jar со всеми зависимостями
# либо используйте собранный обычный jar + classpath sqlite-jdbc:
java -cp chaosledger-0.1.0.jar:libs/sqlite-jdbc-3.46.1.3.jar \
  dev.chaosledger.chaosledger.bootstrap.Standalone
```

Метод `Standalone.main` стартует SQLite + HTTP API и держит процесс живым.

## HTTP API — основные endpoints

| Метод | Путь                                          | Описание                            |
|------|-----------------------------------------------|-------------------------------------|
| GET  | `/api/health`                                  | liveness probe                      |
| GET  | `/api/currencies`                              | список валют                        |
| POST | `/api/currencies`                              | создать/обновить валюту             |
| GET  | `/api/accounts/top/{code}?limit=N`            | топ балансов                        |
| GET  | `/api/accounts/{uuid}`                         | счета игрока                        |
| GET  | `/api/accounts/{uuid}/{code}`                  | один счёт                          |
| POST | `/api/transactions/pay`                        | перевод между игроками             |
| POST | `/api/transactions/grant`                       | минтить деньги                     |
| POST | `/api/transactions/charge`                    | сжечь деньги                       |
| GET  | `/api/transactions/{uuid}?limit=N`            | история игрока                     |
| GET  | `/api/transactions/recent/{code}?limit=N`      | недавние транзакции                |
| GET  | `/api/market`                                   | все товары рынка                   |
| POST | `/api/market`                                   | определить товар                   |
| GET  | `/api/market/{itemId}`                         | один товар                         |
| POST | `/api/market/buy`                               | купить                             |
| POST | `/api/market/sell`                              | продать                            |
| GET  | `/api/market/{itemId}/history?limit=N`        | история цены                       |
| GET  | `/api/quests`                                   | список квестов                     |
| POST | `/api/quests`                                   | создать/обновить квест             |
| DELETE | `/api/quests/{id}`                             | удалить квест                       |
| GET  | `/api/quests/progress/{uuid}`                  | прогресс всех квестов игрока      |
| GET  | `/api/quests/progress/{uuid}/{id}`             | прогресс конкретного квеста        |
| POST | `/api/quests/available`                        | сделать квест доступным            |
| POST | `/api/quests/start`                             | начать квест                       |
| POST | `/api/quests/claim`                             | получить награду                  |
| POST | `/api/quests/fail`                              | провалить квест                   |
| POST | `/api/quests/event`                             | проглотить игровое событие         |
| GET  | `/api/rewards/{uuid}?limit=N`                  | история выданных наград           |
| POST | `/api/rewards/grant`                            | выдать награду вручную            |
| GET  | `/api/audit?limit=N`                            | последние записи аудита           |
| GET  | `/api/audit/{uuid}?limit=N`                    | записи аудита по актору            |

Пример — пополнить счёт игрока:

```bash
curl -X POST http://127.0.0.1:8775/api/transactions/grant \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: your-key" \
  -d '{"playerUuid":"00000000-0000-0000-0000-000000000001","playerName":"test","currency":"CC","amount":50.0,"reason":"Welcome bonus"}'
```

## Частые проблемы

- **`Could not resolve net.fabricmc:fabric-loom:1.17.5`** — версия Loom ещё не опубликована
  под Minecraft 26.3. Проверьте `https://maven.fabricmc.net/net/fabricmc/fabric-loom/` и
  подставьте актуальный номер в `gradle.properties` → `loom_version`.
- **`Could not resolve net.fabricmc.fabric-api:fabric-api:...`** — то же самое для Fabric API.
  См. `https://modrinth.com/mod/fabric-api/versions`.
- **`yarn_mappings=26.3+build.1`** — проверьте актуальный build на
  `https://maven.fabricmc.net/net/fabricmc/yarn/`.
- **SQLite: `org.sqlite.JDBC is not on the classpath`** — на standalone-запуске
  обязательно добавьте `sqlite-jdbc` в classpath (внутри Fabric-сервера он встроен
  через `include` в `build.gradle`).

## Что доделать в следующих версиях

- Веб-панель (отдельный репозиторий) — графики цен, UI квестов, таблицы игроков.
- Полноценный mixin/event hook для игровых событий (pickup, kill, travel), сейчас
  события подаются через `QuestEvent`-DTO и HTTP `/api/quests/event`.
- Кликабельная chest-GUI (pay/claim из меню) — сейчас меню read-only.
- Полноценная миграция схемы через `schema_version`.
- Опция переключения БД на PostgreSQL/MySQL.
