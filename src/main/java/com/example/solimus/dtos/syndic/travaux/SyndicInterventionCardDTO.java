package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicInterventionCardDTO {

    private Long id;
    private String reference;
    private String title;
    private String description;

    private UrgencyLevel urgencyLevel;
    private String urgencyLabel;

    private InterventionStatus status;
    private String statusLabel;

    private String residenceName;
    private String propertyReference;      // rempli si APPARTEMENT
    private String commonFacilityName;     // rempli si PARTIE_COMMUNE
    private IncidentLocationType locationType;

    private String specialtyName;
    private String specialtyIcon;

    private String selectedProviderName;   // null tant qu'aucun devis accepté

    private int photoCount;
    private int commentCount;

    private LocalDateTime createdAt;
}
