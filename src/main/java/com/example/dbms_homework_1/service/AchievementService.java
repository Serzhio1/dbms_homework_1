package com.example.dbms_homework_1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private static final String PROFILE_KEY = "player:";
    private static final String ACHIEVEMENTS_KEY = "achievements:";

    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;

    public boolean addAchievement(String id, String name) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + id))) {
            throw new IllegalArgumentException("Player " + id + " does not exist");
        }

        Long added = redisTemplate.opsForSet().add(ACHIEVEMENTS_KEY + id, name);
        boolean isNew = added != null && added > 0;

        if (isNew) {
            notificationService.notify(id, "achievement", "Player " + id + " earned achievement '" + name + "'");
        }

        log.info("Add achievement: SADD {} {} -> {}", ACHIEVEMENTS_KEY + id, name, isNew ? "added" : "already present");
        return isNew;
    }

    public boolean hasAchievement(String id, String name) {
        Boolean has = redisTemplate.opsForSet().isMember(ACHIEVEMENTS_KEY + id, name);
        boolean result = Boolean.TRUE.equals(has);

        log.info("Check achievement: SISMEMBER {} {} -> {}", ACHIEVEMENTS_KEY + id, name, result);
        return result;
    }

    public Set<String> commonAchievements(String id1, String id2) {
        Set<String> common = redisTemplate.opsForSet()
                .intersect(ACHIEVEMENTS_KEY + id1, ACHIEVEMENTS_KEY + id2);
        Set<String> result = common == null ? Set.of() : common;

        log.info("Common achievements: SINTER {} {} -> {} entries",
                ACHIEVEMENTS_KEY + id1, ACHIEVEMENTS_KEY + id2, result.size());
        return result;
    }
}
