package com.example.solimus.enums;

/**
 * Catégorie de transaction de portefeuille syndic
 * Détermine le type d'opération financière
 */
public enum WalletTransactionCategory {
    CHARGES,    // Paiement de charges par un copropriétaire
    TRAVAUX,    // Paiement d'un prestataire pour des travaux
    RETRAIT     // Retrait de fonds par le syndic
}
