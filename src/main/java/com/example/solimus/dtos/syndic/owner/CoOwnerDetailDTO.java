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

    // Card 2 — Charges annuelles : part du copropriétaire dans le budget de l'année en cours,
    // pour sa/ses résidence(s) chez ce syndic
    private BigDecimal annualChargesAmount;

    // Année utilisée pour le calcul ci-dessus (année en cours côté back, jamais codée en dur au front)
    private Integer annualChargesYear;

    // Card 3 — Montant dû actuellement : SUM(totalDue - paidAmount) sur toutes les charges non
    // soldées (status != PAID), toutes années/résidences chez ce syndic — inclut les charges pas
    // encore échues (remplace l'ancien doublon Solde actuel / Impayés)
    private BigDecimal currentAmountDue;

    // Part de currentAmountDue venant uniquement des pénalités de retard déjà appliquées — 0 si aucune
    private BigDecimal currentPenaltyAmount;

    // Card 4 — Retard : nombre de jours depuis l'échéance de la charge non soldée la plus ancienne —
    // null si aucune charge en retard (afficher "À jour" côté front dans ce cas)
    private Integer delayDays;

    // Card 5 — Taux de paiement à échéance : % de charges soldées (status = PAID) payées avant/à
    // l'échéance, sur le total des charges soldées — null si aucune charge n'a jamais été payée
    // (pas d'historique, carte à masquer côté front)
    private Integer onTimePaymentRate;
}