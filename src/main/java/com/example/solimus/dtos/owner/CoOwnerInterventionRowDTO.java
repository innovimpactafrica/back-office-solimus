package com.example.solimus.dtos.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerInterventionRowDTO {

    // Référence de l'intervention
    private String reference;

    // Catégorie (spécialité)
    private String category;

    // Référence de l'appartement
    private String apartmentReference;

    // Date de création
    private LocalDateTime date;

    // Statut composite (EN_ATTENTE, EN_COURS, RESOLU)
    private String status;

    // Nom du prestataire
    private String providerName;
}
