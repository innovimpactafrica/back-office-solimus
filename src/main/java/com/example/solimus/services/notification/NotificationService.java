package com.example.solimus.services.notification;

public interface NotificationService {

    //sauvegarde le token FCM de l'utilisateur connecté en base
    void saveFcmToken(String fcmToken);

    //Envoie une notification push à un utilisateur précis via son id
    void sendPush(Long userId, String title, String body);
}
