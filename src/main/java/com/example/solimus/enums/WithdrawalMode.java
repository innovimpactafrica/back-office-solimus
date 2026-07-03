package com.example.solimus.enums;

/**
 * Mode de retrait de fonds du portefeuille syndic
 * Détermine comment le syndic souhaite recevoir ses fonds
 */
public enum WithdrawalMode {
    VIREMENT,      // Virement bancaire
    WAVE,          // Paiement mobile Wave
    ORANGE_MONEY   // Paiement mobile Orange Money
}
