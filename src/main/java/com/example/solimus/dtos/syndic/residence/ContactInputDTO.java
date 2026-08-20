package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour un contact clé — utilisé à la création d'une résidence (liste, dans contactsJson)
 * et par les endpoints CRUD dédiés. fullName et phone sont tous les deux optionnels.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContactInputDTO {
    private String fullName;
    private String phone;
}
