package com.example.solimus.dtos.intervention;

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
public class WorkflowStepDTO {
    private String label;        // "Demande reçue"
    private boolean completed;   // cercle vert ou gris
    
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime date;  // date affichée en dessous
}
