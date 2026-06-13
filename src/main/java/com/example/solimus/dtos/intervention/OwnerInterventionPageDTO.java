package com.example.solimus.dtos.intervention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionPageDTO {
    private int totalIncidents;  // total toutes statuts
    private int enCoursCount;    // status = STARTED uniquement
    private Page<OwnerInterventionSummaryDTO> interventions;
}
