package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.ResidenceHealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour les statistiques du bandeau d'indicateurs d'une résidence
 * Appelé une seule fois au chargement de l'écran, indépendamment de l'onglet actif
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceHeaderStatsDTO {
    // Identité de la résidence (affichée en permanence au-dessus des onglets)
    private String name;
    private String photoUrl;
    private String fullAddress;
    private String city;

    // Badge de statut affiché sur la photo
    private ResidenceHealthStatus healthStatus;

    // Indicateurs chiffrés du bandeau
    private Long totalApartments;
    private BigDecimal annualBudget;
    private Long coOwnersCount;
    private Long worksInProgress;
    private Long pendingQuotes;
    private Long openIncidents;
}
