package com.example.solimus.dtos.admin.subscription;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// ===== DTO RÉPONSE - AFFICHAGE D'UNE FORMULE SYNDIC =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicPlanDTO {

    private Long id;
    private String name;
    private String description;

    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;

    private Integer maxResidences;
    private Integer maxCoOwners;
    private Integer maxUsers;

    // Chaque fonctionnalité avec sa valeur technique ET son libellé affichable
    private List<SyndicPlanFeatureDTO> features;

    private Boolean active;

    // Nombre d'abonnés actuellement sur cette formule
    private long subscribersCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}