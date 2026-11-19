package com.example.solimus.enums;

/**
 * Moyen utilisé pour un paiement effectué EN DEHORS de Solimus, déclaré par le payeur avec sa preuve.
 * Purement informatif : Solimus ne traite pas ces fonds.
 */
public enum OffPlatformPaymentMethod {
    VIREMENT("Virement bancaire"),
    ESPECES("Espèces"),
    CHEQUE("Chèque"),
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    AUTRE("Autre");

    private final String label;

    OffPlatformPaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
