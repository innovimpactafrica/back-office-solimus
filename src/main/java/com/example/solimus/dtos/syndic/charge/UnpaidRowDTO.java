package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau "Impayés" (global syndic)
@Data
public class UnpaidRowDTO {
    private Long chargeCallItemId;
    private String coOwnerName;
    private String propertyLabel;
    private String status; // CRITIQUE, RETARD, PARTIEL
    private BigDecimal amountDue;
    private BigDecimal unpaidBalance;
    private Integer daysLate;
}
