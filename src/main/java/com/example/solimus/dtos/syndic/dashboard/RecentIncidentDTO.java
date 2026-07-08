package com.example.solimus.dtos.syndic.dashboard;

import lombok.Data;
import java.time.LocalDateTime;

//DTO d'une ligne du tableau "Incidents Récents" (global syndic, toutes résidences)
@Data
public class RecentIncidentDTO {
    private Long id; // Identifiant de l'intervention
    private String title; // Titre du problème, ex: "Fuite dans appartement 3B"
    private String residenceName; // Nom de la résidence concernée
    private String status; // Libellé traduit du InterventionStatus (En cours, Ouvert, Résolu...)
    private String urgencyLevel; // Niveau d'urgence (LOW, MEDIUM, HIGH) — utile pour l'icône/couleur front
    private LocalDateTime createdAt; // Date de création de l'incident
}