package com.example.solimus.services.notification;

import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService{

    private final UserRepository userRepository;

    //---------------------------------------------------
    // Gestion Notification
    //----------------------------------------------------

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

        // cherche l'utilisateur cible en base
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // vérifie que l'utilisateur a bien un token FCM enregistré
        if (user.getFcmToken() == null) {
            return; // si pas de token, on ne fait rien — l'utilisateur n'a pas encore connecté l'app mobile
        }

        Notification notification = Notification.builder() // on construit la notification
                .setTitle(title) // titre qui apparaîtra en haut de la notification sur le téléphone
                .setBody(body) // corps du message
                .build(); // finalise la construction

        Message message = Message.builder() // on construit le message complet à envoyer à Firebase
                .setToken(user.getFcmToken()) // on cible le téléphone de cet utilisateur via son token
                .setNotification(notification) // on attache la notification construite juste au-dessus
                .build(); // finalise la construction

        try {
            FirebaseMessaging.getInstance().send(message); // envoie le message à Firebase — Firebase le livre sur le téléphone
        } catch (Exception e) {
            // on log l'erreur mais on ne bloque pas le reste du traitement
            // ex: si le token est expiré, on ne veut pas faire planter toute la demande d'intervention
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
