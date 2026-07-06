package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO pour le détail d'un copropriétaire (en-tête + KPIs)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerDetailDTO {

    // -------------------------------------------------------------------------
    // EN-TÊTE
    // -------------------------------------------------------------------------

    private String fullName;

    private String photoUrl;

    // Nombre de résidences distinctes où le copropriétaire a des lots (restreint au syndic)
    private int residencesCount;

    // Nombre d'appartements (lots) du copropriétaire (restreint au syndic)
    private int apartmentsCount;

    // Statut calculé : "A_JOUR", "RETARD", "IMPAYE"
    private String status;

    // -------------------------------------------------------------------------
    // INFORMATIONS PERSONNELLES
    // -------------------------------------------------------------------------

    private String lastName;

    private String firstName;

    private String phone;

    private String email;

    private String address;

    // Date de la première acquisition chez ce syndic (MIN assignedAt)
    private LocalDateTime acquisitionDate;

    // -------------------------------------------------------------------------
    // 5 CARDS KPI
    // -------------------------------------------------------------------------

    // Nombre de lots (identique à apartmentsCount)
    private int lotsCount;

    // Charges annuelles estimées (basées sur le budget annuel et tantièmes)
    private BigDecimal annualCharges;

    // Solde actuel cumulé historique (inclut toutes charges, même pas encore échues)
    private BigDecimal currentBalance;

    // Paiements effectués cumulés historiques
    private BigDecimal paymentsMade;

    // Impayés (uniquement les charges dont la date d'échéance est dépassée)
    private BigDecimal unpaidAmount;
}
