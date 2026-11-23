package com.example.solimus.enums;

/** Origine d'une recharge du wallet prestataire. */
public enum RechargeOrigin {
    /** Le prestataire a payé Solimus hors application et envoyé sa preuve ; l'admin la valide. */
    PREUVE_PRESTATAIRE("Preuve envoyée par le prestataire"),
    /** L'admin crédite directement (ex. paiement en espèces au bureau). */
    CREDIT_ADMIN("Crédit direct par l'admin");

    private final String label;

    RechargeOrigin(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
