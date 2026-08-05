package com.example.solimus.dtos.admin.withdrawal;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Bloc 3, une étape du "Suivi de la demande" =====
// Tous les champs sauf "label" et "completed" sont optionnels — leur présence dépend de l'étape
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineStepDTO {

    private String label;
    private LocalDateTime date;
    private String actor;//Construit dynamiquement : "Prénom Nom (Rôle)" 
    private String subtitle;
    private String reason;
    private boolean completed;
}