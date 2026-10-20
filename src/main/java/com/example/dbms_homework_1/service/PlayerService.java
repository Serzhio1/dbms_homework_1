package com.example.dbms_homework_1.service;

import com.example.dbms_homework_1.dto.PlayerRequest;
import com.example.dbms_homework_1.model.PlayerProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private static final long CACHE_TTL_SECONDS = 60;
    private static final String PROFILE_KEY = "player:";
    private static final String CACHE_KEY = "cache:player:";
    private static final String LOGINS_KEY = "logins:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> incrementLoginsScript;
    private final NotificationService notificationService;

    public PlayerProfile createOrUpdatePlayer(String id, PlayerRequest request) {
        boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + id));

        Object existingCreatedAt = exists
                ? redisTemplate.opsForHash().get(PROFILE_KEY + id, "created_at")
                : null;
        String createdAt = existingCreatedAt != null
                ? existingCreatedAt.toString()
                : Instant.now().toString();

        PlayerProfile profile = PlayerProfile.builder()
                .id(id)
                .name(request.getName())
                .level(request.getLevel())
                .region(request.getRegion())
                .createdAt(createdAt)
                .build();

        redisTemplate.opsForHash().putAll(PROFILE_KEY + id, Map.of(
                "id", profile.getId(),
                "name", profile.getName(),
                "level", String.valueOf(profile.getLevel()),
                "region", profile.getRegion(),
                "created_at", profile.getCreatedAt()
        ));

        redisTemplate.delete(CACHE_KEY + id);

        log.info("{} player {}: HSET {} + DEL {}", exists ? "Updated" : "Created", id,
                PROFILE_KEY + id, CACHE_KEY + id);
        return profile;
    }

    public boolean exists(String id) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + id));
    }

    public long login(String id) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + id))) {
            throw new IllegalArgumentException("Player " + id + " does not exist");
        }

        Long count = redisTemplate.execute(incrementLoginsScript, List.of(), id);
        long logins = count == null ? 0L : count;

        log.info("Player {} logged in -> {} = {} (EVALSHA, atomic INCR + EXPIRE 86400)", id, LOGINS_KEY + id, logins);
        return logins;
    }

    public PlayerProfile levelUp(String id, long delta) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + id))) {
            throw new IllegalArgumentException("Player " + id + " does not exist");
        }

        Long newLevel = redisTemplate.opsForHash().increment(PROFILE_KEY + id, "level", delta);
        long level = newLevel == null ? 0L : newLevel;

        redisTemplate.delete(CACHE_KEY + id);

        notificationService.notify(id, "level_up", "Player " + id + " reached level " + level);

        log.info("Level up: HINCRBY {} level {} -> {} + DEL {} + XADD",
                PROFILE_KEY + id, delta, level, CACHE_KEY + id);

        return getPlayer(id).orElseThrow(() -> new IllegalArgumentException("Player " + id + " does not exist"));
    }

    public Optional<PlayerProfile> getPlayer(String id) {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY + id);
        if (cached != null) {
            log.info("Cache HIT for player {} ({})", id, CACHE_KEY + id);
            try {
                return Optional.of(objectMapper.readValue(cached, PlayerProfile.class));
            } catch (JacksonException e) {
                log.warn("Corrupted cache for player {}, falling back to hash: {}", id, e.getMessage());
                redisTemplate.delete(CACHE_KEY + id);
            }
        }

        Map<Object, Object> hash = redisTemplate.opsForHash().entries(PROFILE_KEY + id);
        if (hash.isEmpty()) {
            log.info("Cache MISS for player {} and hash not found", id);
            return Optional.empty();
        }

        PlayerProfile profile = PlayerProfile.builder()
                .id((String) hash.get("id"))
                .name((String) hash.get("name"))
                .level(Integer.parseInt((String) hash.get("level")))
                .region((String) hash.get("region"))
                .createdAt((String) hash.get("created_at"))
                .build();

        try {
            String json = objectMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(CACHE_KEY + id, json, Duration.ofSeconds(CACHE_TTL_SECONDS));
            log.info("Cache MISS for player {} -> HGETALL {} + SET {} EX {}", id, PROFILE_KEY + id, CACHE_KEY + id, CACHE_TTL_SECONDS);
        } catch (JacksonException e) {
            log.warn("Failed to serialize player {} for cache: {}", id, e.getMessage());
        }

        return Optional.of(profile);
    }
}
