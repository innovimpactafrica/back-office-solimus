package com.example.solimus.enums;

/**
 * Cycle de vie d'une preuve de paiement hors plateforme (charge de copropriété ou paiement de travaux).
 * Le paiement a lieu en dehors de Solimus : seul le BÉNÉFICIAIRE des fonds peut le confirmer
 * (le syndic pour une charge, le prestataire pour un acompte / solde de travaux).
 */
public enum ProofStatus {
    PENDING_VALIDATION("En attente de validation"),
    VALIDATED("Validée"),
    REJECTED("Rejetée");

    private final String label;

    ProofStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
