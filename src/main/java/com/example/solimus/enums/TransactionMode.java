package com.example.solimus.enums;

/**
 * Mode de transaction
 * Détermine comment une transaction a été effectuée
 */
public enum TransactionMode {
    VIREMENT("Virement"),
    PRELEVEMENT("Prélèvement"),
    CHEQUE("Chèque"),
    ESPECES("Espèces"),
    MOBILE_MONEY("Mobile Money"); // au cas où un wallet transaction viendrait aussi d'un paiement copro

    private final String label;

    TransactionMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
