package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.SubscriberType;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

// ===== DTO LIGNE - FORMULE UNIFIÉE (Syndic ou Prestataire) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanOverviewDTO {

    private Long id;
    private SubscriberType planType;
    private String name;
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;
    private List<String> featureLabels;  // libellés affichables uniquement
    private Boolean active;
    private long subscribersCount;
}