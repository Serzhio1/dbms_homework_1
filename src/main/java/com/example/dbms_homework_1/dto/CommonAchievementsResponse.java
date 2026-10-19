package com.example.dbms_homework_1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class CommonAchievementsResponse {

    private String playerId1;
    private String playerId2;
    private Set<String> common;
}
