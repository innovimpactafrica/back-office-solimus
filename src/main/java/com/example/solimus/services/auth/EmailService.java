package com.example.solimus.services.auth;

/**
 * Interface pour le service d'envoi d'emails
 */
public interface EmailService {

    /**
     * Envoie un code d'activation par email à un utilisateur
     *
     * @param email L'email du destinataire
     * @param code  Le code d'activation à 6 chiffres
     * @param firstName Le prénom de l'utilisateur
     */
    void sendActivationCode(String email, String code, String firstName);

    /**
     * Envoie un code de réinitialisation de mot de passe.
     */
    void sendPasswordResetCode(String email, String code, String firstName);

    /**
     * Envoie un email libre (sujet + corps texte).
     */
    void sendEmail(String to, String subject, String body);

    /**
     * Envoie un lien d'activation de compte à un utilisateur créé par l'admin.
     * Le lien contient un token UUID sécurisé et expire après 15 minutes.
     *
     * @param email     L'email de l'utilisateur destinataire.
     * @param token     Le token UUID d'activation.
     * @param firstName Le prénom de l'utilisateur (pour personnaliser l'email).
     */
    void sendUserActivationLink(String email, String token, String firstName);
    /**
     * Envoie une notification à un prestataire pour une nouvelle demande d'intervention.
     */
    void sendInterventionNotification(String email, String providerName, String title, String residenceName);

    /**
     * Envoie un email de confirmation d'activation d'abonnement Premium.
     */
    void sendSubscriptionPremiumNotification(String email, String firstName, String planName, String expirationDate);

    /**
     * Envoie un email de confirmation de renouvellement réussi de l'abonnement Premium.
     */
    void sendSubscriptionRenewalNotification(String email, String firstName, String planName, String expirationDate);

    /**
     * Envoie un email notifiant de l'échec de renouvellement et du retour au plan Gratuit.
     */
    void sendSubscriptionRenewalFailedNotification(String email, String firstName);

    /**
     * Envoie un email notifiant de la désactivation du renouvellement automatique de l'abonnement.
     */
    void sendSubscriptionCancellationNotification(String email, String firstName);

    /**
     * Envoie un email notifiant de l'expiration de l'abonnement Premium.
     */
    void sendSubscriptionExpiredNotification(String email, String firstName);
}
