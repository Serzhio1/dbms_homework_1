package com.example.dbms_homework_1.service;

import com.example.dbms_homework_1.dto.PlayerRequest;
import com.example.dbms_homework_1.model.PlayerProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private static final long CACHE_TTL_SECONDS = 60;
    private static final String PROFILE_KEY = "player:";
    private static final String CACHE_KEY = "cache:player:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PlayerProfile createPlayer(String id, PlayerRequest request) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + id))) {
            throw new IllegalStateException("Player " + id + " already exists");
        }

        PlayerProfile profile = PlayerProfile.builder()
                .id(id)
                .name(request.getName())
                .level(request.getLevel())
                .region(request.getRegion())
                .createdAt(Instant.now().toString())
                .build();

        redisTemplate.opsForHash().putAll(PROFILE_KEY + id, Map.of(
                "id", profile.getId(),
                "name", profile.getName(),
                "level", String.valueOf(profile.getLevel()),
                "region", profile.getRegion(),
                "created_at", profile.getCreatedAt()
        ));

        redisTemplate.delete(CACHE_KEY + id);

        log.info("Created player {}: HSET {} + DEL {}", id, PROFILE_KEY + id, CACHE_KEY + id);
        return profile;
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
