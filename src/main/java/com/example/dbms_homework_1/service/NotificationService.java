package com.example.dbms_homework_1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String STREAM_KEY = "notifications";

    private final StringRedisTemplate redisTemplate;

    public void notify(String playerId, String type, String message) {
        Map<String, String> fields = Map.of(
                "player_id", playerId,
                "type", type,
                "message", message,
                "timestamp", String.valueOf(Instant.now().toEpochMilli())
        );

        RecordId id = redisTemplate.opsForStream().add(STREAM_KEY, fields);
        log.info("Notification: XADD {} * -> {} ({})", STREAM_KEY, id, fields);
    }
}
