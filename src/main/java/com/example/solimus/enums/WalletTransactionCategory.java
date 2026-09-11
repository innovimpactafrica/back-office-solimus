package com.example.solimus.enums;

/**
 * Catégorie de transaction de portefeuille syndic
 * Détermine le type d'opération financière
 */
public enum WalletTransactionCategory {

    CHARGES("Charges"),                    // Paiement de charges par un copropriétaire
    BUDGET_EXPENSE("Dépense budgétaire"),  // Sortie d'argent liée à un poste budgétaire (travaux ou dépense libre)
    WITHDRAWAL("Retrait");                 // Retrait de fonds par le syndic, sans poste budgétaire

    private final String label;

    WalletTransactionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}