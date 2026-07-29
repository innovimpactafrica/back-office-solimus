package com.example.solimus.dtos.admin.syndic;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Une ligne du bloc "Derniers incidents" de la fiche détail résidence (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceIncidentRowDTO {

    private Long id;
    private String title;
    private String statusLabel;
    private String priorityLabel;
    private LocalDateTime date;
    // Lot concerné (ex: "Apt.206") si APPARTEMENT, ou nom de l'équipement si PARTIE_COMMUNE
    private String location;
}
