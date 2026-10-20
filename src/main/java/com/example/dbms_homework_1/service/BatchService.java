package com.example.dbms_homework_1.service;

import com.example.dbms_homework_1.dto.BatchPlayerRequest;
import com.example.dbms_homework_1.dto.BatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    private static final String PROFILE_KEY = "player:";

    private final StringRedisTemplate redisTemplate;

    public BatchResponse createPlayers(List<BatchPlayerRequest> players) {
        List<BatchPlayerRequest> toCreate = new ArrayList<>();
        int skipped = 0;
        for (BatchPlayerRequest player : players) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + player.getId()))) {
                skipped++;
                log.info("Batch: player {} already exists, skipped", player.getId());
            } else {
                toCreate.add(player);
            }
        }

        long start = System.nanoTime();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] now = Instant.now().toString().getBytes(StandardCharsets.UTF_8);
            for (BatchPlayerRequest player : toCreate) {
                byte[] key = (PROFILE_KEY + player.getId()).getBytes(StandardCharsets.UTF_8);
                connection.hSet(key, "id".getBytes(StandardCharsets.UTF_8), player.getId().getBytes(StandardCharsets.UTF_8));
                connection.hSet(key, "name".getBytes(StandardCharsets.UTF_8), player.getName().getBytes(StandardCharsets.UTF_8));
                connection.hSet(key, "level".getBytes(StandardCharsets.UTF_8), String.valueOf(player.getLevel()).getBytes(StandardCharsets.UTF_8));
                connection.hSet(key, "region".getBytes(StandardCharsets.UTF_8), player.getRegion().getBytes(StandardCharsets.UTF_8));
                connection.hSet(key, "created_at".getBytes(StandardCharsets.UTF_8), now);
            }
            return null;
        });

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        BatchResponse response = new BatchResponse(toCreate.size(), skipped, elapsedMs);

        log.info("Batch pipeline: HSET x{} in one round-trip -> created={}, skipped={}, elapsed={}ms",
                toCreate.size(), response.created(), response.skipped(), response.elapsedMs());
        return response;
    }
}
