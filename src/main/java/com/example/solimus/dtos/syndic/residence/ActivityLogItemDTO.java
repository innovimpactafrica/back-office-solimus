package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour un élément du journal d'activité
 * Utilisé pour le panneau "Activité Récente"
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityLogItemDTO {
    // ID de l'entrée de log
    private Long id;

    // Type d'activité (valeur brute de l'enum)
    private ActivityType type;

    // Message court affiché en gras
    private String message;

    // Détail supplémentaire (sous-texte)
    private String detail;

    // Nom complet de l'acteur (null si pas d'acteur)
    private String actorName;

    // Photo URL de l'acteur (null si pas d'acteur)
    private String actorPhotoUrl;

    // Date de création (brute, calcul relatif côté front)
    private LocalDateTime createdAt;
}
