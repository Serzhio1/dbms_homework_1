package com.example.dbms_homework_1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchCreateRequest {

    @NotEmpty(message = "players must not be empty")
    @Size(min = 10, max = 20, message = "players must contain between 10 and 20 items")
    private List<@Valid BatchPlayerRequest> players;
}
