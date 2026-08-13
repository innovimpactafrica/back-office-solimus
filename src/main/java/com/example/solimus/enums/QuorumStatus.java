package com.example.solimus.enums;

// Statut de quorum d'une AG, comparé à l'objectif fixé par le syndic à la création
public enum QuorumStatus {
    REACHED("Atteint"),
    NOT_REACHED("Non atteint");

    private final String label;

    QuorumStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
