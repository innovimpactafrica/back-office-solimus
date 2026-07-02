package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour un contact clé lors de la création d'une résidence
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContactInputDTO {
    private String fullName;
    private String role;
    private String email;
    private String phone;
}
