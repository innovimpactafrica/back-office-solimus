package com.example.solimus.dtos.property;

import com.example.solimus.enums.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyDTO {
    private Long id;
    private String reference;
    private Integer floor;
    private Double area;
    private PropertyType type;
    private Long residenceId;
    private String residenceName;
    
    // Infos du propriétaire unique
    private Long ownerId;
    private String ownerName;
}
