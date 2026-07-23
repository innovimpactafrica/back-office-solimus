package com.example.solimus.enums;

// Statut réel d'une ligne de charge (ExceptionalCallItem / ChargeCallItem),
// posé explicitement au moment où un paiement est confirmé — jamais déduit
// par comparaison arithmétique (quotePart - paidAmount).
public enum ChargeItemPaymentStatus {

    PENDING("En attente"),
    PARTIALLY_PAID("Partiellement payé"),
    PAID("Payé"),
    // Posé une seule fois, à la création de l'item, quand sa quote-part calculée vaut 0 —
    // jamais "Payé" (personne n'a rien payé) ni "En attente" (rien n'est attendu de ce copropriétaire)
    NO_AMOUNT_DUE("Rien à payer");

    private final String label;

    ChargeItemPaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}