package com.example.dbms_homework_1.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LevelRequest {

    @NotNull(message = "delta must not be null")
    private Long delta;
}
