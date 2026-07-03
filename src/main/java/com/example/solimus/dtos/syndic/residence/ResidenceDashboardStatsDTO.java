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

    // Cumul historique des encaissements (somme des paidAmount des ChargeCallItem)
    // NOTE : ceci est un cumul provisoire en attendant le module Wallet.
    // Ce champ ne baisse jamais (sauf annulation de paiement).
    // Pas de sous-texte de variation pour l'instant (pas d'historisation mensuelle).
    private BigDecimal globalTreasury;

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
