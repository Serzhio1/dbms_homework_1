package com.example.dbms_homework_1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScoreRequest {

    @NotBlank(message = "playerId must not be blank")
    private String playerId;

    private long delta;
}
