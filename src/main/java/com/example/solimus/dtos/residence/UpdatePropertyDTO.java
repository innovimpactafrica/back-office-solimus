package com.example.solimus.dtos.residence;

import com.example.solimus.enums.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour modifier un bien d'une résidence
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePropertyDTO {
    private String reference;

    private String bloc;

    private String floor;

    private PropertyType typeBien;

    private BigDecimal superficie;

    private BigDecimal tantieme;
}
