package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO pour un paiement (onglet Paiements du détail copropriétaire)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerPaymentItemDTO {

    private LocalDateTime date;
    private String reference;
    private String residenceName;
    private String period; // ex. "T3" (trimestriel) ou "Jan" (mensuel)
    private Integer year;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private Boolean receiptAvailable;
}
