package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE - ONGLET ORDRE DU JOUR D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaItemsTabResponseDTO {

    private int totalCount; // nombre total de points

    private List<AgendaItemRowDTO> items;
}
