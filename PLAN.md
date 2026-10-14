# GameHub — Redis ДЗ №1: пошаговый план и трекер прогресса

> Файл-навигатор. Агент перед каждой задачей сверяется с ним: **что уже есть в кодовой базе**, **что ещё нужно сделать**.
> Отмечай `[x]` по мере выполнения. Цель — 10/10 по критериям из ТЗ.

## Целевая архитектура (из ТЗ)
- 1 мастер Redis (персистентность RDB + AOF, volatile-lru, защита от split-brain).
- 2 реплики (read-only).
- 3 Sentinel (кворум 2) с автоматическим failover.
- Redis Insight (опционально).
- Spring Boot REST API, подключается к Redis **только через Sentinel**.
- Упор на Redis: Hash, String+INCR+Lua, Sorted Set, Set, Streams, Cache-Aside, Pipeline.

## Критерии оценки (карта на файлы/шаги)
| № | Критерий | Баллы | Где закрывается |
|---|----------|-------|-----------------|
| 1 | Docker Compose (все Up) | 2 | `docker-compose.yml`, `docker/redis/**` |
| 2 | Репликация и Sentinel | 2 | conf-файлы, проверки в README |
| 3 | Профили, лидерборд, достижения | 2 | `PlayerService`, `LeaderboardService`, `AchievementService`, контроллеры |
| 4 | Кеширование | 1 | `PlayerService` cache-aside, `application.yaml` |
| 5 | Очередь уведомлений (Streams) | 1 | `NotificationService`, `NotificationConsumer` |
| 6 | Конвейеризация | 1 | `BatchService` |
| 7 | Защита от split-brain | 0.5 | `redis.conf` (`min-replicas-*`) |
| 8 | Документация и качество кода | 0.5 | `README.md`, комментарии |

## Текущее состояние кодовой базы
- [x] Spring Boot 4.1.1 / Java 23, Maven wrapper (`mvnw`), стартовый `DbmsHomework1Application`.
- [x] `pom.xml`: web + lombok + test + **`spring-boot-starter-data-redis`** (Lettuce 7.5.2, без Jedis).
- [x] `src/main/resources/application.yaml` — пока пустой (только имя приложения).
- [x] `.gitignore` есть; git-репозиторий инициализирован.
- [x] Структура пакетов `config/controller/service/model/dto` и `docker/redis/{master,replica,sentinel}` создана.
- [ ] Всё остальное (инфраструктура, Redis-код, README) — отсутствует.

---

## Фаза 0. Подготовка
- [x] Добавить в `pom.xml` зависимость `spring-boot-starter-data-redis` (Lettuce 7.5.2).
- [x] `git init` в корне проекта.
- [x] Создать структуру пакетов: `config`, `controller`, `service`, `model`, `dto`.
- [x] Создать каталоги `docker/redis/{master,replica,sentinel}`.
- [x] Проверка: `./mvnw -DskipTests compile` → BUILD SUCCESS.

## Фаза 1. Инфраструктура Docker Compose (критерии 1, 2, 7)
- [x] `docker/redis/master/redis.conf`:
  - [x] `appendonly yes`, `appendfsync everysec`, `auto-aof-rewrite-percentage 100`
  - [x] RDB: `save 900 1`, `save 300 10`, `save 60 10000`
  - [x] `maxmemory 256mb`, `maxmemory-policy volatile-lru`
  - [x] `min-replicas-to-write 1`, `min-replicas-max-lag 10`  ← split-brain
- [x] `docker/redis/replica/redis.conf`:
  - [x] `replicaof redis-master 6379`, `replica-read-only yes`
  - [x] те же persistence/maxmemory/`min-replicas-*` (для нового мастера после promote)
- [x] `docker/redis/sentinel/sentinel.conf`:
  - [x] `sentinel monitor mymaster redis-master 6379 2`
  - [x] `down-after-milliseconds 5000`, `failover-timeout 10000`, `parallel-syncs 1`
  - [x] `sentinel resolve-hostnames yes`, `sentinel announce-hostnames yes`
- [x] `docker-compose.yml`:
  - [x] сеть `redis-net` (bridge)
  - [x] `redis-master` (6379:6379)
  - [x] `redis-replica-1` (6380:6379), `redis-replica-2` (6381:6379)
  - [x] `sentinel-1/2/3` (26379/26380/26381), запуск через копию конфига в writable-путь
  - [x] `redisinsight` (5540:5540)
  - [x] healthcheck + depends_on; именованные volumes для данных
  - [ ] `app` (8080:8080) — сервис добавим в Фазе 2 вместе с Dockerfile
- [x] Проверка: `docker compose up -d` + `docker compose ps` → 7 контейнеров Up (6 healthy)
- [x] Проверка: `INFO replication` → `connected_slaves:2` (`state=online`, `ip=redis-replica-N`)
- [x] Проверка: `SENTINEL get-master-addr-by-name mymaster` → `redis-master 6379` (на всех 3 Sentinel)
- [x] Проверка: `SENTINEL master` → `num-slaves=2`, `num-other-sentinels=2`, `quorum=2`
- [x] Проверка: запись на мастере реплицируется; запись на реплике → `READONLY`
- [x] Проверка: `CONFIG GET min-replicas-to-write` → `1`, `maxmemory-policy` → `volatile-lru`

## Фаза 2. Подключение приложения через Sentinel (критерий 2)
- [ ] `application.yaml`: `spring.data.redis.sentinel.master=mymaster`, `.nodes=sentinel-1:26379,...`, таймауты, pool
- [ ] `config/RedisConfig.java`:
  - [ ] бин `DefaultRedisScript<Long>` для Lua (загрузка `scripts/increment_logins.lua`)
  - [ ] стартовая проверка соединения (`PING`)
  - [ ] идемпотентное создание группы `XGROUP CREATE notifications notifications-group $ MKSTREAM`
  - [ ] запуск консьюмера и планировщика тримминга
- [ ] Подтверждение: после failover приложение продолжает работать

## Фаза 3. Профили игроков (Hash + Cache-Aside) (критерии 3, 4)
- [ ] `model/PlayerProfile.java`, `dto/PlayerRequest.java`
- [ ] `PlayerService`:
  - [ ] `POST`: `HSET player:{id} name level region created_at` + `DEL cache:player:{id}`
  - [ ] `GET`: `GET cache:player:{id}` → hit вернуть JSON; miss: `HGETALL` + `SET cache:player:{id} {json} EX 60`
- [ ] `PlayerController`: `POST /api/players/{id}`, `GET /api/players/{id}`
- [ ] Проверка: `TTL cache:player:{id}` > 0, второй GET — hit

## Фаза 4. Счётчик входов (String + INCR + Lua) (критерий 3)
- [ ] `src/main/resources/scripts/increment_logins.lua`: `INCR` + `EXPIRE 86400`, вернуть значение
- [ ] `PlayerService.login(id)` — выполнить скрипт
- [ ] `POST /api/players/{id}/login`
- [ ] Проверка: `GET logins:{id}` растёт, TTL ≈ 86400

## Фаза 5. Лидерборд (Sorted Set) (критерий 3)
- [ ] `LeaderboardService`: `ZINCRBY tournament:main`, `ZREVRANGE 0 9 WITHSCORES`, `ZRANK`
- [ ] `POST /api/leaderboard/score`, `GET /api/leaderboard/top?limit=10`, `GET /api/leaderboard/rank/{playerId}`
- [ ] Проверка: топ-10 и место игрока

## Фаза 6. Достижения (Set) (критерий 3)
- [ ] `AchievementService`: `SADD`, `SISMEMBER`, `SINTER`
- [ ] `POST /api/players/{id}/achievements`
- [ ] `GET /api/players/{id}/achievements/{name}`
- [ ] `GET /api/players/{id1}/achievements/common/{id2}`
- [ ] Проверка: пересечение достижений

## Фаза 7. Очередь уведомлений (Streams) (критерий 5)
- [ ] `NotificationService`:
  - [ ] `XADD notifications * player_id type message timestamp` (+ `MAXLEN ~`)
  - [ ] вызов при `PATCH level` и `POST achievements`
  - [ ] scheduled `XTRIM notifications MINID <now_ms - 7*86400*1000>` (хранение 7 дней)
- [ ] `NotificationConsumer`: `XREADGROUP` → вывод в лог → `XACK`
- [ ] `PATCH /api/players/{id}/level` (`HINCRBY` + `DEL cache` + `XADD`)
- [ ] Проверка: `XLEN notifications` > 0, есть xack

## Фаза 8. Конвейеризация (Pipeline) (критерий 6)
- [ ] `BatchService`: `redisTemplate.executePipelined(...)` для пачки `HSET` (10–20 профилей)
- [ ] `POST /api/players/batch` + замер времени
- [ ] Проверка: код использует pipeline, время замеряется

## Фаза 9. Документация и качество (критерий 8)
- [ ] `README.md`: архитектура, схема данных, запуск, описание API, сценарии демо
- [ ] Комментарии в ключевых местах (Redis-операции объяснены)
- [ ] `GlobalExceptionHandler` (аккуратные ошибки)

## Фаза 10. Проверка и сдача
- [ ] `./mvnw test` проходит
- [ ] Сценарии демо (см. README): запуск, API, кэш, streams, failover, split-brain, pipeline
- [ ] `git add . && git commit`
- [ ] Записать видео 5–10 мин по сценариям из ТЗ

---

## Контракт эндпоинтов (из ТЗ)
| Метод | Путь | Redis |
|-------|------|-------|
| POST | `/api/players/{id}` | `HSET player:{id}` + `DEL cache` |
| GET | `/api/players/{id}` | `GET cache` → miss `HGETALL` + `SET EX 60` |
| PATCH | `/api/players/{id}/level` | `HINCRBY` + `DEL cache` + `XADD` |
| POST | `/api/players/{id}/login` | Lua `INCR` + `EXPIRE 86400` |
| POST | `/api/leaderboard/score` | `ZINCRBY tournament:main` |
| GET | `/api/leaderboard/top?limit=10` | `ZREVRANGE 0 9 WITHSCORES` |
| GET | `/api/leaderboard/rank/{playerId}` | `ZRANK` |
| POST | `/api/players/{id}/achievements` | `SADD` |
| GET | `/api/players/{id}/achievements/{name}` | `SISMEMBER` |
| GET | `/api/players/{id1}/achievements/common/{id2}` | `SINTER` |
| POST | `/api/players/batch` | pipeline `HSET` |

## Структуры данных (из ТЗ)
| Данные | Тип | Ключ | TTL |
|--------|-----|------|-----|
| Профиль | Hash | `player:{id}` | ∞ |
| Счётчик входов | String | `logins:{id}` | 24ч |
| Кеш профиля | String(JSON) | `cache:player:{id}` | 60с |
| Лидерборд | Sorted Set | `tournament:main` | ∞ |
| Достижения | Set | `achievements:{id}` | ∞ |
| Уведомления | Stream | `notifications` | 7 дней |
