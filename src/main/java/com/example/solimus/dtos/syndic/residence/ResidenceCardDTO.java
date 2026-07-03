package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.ResidenceHealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour une carte résidence dans le listing du dashboard
 * Contient les informations affichées sur chaque carte de résidence
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceCardDTO {
    // Identifiant de la résidence
    private Long id;

    // Nom de la résidence
    private String name;

    // Ville de la résidence
    private String city;

    // URL de la photo (MinIO)
    private String photoUrl;

    // Statut de santé calculé à la volée (CRITIQUE, ATTENTION, EXCELLENT)
    private ResidenceHealthStatus healthStatus;

    // Nombre d'appartements (lots) dans cette résidence
    private Long appartementsCount;

    // Taux d'impayés en montant (calculé depuis ChargeCallItem)
    private Double tauxImpayes;

    // Cumul des encaissements pour cette résidence (somme des paidAmount)
    // NOTE : cumul provisoire en attendant le module Wallet
    private BigDecimal tresorerie;

    // Nombre d'interventions ouvertes pour cette résidence (non clôturées ni annulées)
    private Long openInterventions;
}
