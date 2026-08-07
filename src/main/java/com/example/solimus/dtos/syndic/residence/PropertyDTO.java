package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour la réponse d'un lot
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyDTO {
    private Long id;
    private String reference;
    private Integer floor;
    private BigDecimal area;
    private BigDecimal share;
    private String typeName;
    private Long residenceId;
    private String residenceName;

    // Infos du propriétaire unique
    private Long ownerId;
    private String ownerName;

    // Infos du locataire unique (null si le lot n'est pas loué)
    private Long tenantId;
    private String tenantName;
}