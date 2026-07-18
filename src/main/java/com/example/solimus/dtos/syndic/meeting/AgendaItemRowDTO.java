package com.example.solimus.dtos.syndic.meeting;

import lombok.*;

// ===== DTO LIGNE - ONGLET ORDRE DU JOUR D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaItemRowDTO {

    private Long id;
    private int orderIndex;
    private String title;
    private String description; // peut être null, le front n'affiche rien dans ce cas (comme "Questions diverses")
}
