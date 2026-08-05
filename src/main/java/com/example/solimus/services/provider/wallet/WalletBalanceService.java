package com.example.solimus.services.provider.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Calcule le solde d'un wallet prestataire à la volée (jamais stocké)
public interface WalletBalanceService {

    /**
     * Solde actuellement disponible = total encaissé - montant réservé par les retraits
     * PENDING/COMPLETED. C'est LE solde à utiliser pour vérifier qu'un nouveau retrait est possible.
     */
    BigDecimal getCurrentBalance(Long providerId);

    /**
     * Solde brut (total encaissé, sans déduire les retraits) à une date précise dans le passé —
     * utilisé uniquement pour comparer une évolution ("solde fin du mois précédent"), jamais pour
     * vérifier un accès.
     */
    BigDecimal getBalanceAtDate(Long providerId, LocalDateTime asOfDate);

    /**
     * Total encaissé depuis le début du mois en cours.
     */
    BigDecimal getTotalThisMonth(Long providerId);

    /**
     * Total des retraits COMPLETED du mois en cours.
     */
    BigDecimal getWithdrawnThisMonth(Long providerId);

    /**
     * Montant actuellement en attente de validation (retraits PENDING uniquement).
     */
    BigDecimal getPendingBalance(Long providerId);
}