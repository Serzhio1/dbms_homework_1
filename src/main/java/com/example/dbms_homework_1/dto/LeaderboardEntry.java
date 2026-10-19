package com.example.dbms_homework_1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeaderboardEntry {

    private long rank;
    private String playerId;
    private double score;
}
