package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour les statistiques globales du dashboard résidences
 * Contient les 5 KPIs du bandeau supérieur
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceDashboardStatsDTO {
    // Nombre total de résidences gérées par le syndic connecté
    private Long totalResidences;

    // Nombre total d'appartements (lots) dans toutes les résidences du syndic
    private Long totalApartments;

    // Trésorerie globale actuelle basée sur le module Wallet
    // Somme des transactions (positives pour entrées, négatives pour sorties)
    private BigDecimal globalTreasury;

    // Variation de la trésorerie en pourcentage par rapport au mois précédent
    // Peut être négatif (ex: -12.50 pour une baisse) — le front gère l'affichage (couleur/flèche)
    private BigDecimal variationTresoreriePourcentage;

    // Nombre de résidences ayant au moins un impayé (ChargeCallItem non payé)
    private Long residencesWithUnpaid;

    // Pourcentage de résidences avec impayés (calculé en mémoire)
    private Double percentageResidencesWithUnpaid;

    // Nombre total d'interventions ouvertes (non clôturées ni annulées)
    private Long openInterventions;

    // Nombre d'interventions en cours (statut STARTED)
    private Long inProgressInterventions;

    // Nombre d'interventions planifiées (statut PENDING)
    private Long pendingInterventions;
}
