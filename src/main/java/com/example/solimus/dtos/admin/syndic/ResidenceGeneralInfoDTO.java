package com.example.solimus.dtos.admin.syndic;

import lombok.*;

// ===== DTO — Bloc D-a "Informations générales" de la fiche détail résidence (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceGeneralInfoDTO {

    private String name;
    private String fullAddress;
}
