package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'un poste dans le camembert de répartition
@Data
public class PosteDTO {
    private String libelle; // Nom du poste, ex: "Entretien"
    private BigDecimal montant; // Montant du poste
}