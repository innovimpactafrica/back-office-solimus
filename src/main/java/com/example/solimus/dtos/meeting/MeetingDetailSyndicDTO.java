package com.example.solimus.dtos.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDetailSyndicDTO {

    // ============================================================
    // BANDEAU KPI
    // ============================================================

    // Nombre total de copropriétaires convoqués
    private Integer convocatedCount;

    // Nombre de présents
    private Integer presentCount;

    // Nombre de procurations (REPRESENTE)
    private Integer proxyCount;

    // Pourcentage de participation (comptage de têtes, sans pondération)
    private Double participationPercentage;

    // Pourcentage de quorum (pondéré par tantième)
    private Double quorumPercentage;

    // Résolutions (0/0 pour l'instant, sera rempli quand Resolution existera)
    private Integer resolvedCount;
    private Integer totalCount;

    // ============================================================
    // ONGLET "VUE GÉNÉRALE" — INFORMATIONS GÉNÉRALES
    // ============================================================

    // Montant du budget (null pour l'instant, dépend de Resolution/ExceptionalCall)
    private BigDecimal budgetAmount;

    // Nom de la résidence
    private String residenceName;

    // Date de l'AG
    private LocalDate meetingDate;

    // Heure de début
    private LocalTime startTime;

    // Lieu de l'AG
    private String location;

    // Nom de l'organisateur (syndic créateur)
    private String organizerName;

    // ============================================================
    // ONGLET "VUE GÉNÉRALE" — QUORUM
    // ============================================================

    // Présents (réutilisé du KPI)
    private Integer quorumPresentCount;

    // Procurations (réutilisé du KPI)
    private Integer quorumProxyCount;

    // Absents
    private Integer absentCount;
}
