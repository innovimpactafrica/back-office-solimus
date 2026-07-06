package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingCardDTO {

    // Titre de l'AG
    private String title;

    // Nom de la résidence
    private String residenceName;

    // Statut de l'AG
    private MeetingStatus status;

    // Type de l'AG
    private MeetingType type;

    // Date de l'AG
    private LocalDate meetingDate;

    // Heure de début
    private LocalTime startTime;

    // Lieu de l'AG
    private String location;

    // Ratio de participation (comptage de têtes, ex: "6/7")
    private String headcountRatio;

    // Pourcentage de quorum (pondéré par tantième, ex: 88.0)
    private Double quorumPercentage;

    // Nombre de résolutions (0 pour l'instant, sera rempli quand Resolution existera)
    private Integer resolutionsCount;

    // Nombre de documents
    private Integer documentsCount;
}
