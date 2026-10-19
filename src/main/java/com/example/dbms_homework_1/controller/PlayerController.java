package com.example.dbms_homework_1.controller;

import com.example.dbms_homework_1.dto.PlayerRequest;
import com.example.dbms_homework_1.model.PlayerProfile;
import com.example.dbms_homework_1.service.PlayerService;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping("/{id}")
    public ResponseEntity<PlayerProfile> createPlayer(@PathVariable String id, @Valid @RequestBody PlayerRequest request) {
        try {
            PlayerProfile profile = playerService.createPlayer(id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(profile);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerProfile> getPlayer(@PathVariable String id) {
        return playerService.getPlayer(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player " + id + " not found"));
    }
}
