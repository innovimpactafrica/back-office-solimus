package com.example.solimus.dtos.owner.meeting;

import lombok.*;

// ===== DTO POINT ORDRE DU JOUR - DETAIL REUNION (APP MOBILE COPROPRIETAIRE) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerAgendaItemDTO {

    private int orderIndex;
    private String title;
}