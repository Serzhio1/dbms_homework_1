package com.example.dbms_homework_1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RankResponse {

    private String playerId;
    private long rank;
}
