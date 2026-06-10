package com.example.solimus.dtos.residence;

import com.example.solimus.enums.PropertyStatus;
import com.example.solimus.enums.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour lister les biens d'une résidence
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyListDTO {
    private Long id;
    private String reference;
    private String bloc;
    private String floor;
    private PropertyType typeBien;
    private BigDecimal superficie;
    private BigDecimal tantieme;
    
}
