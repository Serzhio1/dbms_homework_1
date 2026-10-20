package com.example.dbms_homework_1.controller;

import com.example.dbms_homework_1.dto.BatchCreateRequest;
import com.example.dbms_homework_1.dto.BatchResponse;
import com.example.dbms_homework_1.dto.LevelRequest;
import com.example.dbms_homework_1.dto.LoginResponse;
import com.example.dbms_homework_1.dto.PlayerRequest;
import com.example.dbms_homework_1.model.PlayerProfile;
import com.example.dbms_homework_1.service.BatchService;
import com.example.dbms_homework_1.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final BatchService batchService;

    @PostMapping("/batch")
    public ResponseEntity<BatchResponse> createPlayersBatch(@Valid @RequestBody BatchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(batchService.createPlayers(request.getPlayers()));
    }

    @PostMapping("/{id}")
    public ResponseEntity<PlayerProfile> createPlayer(@PathVariable String id, @Valid @RequestBody PlayerRequest request) {
        boolean existed = playerService.exists(id);
        PlayerProfile profile = playerService.createOrUpdatePlayer(id, request);
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED).body(profile);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerProfile> getPlayer(@PathVariable String id) {
        return playerService.getPlayer(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player " + id + " not found"));
    }

    @PostMapping("/{id}/login")
    public ResponseEntity<LoginResponse> login(@PathVariable String id) {
        return ResponseEntity.ok(new LoginResponse(id, playerService.login(id)));
    }

    @PatchMapping("/{id}/level")
    public ResponseEntity<PlayerProfile> levelUp(@PathVariable String id, @Valid @RequestBody LevelRequest request) {
        return ResponseEntity.ok(playerService.levelUp(id, request.getDelta()));
    }
}
