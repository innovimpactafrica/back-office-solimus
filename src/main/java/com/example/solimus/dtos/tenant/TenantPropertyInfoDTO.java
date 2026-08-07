package com.example.solimus.dtos.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO du bien loué par le locataire connecté (référence du lot + nom de la résidence)
// Un locataire n'a jamais qu'un seul bien, contrairement au copropriétaire.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPropertyInfoDTO {

    private String propertyReference;

    private String residenceName;
}
