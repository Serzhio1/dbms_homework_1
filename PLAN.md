# GameHub — Redis ДЗ №1: пошаговый план и трекер прогресса

> Файл-навигатор. Агент перед каждой задачей сверяется с ним: **что уже есть в кодовой базе**, **что ещё нужно сделать**.
> Отмечай `[x]` по мере выполнения. Цель — 10/10 по критериям из ТЗ.

## Краткое описание задачи (ТЗ)
Backend-платформа **«GameHub»**: REST API для управления игровыми турнирами, рейтингами игроков, достижениями и уведомлениями на Redis. Требуется:
- **Данные Redis**: профиль игрока — Hash, счётчик входов — String + Lua (INCR + EXPIRE 86400), кеш профиля — String JSON TTL 60с (Cache-Aside), лидерборд — Sorted Set, достижения — Set, уведомления — Stream (7 дней).
- **Эндпоинты (10 шт.)**: POST/GET/PATCH профили, POST login, POST leaderboard/score, GET top/rank, POST/GET достижения, POST batch (pipeline).
- **Инфраструктура**: Docker Compose (мастер 6379 с RDB+AOF+volatile-lru+min-replicas*, 2 реплики 6380/6381 read-only, 3 Sentinel с кворумом 2 на 26379–26381, Redis Insight 5540). Spring Boot приложение подключается **только через Sentinel**.
- **Функциональность**: атомарный Lua-скрипт для логинов, Cache-Aside для профилей, Stream с группой потребителя (XGROUP/XREADGROUP/XACK) и XTRIM по MINID (7 дней), пайплайн для массовой загрузки (10–20 профилей).
- **Инфраструктурные требования**: персистентность RDB+AOF на мастере, `min-replicas-to-write 1`/`min-replicas-max-lag 10` для защиты от split-brain, общая Docker-сеть, healthchecks, таймауты/ретраи при подключении, проверка соединения при старте.
- **Сдача**: код + docker-compose.yml + конфиги (redis.conf, sentinel.conf) + README.md с инструкцией по запуску и описанием архитектуры + видео-демо 5–10 мин (все сценарии: запуск, API, кеш, streams, failover, split-brain, pipeline).
- **Скрипт самопроверки**: `dzl_check.py` проверяет основные требования.
- **Оценка (10 баллов)**: инфраструктура (2), репликация/Sentinel (2), функциональность (2), кеш (1), streams (1), pipeline (1), split-brain (0.5), документация (0.5).

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
- [x] `.gitignore` есть; git-репозиторий инициализирован.
- [x] Структура пакетов `config/controller/service/model/dto` создана.
- [x] Фаза 0 (подготовка) + Фаза 1 (инфраструктура Docker: master/replica/sentinel/insight) + Фаза 2 (подключение приложения через Sentinel, Lua-скрипт, Stream-группа, пинг) — выполнены и протестированы (включая failover через `SENTINEL failover`, автопереподключение через Lettuce watchdog, транзиентные `NOREPLICAS` ошибки при переключении).
- [x] Фаза 3 (профиль игрока Hash + Cache-Aside), Фаза 4 (счётчик входов Lua), Фаза 5 (лидерборд Sorted Set), Фаза 6 (достижения Set), Фаза 7 (streams: продюсер `XADD`, консьюмер с `XACK`, MINID-trim) — выполнены и протестированы end-to-end.
- [x] Фаза 8 (pipeline: `BatchService` + `POST /api/players/batch` с замером времени, валидация 10–20, пропуск дубликатов) — выполнена и протестирована.
- [x] Фаза 9 (`README.md` на английском + `GlobalExceptionHandler` с единым форматом ошибок) — выполнена и протестирована.
- [ ] Фаза 10 (финальная проверка, видео) — ещё не сделана.
- Примечание: Spring Boot 4.1 использует Jackson 3 (`tools.jackson.*`), а не Jackson 2; добавлены `spring-boot-starter-validation` и `spring-boot-starter-jackson`.

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
  - [x] `app` (8080:8080) — сервис добавлен (в Фазе 2 вместе с Dockerfile)
- [x] Проверка: `docker compose up -d` + `docker compose ps` → 7 контейнеров Up (6 healthy)
- [x] Проверка: `INFO replication` → `connected_slaves:2` (`state=online`, `ip=redis-replica-N`)
- [x] Проверка: `SENTINEL get-master-addr-by-name mymaster` → `redis-master 6379` (на всех 3 Sentinel)
- [x] Проверка: `SENTINEL master` → `num-slaves=2`, `num-other-sentinels=2`, `quorum=2`
- [x] Проверка: запись на мастере реплицируется; запись на реплике → `READONLY`
- [x] Проверка: `CONFIG GET min-replicas-to-write` → `1`, `maxmemory-policy` → `volatile-lru`

## Фаза 2. Подключение приложения через Sentinel (критерий 2)
- [x] `application.yaml`: `spring.data.redis.sentinel.master=mymaster`, `.nodes=sentinel-1:26379,...`, таймауты, pool
- [x] `config/RedisConfig.java`:
  - [x] бин `DefaultRedisScript<Long>` для Lua (загрузка `scripts/increment_logins.lua`)
  - [x] стартовая проверка соединения (`PING`)
  - [x] идемпотентное создание группы `XGROUP CREATE notifications notifications-group $ MKSTREAM`
  - [x] запуск консьюмера и планировщика тримминга
- [x] Подтверждение: после failover приложение продолжает работать (Lettuce watchdog переподключился к новому мастеру)

## Фаза 3. Профили игроков (Hash + Cache-Aside) (критерии 3, 4)
- [x] `model/PlayerProfile.java`, `dto/PlayerRequest.java` (Bean Validation: `@NotBlank`, `@Min`)
- [x] `PlayerService`:
  - [x] `POST`: upsert по ТЗ — `HSET player:{id} name level region created_at` + `DEL cache:player:{id}` (201 при создании, 200 при обновлении; `created_at` сохраняется)
  - [x] `GET`: `GET cache:player:{id}` → hit вернуть JSON; miss: `HGETALL` + `SET cache:player:{id} {json} EX 60` (+ самовосстановление при повреждённом кэше)
- [x] `PlayerController`: `POST /api/players/{id}`, `GET /api/players/{id}`
- [x] Проверка: `TTL cache:player:{id}` > 0 (60), второй GET — hit, отсутствующий игрок → 404

## Фаза 4. Счётчик входов (String + INCR + Lua) (критерий 3)
- [x] `src/main/resources/scripts/increment_logins.lua`: `INCR` + `EXPIRE 86400`, вернуть значение
- [x] `PlayerService.login(id)` — выполнить скрипт (требует существования игрока → 404)
- [x] `POST /api/players/{id}/login` (ответ `{playerId, logins}`)
- [x] Проверка: `GET logins:{id}` растёт (1,2,3,…), `TTL` ≈ 86400 и обновляется при каждом входе

## Фаза 5. Лидерборд (Sorted Set) (критерий 3)
- [x] `LeaderboardService`: `ZINCRBY tournament:main`, `ZREVRANGE 0 9 WITHSCORES`, `ZRANK` (по ТЗ: место игрока — именно `ZRANK`, 0 = низший счёт)
- [x] `POST /api/leaderboard/score`, `GET /api/leaderboard/top?limit=10`, `GET /api/leaderboard/rank/{playerId}`
- [x] Проверка: топ-10 по убыванию, место игрока через `ZRANK` (0 = игрок с наименьшим счётом), отсутствующий → 404, несуществующий игрок при начислении → 404

## Фаза 6. Достижения (Set) (критерий 3)
- [x] `AchievementService`: `SADD`, `SISMEMBER`, `SINTER`
- [x] `POST /api/players/{id}/achievements` (требует существования игрока → 404; 201 + `added`)
- [x] `GET /api/players/{id}/achievements/{name}` (`SISMEMBER` → `{has}`)
- [x] `GET /api/players/{id1}/achievements/common/{id2}` (`SINTER` → `{playerId1, playerId2, common:[]}`)
- [x] Проверка: пересечение достижений (общие, пустое пересечение), дубликат `SADD` → `added=false`

## Фаза 7. Очередь уведомлений (Streams) (критерий 5)
- [x] `NotificationService`:
  - [x] `XADD notifications * player_id type message timestamp` (без `MAXLEN ~`, только MINID-trim)
  - [x] вызов при `PATCH level` (всегда) и `POST achievements` (только при новом достижении)
  - [x] scheduled `XTRIM notifications MINID <now_ms - 7*86400*1000>` (хранение 7 дней) — реализовано через `XTrimOptions`/`TrimOptions.minId`
- [x] `NotificationConsumer`: `XREADGROUP` → вывод в лог → `XACK` (авто-ack включён: `receiveAutoAck(...)`)
- [x] `PATCH /api/players/{id}/level` (`HINCRBY` + `DEL cache` + `XADD`, delta, 404 если игрока нет)
- [x] Проверка: `XLEN notifications` > 0, `XPENDING`/`XINFO GROUPS pending` = 0 (сообщения подтверждаются)
- [x] Побочно исправлен баг идемпотентности группы: `BUSYGROUP` теперь распознаётся по цепочке `cause` (раньше ложно логировалось "Failed to create stream group")

## Фаза 8. Конвейеризация (Pipeline) (критерий 6)
- [x] `BatchService`: `redisTemplate.executePipelined(...)` для пачки `HSET` (10–20 профилей)
- [x] `POST /api/players/batch` + замер времени (`elapsedMs`, `System.nanoTime()`)
- [x] Проверка: код использует pipeline, время замеряется (15 профилей → 1 round-trip, ~11ms)
- [x] Валидация: `@Size(min=10, max=20)` → иначе 400; дубликаты пропускаются (`skipped`)

## Фаза 9. Документация и качество (критерий 8)
- [x] `README.md`: архитектура, схема данных, запуск, описание API, сценарии демо
- [x] Комментарии в ключевых местах — **решено не добавлять** (по договорённости; логи в коде уже объясняют Redis-операции)
- [x] `GlobalExceptionHandler` (единый формат ошибок: timestamp/status/error/message/path + fieldErrors)

## Фаза 10. Проверка и сдача
- [ ] `./mvnw test` проходит
- [ ] Сценарии демо (см. README): запуск, API, кэш, streams, failover, split-brain, pipeline
- [ ] `git add . && git commit`
- [ ] Записать видео 5–10 мин по сценариям из ТЗ

---

## Контракт эндпоинтов (из ТЗ)
| Метод | Путь | Redis |
|-------|------|-------|
| POST | `/api/players/{id}` | upsert по ТЗ: `HSET player:{id}` + `DEL cache` (201 создание / 200 обновление) |
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
