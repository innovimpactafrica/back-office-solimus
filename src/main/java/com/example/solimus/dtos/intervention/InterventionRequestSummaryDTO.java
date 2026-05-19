package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.InterventionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterventionRequestSummaryDTO {
    private Long id;
    private String title;
    private String residenceName;
    private String status;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
