package com.example.solimus.dtos.syndic.dashboard;

import lombok.Data;
import java.time.LocalDateTime;

//DTO d'une ligne du tableau "Activités Récentes"
@Data
public class ActivityRowDTO {
    private String type; // Libellé du type d'activité (Paiement, Incident, Document...)
    private String description; // Message de l'activité
    private String residenceName; // Nom de la résidence concernée
    private LocalDateTime occurredAt; // Date brute, pour tri
    private String relativeTime; // "Il y a 2h" — calculé après tri
    private String status; // Terminé, Attention, En cours, ou null si pas encore géré
}