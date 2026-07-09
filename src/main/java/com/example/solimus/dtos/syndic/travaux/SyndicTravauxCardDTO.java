package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

//DTO d'une carte incident dans la liste syndic
@Data
@Builder
public class SyndicTravauxCardDTO {
    private Long id;
    private String reference;
    private String title;
    private String description;
    private UrgencyLevel urgencyLevel;
    private InterventionStatus status;
    private String statusLabel;
    private String residenceName;
    private String positionLabel; // "Appartement 3B" ou nom équipement commun
    private String specialtyName;
    private String selectedProviderName; // null si pas encore assigné
    private LocalDateTime createdAt;
    private int photoCount;
    private int commentCount;
}
