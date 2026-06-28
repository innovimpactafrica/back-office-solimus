package com.example.solimus.dtos.owner.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO contenant les informations du prestataire sélectionné pour une intervention
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfoDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String companyName;
}
