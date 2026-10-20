package com.example.dbms_homework_1.service;

import com.example.dbms_homework_1.dto.LeaderboardEntry;
import com.example.dbms_homework_1.dto.ScoreRequest;
import com.example.dbms_homework_1.dto.ScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final String LEADERBOARD_KEY = "tournament:main";
    private static final String PROFILE_KEY = "player:";

    private final StringRedisTemplate redisTemplate;

    public ScoreResponse addScore(ScoreRequest request) {
        String playerId = request.getPlayerId();
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(PROFILE_KEY + playerId))) {
            throw new IllegalArgumentException("Player " + playerId + " does not exist");
        }

        Double newScore = redisTemplate.opsForZSet().incrementScore(LEADERBOARD_KEY, playerId, request.getDelta());
        double score = newScore == null ? 0.0 : newScore;

        log.info("Score update: ZINCRBY {} {} {} -> {}", LEADERBOARD_KEY, request.getDelta(), playerId, score);
        return new ScoreResponse(playerId, score);
    }

    public List<LeaderboardEntry> getTop(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, limit - 1L);

        List<LeaderboardEntry> entries = new ArrayList<>();
        long rank = 0;
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                entries.add(new LeaderboardEntry(rank++, tuple.getValue(),
                        tuple.getScore() == null ? 0.0 : tuple.getScore()));
            }
        }

        log.info("Top {}: ZREVRANGE {} 0 {} WITHSCORES -> {} entries",
                limit, LEADERBOARD_KEY, limit - 1, entries.size());
        return entries;
    }

    public Optional<Long> getRank(String playerId) {
        Long rank = redisTemplate.opsForZSet().rank(LEADERBOARD_KEY, playerId);
        log.info("Rank lookup: ZRANK {} {} -> {}", LEADERBOARD_KEY, playerId, rank);
        return Optional.ofNullable(rank);
    }
}
