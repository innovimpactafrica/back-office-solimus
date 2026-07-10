package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInterventionRequestDTO {

    private String title;
    private String description;
    private Long residenceId;
    private Long propertyId;
    private Long commonFacilityId;
    private Long specialtyId;
    private IncidentLocationType locationType;
    private InterventionManagementMode managementMode;
    private UrgencyLevel urgencyLevel;
    private List<String> photoUrls;
}
