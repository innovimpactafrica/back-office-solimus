package com.example.solimus.dtos.owner.dashboard;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

// ===== DTO CARTE - CHARGE EN ATTENTE (dashboard owner) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerPendingChargeDTO {

    private Long chargeCallItemId;
    private String title;            // construit depuis frequency
    private String residenceName;
    private String propertyReference;
    private LocalDate dueDate;
    private BigDecimal remainingAmount;
    private String status;
}
