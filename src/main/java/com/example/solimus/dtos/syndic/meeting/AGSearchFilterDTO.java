package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.MeetingStatus;
import lombok.*;

// ===== DTO FILTRE DE RECHERCHE - LISTING ASSEMBLEES GENERALES =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AGSearchFilterDTO {

    private MeetingStatus status; // valeur de l'enum MeetingStatus, null = tous les statuts
    private String search;   // recherche partielle sur le titre de la reunion, null = pas de recherche

    @Builder.Default
    private Integer page = 0;  // numéro de page, commence a 0

    @Builder.Default
    private Integer size = 10; // taille de la page
}
