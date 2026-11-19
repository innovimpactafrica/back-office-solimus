package com.example.solimus.enums;

/** Qui a payé (hors plateforme) l'acompte ou le solde d'une intervention. */
public enum ProofPayerType {
    SYNDIC("Syndic"),
    COPROPRIETAIRE("Copropriétaire");

    private final String label;

    ProofPayerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
