package com.example.dbms_homework_1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoreResponse {

    private String playerId;
    private double score;
}
