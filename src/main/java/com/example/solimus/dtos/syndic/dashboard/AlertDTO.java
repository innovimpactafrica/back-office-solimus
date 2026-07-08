package com.example.solimus.dtos.syndic.dashboard;

import lombok.Data;

import java.time.LocalDateTime;

//DTO d'une alerte affichée sur le tableau de bord
@Data
public class AlertDTO {
    private String type; // UNPAID, MEETING
    private String title; // "Impayé Important", "AG à préparer"
    private String description; // "Résidence Montparnasse - 12 500 000 FCFA"
    private LocalDateTime occurredAt; // Date brute, utilisée pour le tri (pas affichée telle quelle)
    private String relativeTime; // "Il y a 1h" — calculé pour l'affichage, après tri
}