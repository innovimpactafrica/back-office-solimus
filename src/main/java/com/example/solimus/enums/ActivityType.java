package com.example.solimus.enums;

/**
 * Types d'événements suivis dans le journal d'activité d'une résidence.
 */
public enum ActivityType {
    COMMENT_ADDED,          // pas encore implémenté — aucun service de commentaire existant
    INTERVENTION_REPORTED,  // branché sur SyndicTravauxServiceImpl.createInterventionRequest()
    INTERVENTION_RESOLVED,  // pas encore implémenté — aucun service de transition FINISHED/FINAL_VALIDATION existant
    PROVIDER_ASSIGNED,      // branché sur SyndicServiceImpl.acceptQuote()
    PAYMENT_RECEIVED,       // à brancher une fois le service de paiement ChargeCall identifié
    CHARGE_CALL_GENERATED   // à brancher une fois le service de génération ChargeCall identifié
}
