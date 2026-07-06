package com.example.solimus.dtos.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDashboardResponseDTO {

    // Nombre total d'AG du syndic
    private Integer totalCount;

    // Nombre d'AG planifiées
    private Integer planifieesCount;

    // Nombre d'AG terminées (année en cours)
    private Integer termineesCount;

    // Nombre d'AG en brouillon
    private Integer brouillonsCount;

    // Liste des AG (paginée, filtrée)
    private List<MeetingCardDTO> meetings;
}
