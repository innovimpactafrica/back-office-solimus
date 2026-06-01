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
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionSummaryDTO {

    private Long id;
    private String title;
    private String residenceName;
    private String propertyReference;
    private String specialtyName;
    private InterventionStatus status;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
