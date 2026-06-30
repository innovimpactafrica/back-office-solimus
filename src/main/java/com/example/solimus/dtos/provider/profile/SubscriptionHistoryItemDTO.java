package com.example.solimus.dtos.provider.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Représente une ligne de l'historique des paiements d'abonnement
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHistoryItemDTO {

    private String planName;        // Plan d'abonnement
    private String status;          // Statut paiement "Payé" / "Échoué" / "En attente"
    private String reference;       // Ref de paiement
    private BigDecimal amount;      // Montant payé
    private String paymentMethod;   // Méthode de paiement
    private LocalDateTime date;     // date du paiement
}
