package com.example.solimus.dtos.owner.signalement;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerSignalementSummaryDTO {

    private Long id;

    private String reference;

    private String title;

    private String residenceName;

    private String propertyReference;

    private String commonFacilityName;

    private SignalementStatus status;

    private String statusLabel;

    private UrgencyLevel urgencyLevel;

    private String urgencyLabel;

    private IncidentLocationType locationType;

    private LocalDateTime createdAt;
}
