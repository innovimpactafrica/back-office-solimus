package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour les parties communes
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonFacilityDTO {
    private Long id;
    private String label;
}
