package com.example.solimus.services.notification;

import com.example.solimus.entities.Notification;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.NotificationRepository;
import com.example.solimus.repositories.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService{

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository; // notre nouvelle entité

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
