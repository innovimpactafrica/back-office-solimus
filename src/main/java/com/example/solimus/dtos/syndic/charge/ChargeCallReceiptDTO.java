package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO du reçu de paiement d'une ligne de charge (modale "Voir reçu")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChargeCallReceiptDTO {
    private String receiptReference;  // Référence du paiement, ex: CPY-472169
    private String coOwnerName;
    private String propertyReference; // Lot(s) séparés par virgule, ex: "A05" ou "A05, B02"
    private String residenceName;
    private String periode;           // Ex: "T3"
    private Integer annee;            // Ex: 2026
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private BigDecimal amountPaid;    // Montant réellement payé sur CE paiement précis (pas le montant dû)
}