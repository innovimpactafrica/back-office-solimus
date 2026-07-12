package com.example.solimus.enums;

/**
 * Types d'événements suivis dans le journal d'activité d'une résidence.
 */
public enum ActivityType {
    COMMENT_ADDED,          // pas encore implémenté — aucun service de commentaire existant
    INTERVENTION_REPORTED,  // branché sur SyndicTravauxServiceImpl.createInterventionRequest()
    INTERVENTION_RESOLVED,  // branché sur ProviderTravauxServiceImpl.finishIntervention() (FINISHED) et SyndicTravauxServiceImpl.payBalanceAndClose() (FINAL_VALIDATION)
    PROVIDER_ASSIGNED,      // branché sur SyndicServiceImpl.acceptQuote()
    PAYMENT_RECEIVED,       //  brancher une fois le service de paiement ChargeCall identifié
    CHARGE_CALL_GENERATED,  // branché sur ChargeServiceImpl.generateChargeCall()
    MEETING_CREATED,        // branché sur SyndicMeetingServiceImpl.createMeeting()
    MEETING_PUBLISHED,      // branché sur SyndicMeetingServiceImpl.publishMeeting()
    MEETING_DOCUMENT_ADDED, // pas encore branché — aucun service d'ajout de document AG existant
    BUDGET_CREATED,         // branché sur ChargeServiceImpl.createBudget()
    BUDGET_CLOSED,          // branché sur ChargeServiceImpl.closeBudget()
    EXCEPTIONAL_CALL_CREATED,   // branché sur ChargeServiceImpl.createExceptionalCall()
    EXCEPTIONAL_CALL_ACTIVATED, // branché sur ChargeServiceImpl.activateExceptionalCall()
    EXCEPTIONAL_CALL_CLOSED     // branché sur ChargeServiceImpl.closeExceptionalCall()
}