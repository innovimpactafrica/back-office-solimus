package com.example.solimus.services.syndic.syndicAG;

import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.User;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

// Envoi effectif de la convocation AG sur les canaux choisis par le syndic
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingConvocationSenderService {

    private final PropertyRepository propertyRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;



    public void sendConvocation(Meeting meeting) {

        // Récupère tous les copropriétaires distincts de la résidence de cette réunion
        List<User> owners = propertyRepository.findDistinctOwnersByResidenceId(meeting.getResidence().getId());

        String subject = "Convocation — " + meeting.getTitle();

        //Pour chaque copropriétaire convoqué
        for (User owner : owners) {

            // Chaque envoi est isolé : un échec sur un copropriétaire (email invalide, etc.)
            // ne doit pas empêcher l'envoi aux autres copropriétaires de la même réunion.
            if (Boolean.TRUE.equals(meeting.getSendByEmail())) {
                try {
                    emailService.sendEmail(owner.getEmail(), subject, meeting.getConvocationMessage());
                } catch (Exception e) {
                    log.error("Échec envoi email convocation à {} (userId={}) : {}",
                            owner.getEmail(), owner.getId(), e.getMessage());
                }
            }

            if (Boolean.TRUE.equals(meeting.getSendBySms())) {
                // TODO : smsService.send(owner.getPhone(), meeting.getConvocationMessage())
            }

            if (Boolean.TRUE.equals(meeting.getSendByPlatformNotification())) {
                try {
                    // sendPush gere deja lui-meme le cas ou l'utilisateur n'a pas de token FCM (return silencieux)
                   notificationService.sendPush(owner.getId(), subject, meeting.getConvocationMessage());
                } catch (Exception e) {
                    log.error("Échec envoi push convocation, userId={} : {}", owner.getId(), e.getMessage());
                }
            }
        }
    }
}
