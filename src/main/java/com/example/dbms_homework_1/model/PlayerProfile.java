package com.example.dbms_homework_1.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerProfile {

    private String id;
    private String name;
    private int level;
    private String region;
    private String createdAt;
}
