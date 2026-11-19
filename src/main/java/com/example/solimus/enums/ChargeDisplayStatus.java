package com.example.solimus.enums;

/**
 * Statut AFFICHÉ d'une charge de copropriétaire.
 * <p>
 * Le statut financier ({@link ChargeItemPaymentStatus}) ne passe à PAID qu'à la validation du syndic :
 * tous les calculs de montants dus, impayés et retards restent donc justes. « En attente de validation »
 * se lit sur le statut de la preuve ({@code proofStatus}) posé sur la ligne de charge.
 */
public enum ChargeDisplayStatus {
    PENDING("En attente"),
    PENDING_VALIDATION("En attente de validation"),
    PAID("Payé"),
    NO_AMOUNT_DUE("Rien à payer");

    private final String label;

    ChargeDisplayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Statut affiché à partir du statut financier et du statut de la dernière preuve. */
    public static ChargeDisplayStatus of(ChargeItemPaymentStatus status, ProofStatus proofStatus) {
        if (status == ChargeItemPaymentStatus.PAID) return PAID;
        if (status == ChargeItemPaymentStatus.NO_AMOUNT_DUE) return NO_AMOUNT_DUE;
        return proofStatus == ProofStatus.PENDING_VALIDATION ? PENDING_VALIDATION : PENDING;
    }

    /** Accepte le code (PENDING_VALIDATION) ou le libellé (« En attente de validation »), sinon null. */
    public static ChargeDisplayStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        for (ChargeDisplayStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim()) || s.label.equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
