package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO résumé d'un item d'appel de charges (ChargeCallItem)
 * Utilisé pour le tableau "Appels de Charges" de l'onglet Finances
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChargeCallItemSummaryDTO {
    // Nom du copropriétaire
    private String coOwnerName;

    // Liste des lots que ce copropriétaire possède dans cette résidence
    private List<PropertySummaryDTO> properties;

    // Montant dû pour cet appel de charges
    private BigDecimal amountDue;

    // Statut du paiement (PENDING, PAID, etc.)
    private PaymentStatus status;

    // Date limite de paiement
    private LocalDate dueDate;

    // Mode de paiement du dernier paiement complété (si existe)
    private String paymentMethod;
}
