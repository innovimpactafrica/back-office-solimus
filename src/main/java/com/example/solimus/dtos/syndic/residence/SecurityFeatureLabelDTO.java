package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour retourner les options de sécurité disponible 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityFeatureLabelDTO {
    private Long id;
    private String label;
    private String icon;
}
