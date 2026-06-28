package com.example.solimus.dtos.provider.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//DTO représentant une étape du workflow d'une demande de travail
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowStepDTO {

    private String label; // "Demande reçue", "Devis envoyé"...

    private boolean completed; // true = déjà atteint, false = pas encore

    @JsonFormat(pattern = "dd MMM yyyy HH:mm")
    private LocalDateTime date; // null si pas encore atteint
}