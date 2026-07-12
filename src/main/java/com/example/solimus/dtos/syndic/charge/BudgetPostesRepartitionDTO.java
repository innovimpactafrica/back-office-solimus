package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.util.List;

//DTO du camembert "Répartition des postes" d'une résidence, pour une année
@Data
public class BudgetPostesRepartitionDTO {
    private String residenceName; // Nom de la résidence affichée
    private Integer year; // Année du budget affiché
    private List<PosteDTO> postes; // Liste des postes budgétaires avec leur montant
}