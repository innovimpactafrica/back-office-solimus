package com.example.solimus.services.notification;

import com.example.solimus.entities.Notification;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.NotificationRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService{

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final SyndicProfileRepository syndicProfileRepository;

    @Override
    public void saveFcmToken(String fcmToken) {

        // Récupérer l'utilisateur connecté
        User user = getCurrentUser();

        // met à jour le token FCM sur l'utilisateur
        user.setFcmToken(fcmToken);

        // sauvegarde en base
        userRepository.save(user);
    }

    @Override
    public void sendPush(Long userId, String title, String body) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // ÉTAPE 1 : on enregistre TOUJOURS la notification en base, même si le push Firebase échoue
        // (ça reste utile pour l'historique et le compteur, peu importe si le téléphone est connecté ou pas)
       Notification persistedNotification = new Notification();
        persistedNotification.setUser(user);
        persistedNotification.setTitle(title);
        persistedNotification.setBody(body);
        persistedNotification.setRead(false);
        notificationRepository.save(persistedNotification);

        // ÉTAPE 2 : si pas de token FCM, on s'arrête là (déjà enregistré en base, mais pas de push physique)
        if (user.getFcmToken() == null) {
            return;
        }

        // ÉTAPE 3 : construit et envoie le push Firebase (classe Notification de Firebase, différente de la nôtre)
        com.google.firebase.messaging.Notification firebaseNotification = com.google.firebase.messaging.Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(user.getFcmToken())
                .setNotification(firebaseNotification)
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            System.err.println("Erreur envoi push userId=" + userId + " : " + e.getMessage());
        }
    }

    @Override
    public void sendPushOnly(Long userId, String title, String body) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Pas de token FCM enregistré : rien à envoyer, et surtout aucune écriture en base ici
        if (user.getFcmToken() == null) {
            return;
        }

        //Sinon,  Construit et envoie le push Firebase (classe Notification de Firebase, différente de la nôtre)
        com.google.firebase.messaging.Notification firebaseNotification = com.google.firebase.messaging.Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(user.getFcmToken())
                .setNotification(firebaseNotification)
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            System.err.println("Erreur envoi push (sendPushOnly) userId=" + userId + " : " + e.getMessage());
        }
    }

    // =========================================================================
    // Envoie un push "Nouveau paiement" au syndic, si sa préférence est activée
    // =========================================================================
    @Override
    public void sendNewPaymentNotification(Long syndicUserId, String title, String body) {

        Optional<SyndicProfile> profileOpt = syndicProfileRepository.findByUserId(syndicUserId);

        // Vérifie si le profil existe ET si sa préférence "notifNewPayments" est activée.
        // Si aucun profil n'est trouvé (cas rare/imprévu), on envoie quand même par sécurité "(orElse(true)",plutôt que de bloquer silencieusement
        boolean shouldSend = profileOpt.map(SyndicProfile::getNotifNewPayments).orElse(true);

        // N'envoie le push que si la vérification ci-dessus a donné le feu vert (true)
        if (shouldSend) {
            sendPush(syndicUserId, title, body);
        }
    }

    // =========================================================================
    // Envoie un push "Incident urgent" au syndic, si sa préférence est activée
    // =========================================================================
    public void sendUrgentIncidentNotification(Long syndicUserId, String title, String body) {

        // Vérifie si le profil existe ET si sa préférence "NotifUrgentIncidents" est activée.
        // Si aucun profil n'est trouvé (cas rare/imprévu), on envoie quand même par sécurité "(orElse(true)",plutôt que de bloquer silencieusement
        Optional<SyndicProfile> profileOpt = syndicProfileRepository.findByUserId(syndicUserId);
        boolean shouldSend = profileOpt.map(SyndicProfile::getNotifUrgentIncidents).orElse(true);

        // N'envoie le push que si la vérification ci-dessus a donné le feu vert (true)
        if (shouldSend) {
            sendPush(syndicUserId, title, body);
        }
    }

    // =========================================================================
    // Envoie un push "Relance impayé" au syndic, si sa préférence est activée
    // =========================================================================
    public void sendUnpaidReminderNotification(Long syndicUserId, String title, String body) {

        // Vérifie si le profil existe ET si sa préférence "NotifUnpaidReminders" est activée.
        // Si aucun profil n'est trouvé (cas rare/imprévu), on envoie quand même par sécurité "(orElse(true)",plutôt que de bloquer silencieusement
        Optional<SyndicProfile> profileOpt = syndicProfileRepository.findByUserId(syndicUserId);
        boolean shouldSend = profileOpt.map(SyndicProfile::getNotifUnpaidReminders).orElse(true);

        // N'envoie le push que si la vérification ci-dessus a donné le feu vert (true)
        if (shouldSend) {
            sendPush(syndicUserId, title, body);
        }
    }

    // =========================================================================
    // Envoie un push "Rappel AG" au syndic, si sa préférence est activée
    // =========================================================================
    public void sendAgReminderNotification(Long syndicUserId, String title, String body) {

        // Vérifie si le profil existe ET si sa préférence "NotifAgReminders" est activée.
        // Si aucun profil n'est trouvé (cas rare/imprévu), on envoie quand même par sécurité "(orElse(true)",plutôt que de bloquer silencieusement
        Optional<SyndicProfile> profileOpt = syndicProfileRepository.findByUserId(syndicUserId);
        boolean shouldSend = profileOpt.map(SyndicProfile::getNotifAgReminders).orElse(true);

        // N'envoie le push que si la vérification ci-dessus a donné le feu vert (true)
        if (shouldSend) {
            sendPush(syndicUserId, title, body);
        }
    }



    //---------------------------------------------------
    // Méthodes utilitaires
    //----------------------------------------------------

    //Récupérer l'utilisateur connecté
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire non trouvé"));

    }


}
