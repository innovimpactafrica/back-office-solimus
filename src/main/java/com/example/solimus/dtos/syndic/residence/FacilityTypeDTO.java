package com.example.solimus.dtos.syndic.residence;

import lombok.Builder;
import lombok.Data;

import java.util.List;

//DTO output type de bien commun (Création Résidence étape 3)
@Data
@Builder
public class FacilityTypeDTO {

    private Long id; // id du type

    private String name; // "Piscine", "Ascenseur"...

    private String icon; // icône affichée dans le bloc

    private List<String> fields; // champs à afficher pour ce type — ["count", "isHeated"] pour Piscine
}