package com.example.solimus.dtos.owner.dashboard;

import lombok.*;

// ===== DTO LIGNE - SELECTEUR "MON BIEN" (dashboard owner) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerPropertySelectorDTO {

    private Long propertyId;
    private Long residenceId;
    private String residenceName;   // "Résidence Les Jardins"
    private String propertyReference; // "A12"
}
