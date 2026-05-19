package com.example.solimus.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponseDTO {
    private String message;
    private List<String> details;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private java.time.LocalDateTime timestamp;
    private int status;
}
