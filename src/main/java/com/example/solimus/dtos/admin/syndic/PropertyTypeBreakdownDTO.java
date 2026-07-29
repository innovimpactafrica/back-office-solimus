package com.example.solimus.dtos.admin.syndic;

import lombok.*;

// ===== DTO — Répartition des biens d'une résidence par type (Bloc D-a, Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyTypeBreakdownDTO {

    private Long propertyTypeId;
    private String typeLabel;
    private long count;
}
