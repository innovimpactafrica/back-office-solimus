package com.example.solimus.dtos.syndic.meeting;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

// ===== DTO CARTE ASSEMBLEE GENERALE (liste/dashboard) =====
// Représente une carte AG affichée dans la liste, avec toutes les infos resumées
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AGCardDTO {

    private Long id;                    // identifiant technique de la reunion

    private String title;               // titre de l'AG saisi à la creation
    private String residenceName;       // nom de la residence concernée
    private Long residenceId;           // id residence, utile pour navigation cote front

    private String status;              // valeur technique du MeetingStatus (ex: "UPCOMING")
    private String statusLabel;         // libelle affichable du statut (ex: "à venir")

    private String type;                // valeur technique du MeetingType (ex: "ORDINARY")
    private String typeLabel;           // libelle affichable du type (ex: "Ordinaire")

    private LocalDate meetingDate;      // date de la reunion
    private LocalTime startTime;        // heure de debut

    private String location;            // lieu de la réunion

    private long presentCount;           // X : nombre de participants ayant signé (hasSigned = true)
    private long totalParticipants;      // Y : nombre total de participants convoqués à l'AG
    private double participationRate;   // % de tantième présent, calculé via tantièmeSnapshot des présents

    private long resolutionsCount;       // nombre de points à l'ordre du jour (agendaItems.size())
    private long documentsCount;         // nombre de documents liés à l'AG
}
