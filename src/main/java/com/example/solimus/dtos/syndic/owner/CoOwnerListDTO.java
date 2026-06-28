package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour la liste des copropriétaires du syndic connecté
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerListDTO {

    private Long id;

    private String fullName;

    private String photoUrl;

    private String email;

    private String phone;

    private int propertyCount; // Nombre de propriétés du copropriétaire
}
