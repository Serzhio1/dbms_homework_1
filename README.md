# GameHub

REST API для управления турнирами, рейтингами игроков, достижениями и уведомлениями на Redis. В проекте показаны структуры данных Redis: **Hash, String + Lua, Sorted Set, Set, Streams, Cache-Aside, Pipeline**, а также отказоустойчивая топология: **1 master + 2 реплики + 3 Sentinel** с автоматическим переключением мастера.

---

## 1. Архитектура

Система запускается в одной Docker-сети и состоит из контейнеров:

| Контейнер | Порт | Назначение |
|-----------|------|------------|
| `gamehub-app` | 8080 | REST API (Spring Boot) |
| `redis-master` | 6379 | Redis master, принимает запись |
| `redis-replica-1` | 6380 | Реплика master, только чтение |
| `redis-replica-2` | 6381 | Реплика master, только чтение |
| `sentinel-1` | 26379 | Sentinel (мониторинг master) |
| `sentinel-2` | 26380 | Sentinel (мониторинг master) |
| `sentinel-3` | 26381 | Sentinel (мониторинг master) |
| `redisinsight` | 5540 | Веб-интерфейс для просмотра данных (опционально) |

Схема взаимодействия:

```
        ┌──────────────┐
        │  gamehub-app │  HTTP :8080
        └──────┬───────┘
               │
               │ 1. "Кто сейчас master?"
               ▼
    ┌─────────────────────┐
    │ sentinel-1/2/3      │  кворум = 2
    │ ответ: redis-master │
    └──────────┬──────────┘
               │
               │ 2. чтение и запись
               ▼
    ┌─────────────────────┐       репликация        ┌─────────────────┐
    │    redis-master     │ ───────────────────────► │ redis-replica-1 │
    │   запись разрешена  │ ───────────────────────► │ redis-replica-2 │
    └─────────────────────┘                          └─────────────────┘
```

Приложение подключается к Redis **только через Sentinel**. Реплики работают в режиме `replica-read-only yes`. На мастере настроены `min-replicas-to-write 1` и `min-replicas-max-lag 10`: если мастер потерял связь со всеми репликами по этим правилам, он отказывает в записи — это снижает риск split-brain.

---

## 2. Стек

| Слой | Технология |
|------|------------|
| Язык | Java 23 |
| Фреймворк | Spring Boot 4.1.1 |
| Redis-клиент | Lettuce (через `spring-boot-starter-data-redis`) |
| База | Redis 7, Sentinel |
| Инфраструктура | Docker Compose |
| Сборка | Maven (wrapper `./mvnw`) |

---

## 3. Запуск

Требования: Docker и Docker Compose.

```bash
docker compose up -d --build
```

Проверка:

```bash
docker compose ps
docker exec sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
# ожидаемо: redis-master 6379
docker exec redis-master redis-cli INFO replication   # connected_slaves:2
```

---

## 4. Модель данных

| Данные | Тип Redis | Ключ | TTL |
|--------|-----------|------|-----|
| Профиль игрока | Hash | `player:{id}` | нет |
| Счётчик входов | String | `logins:{id}` | 24 ч (устанавливается Lua-скриптом) |
| Кеш профиля | String (JSON) | `cache:player:{id}` | 60 с |
| Лидерборд | Sorted Set | `tournament:main` | нет |
| Достижения | Set | `achievements:{id}` | нет |
| Уведомления | Stream | `notifications` | 7 дней (обрезка через `XTRIM MINID`) |

---

## 5. Справочник API

Базовый URL: `http://localhost:8080`

| № | Метод и путь | Операции Redis | Описание |
|---|--------------|----------------|----------|
| 1 | `POST /api/players/{id}` | `HSET` + `DEL cache` | Создание или обновление профиля (upsert) |
| 2 | `GET /api/players/{id}` | `GET cache` → при промахе `HGETALL` + `SET EX 60` | Чтение по схеме Cache-Aside |
| 3 | `PATCH /api/players/{id}/level` | `HINCRBY` + `DEL cache` + `XADD` | Изменение уровня, запись уведомления |
| 4 | `POST /api/players/{id}/login` | Lua: `INCR` + `EXPIRE 86400` | Атомарный счётчик входов |
| 5 | `POST /api/leaderboard/score` | `ZINCRBY tournament:main` | Добавление очков |
| 6 | `GET /api/leaderboard/top?limit=10` | `ZREVRANGE 0 9 WITHSCORES` | Топ игроков |
| 7 | `GET /api/leaderboard/rank/{playerId}` | `ZRANK tournament:main` | Место игрока (0 — наименьший счёт в смысле ZRANK) |
| 8 | `POST /api/players/{id}/achievements` | `SADD` | Выдать достижение (201) |
| 9 | `GET /api/players/{id}/achievements/{name}` | `SISMEMBER` | Есть ли достижение |
| 10 | `GET /api/players/{id1}/achievements/common/{id2}` | `SINTER` | Общие достижения |
| 11 | `POST /api/players/batch` | pipeline `HSET` (10–20 игроков) | Массовая загрузка за один round-trip |

### Примеры запросов

```bash
# 1. Создать или обновить игрока
curl -s -X POST localhost:8080/api/players/alice \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","level":3,"region":"EU"}'

# 2. Два чтения подряд: первый обычно cache MISS, второй — cache HIT
curl -s localhost:8080/api/players/alice
curl -s localhost:8080/api/players/alice

# 3. Счётчик входов (Lua: INCR + EXPIRE 86400)
curl -s -X POST localhost:8080/api/players/alice/login

# 4. Повышение уровня (пишется уведомление в stream)
curl -s -X PATCH localhost:8080/api/players/alice/level \
  -H 'Content-Type: application/json' -d '{"delta":2}'

# 5. Лидерборд
curl -s -X POST localhost:8080/api/leaderboard/score \
  -H 'Content-Type: application/json' -d '{"playerId":"alice","delta":100}'
curl -s 'localhost:8080/api/leaderboard/top?limit=10'
curl -s localhost:8080/api/leaderboard/rank/alice

# 6. Достижения
curl -s -X POST localhost:8080/api/players/alice/achievements \
  -H 'Content-Type: application/json' -d '{"name":"first-win"}'
curl -s localhost:8080/api/players/alice/achievements/first-win
curl -s localhost:8080/api/players/alice/achievements/common/bob

# 7. Batch через pipeline
curl -s -X POST localhost:8080/api/players/batch \
  -H 'Content-Type: application/json' \
  -d '{"players":[
        {"id":"p1","name":"Player1","level":1,"region":"EU"},
        {"id":"p2","name":"Player2","level":2,"region":"US"},
        {"id":"p3","name":"Player3","level":3,"region":"EU"},
        {"id":"p4","name":"Player4","level":4,"region":"US"},
        {"id":"p5","name":"Player5","level":5,"region":"EU"},
        {"id":"p6","name":"Player6","level":6,"region":"US"},
        {"id":"p7","name":"Player7","level":7,"region":"EU"},
        {"id":"p8","name":"Player8","level":8,"region":"US"},
        {"id":"p9","name":"Player9","level":9,"region":"EU"},
        {"id":"p10","name":"Player10","level":10,"region":"US"}]}'
```

---

## 6. Уведомления (Streams)

- Продюсер: `NotificationService` добавляет записи командой  
  `XADD notifications * player_id type message timestamp` при повышении уровня и при выдаче нового достижения.
- Группа потребителей `notifications-group`, consumer `consumer-1` создаётся при старте. `NotificationConsumer` читает через `XREADGROUP` и подтверждает обработку (`XACK`) в режиме auto-ack контейнера слушателя.
- Хранение: `NotificationScheduler` раз в минуту обрезает старые записи через `XTRIM notifications MINID <now - 7 дней>`.

Проверка:

```bash
docker exec redis-master redis-cli XLEN notifications
docker exec redis-master redis-cli XPENDING notifications notifications-group
docker compose logs --tail=100 app | grep "Notification received"
```

---

## 7. Видео-демонстрация

Видео с демонстрацией работы приложения: <https://disk.yandex.ru/i/107PgGsHouT4_g>
