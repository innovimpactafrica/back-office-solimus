package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE - ONGLET RESOLUTIONS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionsTabResponseDTO {

    private int totalCount; // = badge de l'onglet, nb de points marqués requiresResolution=true

    private List<ResolutionRowDTO> resolutions;
}
