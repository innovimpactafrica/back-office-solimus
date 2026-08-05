package com.example.solimus.enums;

// Catégorie d'une ligne du grand livre du wallet prestataire (ProviderWalletTransaction)
public enum ProviderWalletTransactionCategory {

    INTERVENTION_PAYMENT("Paiement d'intervention"), // Paiement mobile money reçu pour un travaux (via TouchPay)
    TRAVAUX("Paiement de travaux"); // Paiement reçu d'un syndic pour des travaux (transfert depuis son wallet)

    private final String label;

    ProviderWalletTransactionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}