package com.example.dbms_homework_1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AchievementResponse {

    private String playerId;
    private String name;
    private boolean added;
}
