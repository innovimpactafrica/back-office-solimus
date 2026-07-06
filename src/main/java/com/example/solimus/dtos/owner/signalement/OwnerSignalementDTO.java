package com.example.solimus.dtos.owner.signalement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerSignalementDTO {

    private long totalSignalements;
    private long enAttenteCount;

    private Page<OwnerSignalementSummaryDTO> signalements;
}
