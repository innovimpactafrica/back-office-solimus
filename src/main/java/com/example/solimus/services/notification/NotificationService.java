package com.example.solimus.services.notification;


public interface NotificationService {

    //sauvegarde le token FCM de l'utilisateur connecté en base
    void saveFcmToken(String fcmToken);

    //Envoie une notification push à un utilisateur précis via son id
    void sendPush(Long userId, String title, String body);

    // Envoie un push "Nouveau paiement" au syndic, si sa préférence est activée
    void sendNewPaymentNotification(Long syndicUserId, String title, String body);

    // Envoie un push "Incident urgent" au syndic, si sa préférence est activée
    void sendUrgentIncidentNotification(Long syndicUserId, String title, String body);

    // Envoie un push "Relance impayé" au syndic, si sa préférence est activée
    void sendUnpaidReminderNotification(Long syndicUserId, String title, String body);

    // Envoie un push "Rappel AG" au syndic, si sa préférence est activée
    void sendAgReminderNotification(Long syndicUserId, String title, String body);
}
