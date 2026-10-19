package com.example.dbms_homework_1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AchievementRequest {

    @NotBlank(message = "name must not be blank")
    private String name;
}
