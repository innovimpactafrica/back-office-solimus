package com.example.solimus.dtos.syndic.signalement;


import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicSignalementDetailDTO {
    private Long id;
    private String reference;
    private String title;
    private String residenceName;
    private String propertyReference;
    private String commonFacilityName;
    private IncidentLocationType locationType;
    private String declaredByName;
    private String declaredByRole;
    private SignalementStatus status;
    private String statusLabel;
    private UrgencyLevel urgencyLevel;
    private String urgencyLabel;
    private String description;
    private List<String> photoUrls;
    private LocalDateTime createdAt;

}
