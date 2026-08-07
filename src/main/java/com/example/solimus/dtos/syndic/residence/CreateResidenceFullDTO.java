package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ============================================================================
 * CRÉATION D'UNE RÉSIDENCE COMPLÈTE — infos générales + lots + équipements
 * ============================================================================
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateResidenceFullDTO {

    private String name;
    private String description;

    private String fullAddress;
    private String city;
    private String country;

    private BigDecimal latitude;
    private BigDecimal longitude;

    private LocalDate constructionDate;
    private LocalDate renovationDate;

    private BigDecimal totalArea; // Superficie totale de la résidence (m²), dénominateur du calcul du tantième

    private List<ContactInputDTO> contacts;
    private List<AddPropertyDTO> properties;
    private List<AddFacilityDTO> facilities;
    private List<Long> securityFeatureIds;
}