package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'une ligne du tableau "Paiements" (global syndic)
@Data
public class PaymentRowDTO {
    private String coOwnerName;
    private String propertyLabel;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private String status; // PAYE, CRITIQUE, RETARD, PARTIEL, A_JOUR
    private LocalDate paymentDate;
}
