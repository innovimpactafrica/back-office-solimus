package com.example.solimus.dtos.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un point de l'ordre du jour.
 * Affiché avec son numéro + titre (ex: "1. Présentation du budget annuel").
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingAgendaItemDTO {
    private Long id;
    private Integer orderIndex; // numéro affiché devant le titre
    private String title;
}
