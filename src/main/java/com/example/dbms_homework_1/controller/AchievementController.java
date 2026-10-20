package com.example.dbms_homework_1.controller;

import com.example.dbms_homework_1.dto.AchievementCheckResponse;
import com.example.dbms_homework_1.dto.AchievementRequest;
import com.example.dbms_homework_1.dto.AchievementResponse;
import com.example.dbms_homework_1.dto.CommonAchievementsResponse;
import com.example.dbms_homework_1.service.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @PostMapping("/{id}/achievements")
    public ResponseEntity<AchievementResponse> addAchievement(@PathVariable String id,
                                                              @Valid @RequestBody AchievementRequest request) {
        boolean added = achievementService.addAchievement(id, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AchievementResponse(id, request.getName(), added));
    }

    @GetMapping("/{id}/achievements/{name}")
    public ResponseEntity<AchievementCheckResponse> hasAchievement(@PathVariable String id,
                                                                   @PathVariable String name) {
        boolean has = achievementService.hasAchievement(id, name);
        return ResponseEntity.ok(new AchievementCheckResponse(id, name, has));
    }

    @GetMapping("/{id1}/achievements/common/{id2}")
    public ResponseEntity<CommonAchievementsResponse> commonAchievements(@PathVariable String id1,
                                                                         @PathVariable String id2) {
        return ResponseEntity.ok(new CommonAchievementsResponse(id1, id2,
                achievementService.commonAchievements(id1, id2)));
    }
}
