package com.example.solimus.services.notification;

import com.example.solimus.entities.Notification;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.User;
import com.example.solimus.enums.DeviceType;
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
    public void registerDeviceToken(String deviceToken, DeviceType deviceType) {

        // Récupérer l'utilisateur connecté
        User user = getCurrentUser();

        // Même logique que saveFcmToken, avec en plus le type de téléphone
        user.setFcmToken(deviceToken);
        user.setDeviceType(deviceType);

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

        // ÉTAPE 3 : envoie le push Firebase — erreur avalée ici (juste loguée), pour ne jamais faire
        // échouer l'appelant à cause d'un problème Firebase (token invalide, etc.)
        try {
            sendFirebaseMessage(user.getFcmToken(), title, body);
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

        try {
            sendFirebaseMessage(user.getFcmToken(), title, body);
        } catch (Exception e) {
            System.err.println("Erreur envoi push (sendPushOnly) userId=" + userId + " : " + e.getMessage());
        }
    }

    @Override
    public String sendTestPushWithDiagnostic(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Cas le plus fréquent : le mobile n'a jamais (ou pas encore) appelé /api/account/device-token
        // avec succès pour ce compte précis — rien à tester, pas la peine d'appeler Firebase
        if (user.getFcmToken() == null) {
            return "ÉCHEC : aucun token FCM enregistré pour ce compte. L'app mobile n'a pas encore "
                    + "appelé POST /api/account/device-token avec succès pour cet utilisateur "
                    + "(userId=" + userId + ").";
        }

        try {
            String firebaseMessageId = sendFirebaseMessage(user.getFcmToken(), "Test Solimus",
                    "Si vous recevez ce message, les notifications push fonctionnent correctement.");
            return "OK : Firebase a accepté le message (id=" + firebaseMessageId + "). Si le téléphone "
                    + "ne reçoit toujours rien, le problème est côté mobile (permissions notifications "
                    + "refusées, config Firebase du mobile différente du projet du serveur, app tuée "
                    + "par l'optimisation batterie, etc.), pas côté serveur.";
        } catch (IllegalStateException e) {
            // "FirebaseApp with name [DEFAULT] doesn't exist" — le SDK Firebase Admin ne s'est jamais
            // initialisé sur ce serveur (credentials absents), pas un problème de token
            return "ÉCHEC : Firebase Admin SDK n'est pas initialisé sur ce serveur (" + e.getMessage()
                    + "). Ce n'est pas un problème de token FCM — vérifier que firebase-service-account.json "
                    + "ou la variable d'environnement FIREBASE_SERVICE_ACCOUNT_JSON est bien présente sur "
                    + "le serveur déployé, puis regarder les logs de démarrage de l'application.";
        } catch (Exception e) {
            return "ÉCHEC Firebase : " + e.getClass().getSimpleName() + " — " + e.getMessage()
                    + ". Le token FCM enregistré est probablement invalide ou n'appartient pas au "
                    + "projet Firebase de la clé serveur actuelle (vérifier que le mobile utilise bien "
                    + "la config du projet \"solimussyndic-app\").";
        }
    }

    // Construit et envoie un message Firebase — centralise ce qui était dupliqué entre sendPush,
    // sendPushOnly et le diagnostic de test, et propage l'exception (chaque appelant décide s'il
    // l'avale ou la remonte)
    private String sendFirebaseMessage(String fcmToken, String title, String body) throws Exception {

        com.google.firebase.messaging.Notification firebaseNotification = com.google.firebase.messaging.Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(firebaseNotification)
                .build();

        return FirebaseMessaging.getInstance().send(message);
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
