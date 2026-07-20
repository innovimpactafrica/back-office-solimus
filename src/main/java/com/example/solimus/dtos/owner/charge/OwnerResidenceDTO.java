package com.example.solimus.dtos.owner.dashboard;

import lombok.*;

// ===== DTO RÉSIDENCE (id + nom) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerResidenceDTO {

    private Long id;
    private String name;
}
