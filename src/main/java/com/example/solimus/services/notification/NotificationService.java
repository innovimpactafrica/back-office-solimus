package com.example.solimus.services.notification;

import com.example.solimus.enums.DeviceType;

public interface NotificationService {

    //sauvegarde le token FCM de l'utilisateur connecté en base
    void saveFcmToken(String fcmToken);

    // Enregistre le token FCM + le type de téléphone (Android/iOS) de l'utilisateur connecté —
    // appelé par l'app mobile via POST /api/account/device-token
    void registerDeviceToken(String deviceToken, DeviceType deviceType);

    // Envoie un push de test à un utilisateur et retourne le résultat réel de l'envoi Firebase
    // (contrairement à sendPush, qui avale toute erreur pour ne jamais faire échouer l'appelant métier) —
    // utilisé uniquement par l'endpoint de diagnostic POST /api/account/test-push
    String sendTestPushWithDiagnostic(Long userId);

    //Envoie une notification push à un utilisateur précis via son id
    void sendPush(Long userId, String title, String body);

    // Envoie uniquement le push Firebase (le message arrive sur le téléphone).
    // N'enregistre rien en base (contrairement à "sendPush"): la ligne Notification doit être créée séparément par l'appelant
    // pour éviter de créer deux fois la même notification.
    void sendPushOnly(Long userId, String title, String body);

    // Envoie un push "Nouveau paiement" au syndic, si sa préférence est activée
    void sendNewPaymentNotification(Long syndicUserId, String title, String body);

    // Envoie un push "Incident urgent" au syndic, si sa préférence est activée
    void sendUrgentIncidentNotification(Long syndicUserId, String title, String body);

    // Envoie un push "Relance impayé" au syndic, si sa préférence est activée
    void sendUnpaidReminderNotification(Long syndicUserId, String title, String body);

    // Envoie un push "Rappel AG" au syndic, si sa préférence est activée
    void sendAgReminderNotification(Long syndicUserId, String title, String body);
}
