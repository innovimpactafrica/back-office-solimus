package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.SyndicPlanFeature;
import lombok.*;

import java.math.BigDecimal;
import java.util.Set;

// ===== DTO REQUÊTE - CRÉATION/MISE À JOUR D'UNE FORMULE SYNDIC =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicPlanRequestDTO {

    private String name;
    private String description;

    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;

    private Integer maxResidences;
    private Integer maxCoOwners;
    private Integer maxUsers;

    private Set<SyndicPlanFeature> features;

    private Boolean active;
}