package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO du reçu d'un paiement (bouton "Reçu" sur une ligne de l'historique des paiements, onglet
// Paiements du détail copropriétaire) — même logique que ChargeCallReceiptDTO, adaptée au contexte
// de cet onglet (clé = ChargeCallPayment.id, déjà exposé par CoOwnerPaymentItemDTO.id)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerPaymentReceiptDTO {
    private String receiptReference;
    private String coOwnerName;
    private String residenceName;
    private String period; // ex: "T3"
    private Integer year;  // ex: 2026
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private BigDecimal amountPaid;
}
