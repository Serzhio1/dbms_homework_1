package com.example.dbms_homework_1.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BatchPlayerRequest {

    @NotBlank(message = "id must not be blank")
    private String id;

    @NotBlank(message = "name must not be blank")
    private String name;

    @Min(value = 1, message = "level must be at least 1")
    private int level;

    @NotBlank(message = "region must not be blank")
    private String region;
}
