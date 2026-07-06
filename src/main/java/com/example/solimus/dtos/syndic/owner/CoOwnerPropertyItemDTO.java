package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO pour un lot dans l'onglet Appartements du détail copropriétaire
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerPropertyItemDTO {

    private String reference;

    private String bloc;

    private Integer floor;

    private BigDecimal superficie;

    private BigDecimal tantieme;

    private String residenceName;

    private BigDecimal annualCharge;
}
