package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour un item de l'historique des interventions d'un bien commun
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterventionHistoryItemDTO {
    // Titre de l'intervention
    private String title;

    // Date de création de l'intervention (toujours renseignée)
    private LocalDateTime date;

    // Nom du prestataire sélectionné (peut être null)
    private String provider;

    // Statut de l'intervention (libellé français de l'enum)
    private String status;
}
