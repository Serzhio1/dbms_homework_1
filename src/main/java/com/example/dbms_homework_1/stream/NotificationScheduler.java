package com.example.dbms_homework_1.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XTrimOptions;
import org.springframework.data.redis.connection.stream.RecordId;
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
            long minId = Instant.now().minusSeconds(7 * 24 * 3600).toEpochMilli();
            XTrimOptions trimOptions = XTrimOptions.trim(TrimOptions.minId(RecordId.of(minId, 0)).approximate());
            Long removed = redisTemplate.opsForStream().trim("notifications", trimOptions);
            log.debug("Trimmed {} old notification(s) from stream", removed);
        } catch (Exception e) {
            log.warn("Failed to trim notifications stream: {}", e.getMessage());
        }
    }
}
