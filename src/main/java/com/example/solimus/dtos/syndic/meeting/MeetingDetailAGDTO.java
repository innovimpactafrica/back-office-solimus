package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.QuorumStatus;
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
    private long presentCount;             // nombre présents physiquement (PRESENT)
    private long procurationsCount;        // nombre de procurations données (PROXY)
    private double participationRate;     // % tantième présent + procuration (réutilise pour Quorum)
    private QuorumStatus quorumStatus;                  // REACHED / NOT_REACHED, vs quorumObjectivePercentage
    private BigDecimal quorumObjectivePercentage;       // objectif fixé par le syndic à la création
    private long resolvedResolutionsCount; // X : points marqués "résolution" ET déjà traités
    private long totalResolutionsCount;    // Y : points marqués "résolution" au total

    // ----- Bloc "Informations générales" -----
    private BigDecimal budget;    // Budget.budgetTotal du budget actif de la résidence
    private String organizerName; // nom complet du syndic organisateur

    // ----- Bloc "Quorum" -----
    private long quorumPresentCount;       // = presentCount
    private long quorumProcurationsCount;  // = procurationsCount
    private long quorumAbsentCount;        // = convoquesCount - presentCount - procurationsCount

    // ----- Badges des onglets -----
    private long participantsTabCount;  // = convoquesCount
    private long agendaTabCount;        // = agendaItems.size(), TOUS les points
    private long resolutionsTabCount;   // = totalResolutionsCount, points marqués uniquement
    private long documentsTabCount;
    private long historyTabCount;       // nb d'entrées ActivityLog liées à cette réunion
}