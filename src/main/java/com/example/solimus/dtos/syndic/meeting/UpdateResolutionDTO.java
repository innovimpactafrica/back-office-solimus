package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.ResolutionStatus;
import lombok.*;

// ===== DTO REQUETE - MISE A JOUR D'UNE RESOLUTION =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResolutionDTO {

    private ResolutionStatus resolutionStatus; // Adoptée/Rejetée/Reportée/En attente
    private String observations;               // texte libre, peut être null
}
