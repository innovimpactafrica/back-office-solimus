package com.example.solimus.dtos.syndic.signalement;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
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
public class SyndicSignalementCardDTO {

    private Long id;
    private String reference;
    private String title;

    private String propertyReference;
    private String commonFacilityName;
    private IncidentLocationType locationType;

    private String residenceName;

    private String declaredByName;

    private UrgencyLevel urgencyLevel;
    private String urgencyLabel;

    private SignalementStatus status;
    private String statusLabel;

    private LocalDateTime createdAt;
}
