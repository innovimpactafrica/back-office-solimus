package com.example.solimus.dtos.owner.travaux;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// DTO représentant une étape de la timeline de suivi d'une intervention
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerTimelineStepDTO {

    private String type;
    private String label;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime date;

    private boolean completed;
}
