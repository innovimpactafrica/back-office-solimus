package com.example.solimus.dtos.syndic.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// DTO — une carte de la modale "Choisir une nouvelle formule" (changement d'abonnement en self-service)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicPlanOptionDTO {

    private Long id;
    private String name;

    // Les deux prix sont toujours renvoyés ensemble — le toggle Mensuel/Annuel est un choix d'affichage
    // côté front, pas un filtre côté backend
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;

    private List<String> features;

    // Limites de la formule — null signifie illimité pour ce critère précis
    private Integer maxResidences;
    private Integer maxApartments;
}
