package com.example.solimus.dtos.syndic.meeting;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

// ===== DTO CARTE ASSEMBLEE GENERALE (liste/dashboard) =====
// Represente une carte AG affichee dans la liste, avec toutes les infos resumees
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AGCardDTO {

    private Long id;                    // identifiant technique de la reunion

    private String title;               // titre de l'AG saisi a la creation
    private String residenceName;       // nom de la residence concernee
    private Long residenceId;           // id residence, utile pour navigation cote front

    private String status;              // valeur technique du MeetingStatus (ex: "UPCOMING")
    private String statusLabel;         // libelle affichable du statut (ex: "A venir")

    private String type;                // valeur technique du MeetingType (ex: "ORDINARY")
    private String typeLabel;           // libelle affichable du type (ex: "Ordinaire")

    private LocalDate meetingDate;      // date de la reunion
    private LocalTime startTime;        // heure de debut

    private String location;            // lieu de la reunion

    private int presentCount;           // X : nombre de participants ayant signe (hasSigned = true)
    private int totalParticipants;      // Y : nombre total de participants convoques a l'AG
    private double participationRate;   // % de tantieme present, calcule via tantiemeSnapshot des presents

    private int resolutionsCount;       // nombre de points a l'ordre du jour (agendaItems.size())
    private int documentsCount;         // nombre de documents lies a l'AG
}
