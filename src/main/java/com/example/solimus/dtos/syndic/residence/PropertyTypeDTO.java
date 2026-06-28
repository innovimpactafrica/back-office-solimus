package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO output Type de Bien
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyTypeDTO {
    private Long id;
    private String name;
}
