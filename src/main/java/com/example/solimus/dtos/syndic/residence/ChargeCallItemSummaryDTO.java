package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.ChargeItemPaymentStatus;
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

    // Période de l'appel, ex: "T3"
    private String periode;

    // Année de l'appel, ex: 2026
    private Integer annee;

    // Montant total dû (quote-part + pénalité si déjà appliquée) — item.getTotalDue()
    private BigDecimal amountDue;

    // Montant de la pénalité isolé, pour affichage séparé si > 0 (0 si aucune pénalité)
    private BigDecimal penaltyAmount;

    // Statut du paiement (PENDING, PAID, etc.)
    private ChargeItemPaymentStatus status;

    // Date limite de paiement
    private LocalDate dueDate;

    // Mode de paiement du dernier paiement complété (si existe)
    private String paymentMethod;

    // Référence du dernier paiement complété — null si jamais payé, utilisée par le bouton "Voir reçu"
    private String paymentReference;
}