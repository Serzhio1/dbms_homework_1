package com.example.dbms_homework_1.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60000)
    public void trimOldNotifications() {
        try {
            long sevenDaysAgo = Instant.now().minusSeconds(7 * 24 * 3600).toEpochMilli();
            redisTemplate.opsForStream().trim("notifications", sevenDaysAgo, true);
        } catch (Exception e) {
            log.warn("Failed to trim notifications stream: {}", e.getMessage());
        }
    }
}
