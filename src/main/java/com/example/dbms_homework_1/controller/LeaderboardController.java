package com.example.dbms_homework_1.controller;

import com.example.dbms_homework_1.dto.LeaderboardEntry;
import com.example.dbms_homework_1.dto.RankResponse;
import com.example.dbms_homework_1.dto.ScoreRequest;
import com.example.dbms_homework_1.dto.ScoreResponse;
import com.example.dbms_homework_1.service.LeaderboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @PostMapping("/score")
    public ResponseEntity<ScoreResponse> addScore(@Valid @RequestBody ScoreRequest request) {
        return ResponseEntity.ok(leaderboardService.addScore(request));
    }

    @GetMapping("/top")
    public ResponseEntity<List<LeaderboardEntry>> getTop(@RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
        }
        return ResponseEntity.ok(leaderboardService.getTop(limit));
    }

    @GetMapping("/rank/{playerId}")
    public ResponseEntity<RankResponse> getRank(@PathVariable String playerId) {
        return leaderboardService.getRank(playerId)
                .map(rank -> ResponseEntity.ok(new RankResponse(playerId, rank)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player " + playerId + " has no leaderboard score"));
    }
}
