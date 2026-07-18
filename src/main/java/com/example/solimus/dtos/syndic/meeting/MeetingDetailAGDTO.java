package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// ===== DTO DETAIL D'UNE AG (onglet Vue Generale de la modale) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDetailAGDTO {

    // ----- En-tete -----
    private Long id;
    private String title;
    private String residenceName;
    private String status;       // valeur technique (ex: "UPCOMING")
    private String statusLabel;  // libelle (ex: "Planifiée")
    private String type;         // valeur technique (ex: "ORDINARY")
    private String typeLabel;    // libelle (ex: "Ordinaire")
    private LocalDate meetingDate;
    private LocalTime startTime;
    private String location;

    // ----- KPIs du haut -----
    private long convoquesCount;           // total participants convoqués
    private long presentCount;             // nombre ayant signé
    private double participationRate;     // % tantième présent (réutilise pour Quorum)
    private long resolvedResolutionsCount; // X : points marqués "résolution" ET déjà traités
    private long totalResolutionsCount;    // Y : points marqués "résolution" au total

    // ----- Bloc "Informations générales" -----
    private BigDecimal budget;    // Budget.budgetTotal du budget actif de la résidence
    private String organizerName; // nom complet du syndic organisateur

    // ----- Bloc "Quorum" -----
    private long quorumPresentCount;  // = presentCount
    private long quorumAbsentCount;   // = convoquesCount - presentCount

    // ----- Badges des onglets -----
    private long participantsTabCount;  // = convoquesCount
    private long agendaTabCount;        // = agendaItems.size(), TOUS les points
    private long resolutionsTabCount;   // = totalResolutionsCount, points marqués uniquement
    private long documentsTabCount;
    private long historyTabCount;       // nb d'entrées ActivityLog liées à cette réunion
}