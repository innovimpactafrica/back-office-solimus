package com.example.solimus.dtos.syndic.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO output
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyTypeDTO {
    private Long id;
    private String name;
    private String description;
}
