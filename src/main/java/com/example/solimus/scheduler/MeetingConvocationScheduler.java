package com.example.solimus.scheduler;

import com.example.solimus.entities.ActivityLog;
import com.example.solimus.entities.Meeting;
import com.example.solimus.enums.ActivityType;
import com.example.solimus.repositories.ActivityLogRepository;
import com.example.solimus.repositories.MeetingRepository;
import com.example.solimus.services.syndic.syndicAG.MeetingConvocationSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// Job planifié qui envoie automatiquement les convocations AG à la date programmée
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingConvocationScheduler {

    private final MeetingRepository meetingRepository;
    private final ActivityLogRepository activityLogRepository;
    private final MeetingConvocationSenderService convocationSenderService;

    // Tourne toutes les heures, à la minute 0
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void sendPendingConvocations() {

        // Récupère toutes les réunions dont la date de convocation est atteinte
        // et dont la convocation n'a pas encore été envoyée
        List<Meeting> meetingsDue = meetingRepository.findMeetingsWithPendingConvocation(LocalDate.now());
        log.info("Job convocations AG : {} réunion(s) à traiter", meetingsDue.size());

        // Boucle classique : une erreur sur une réunion ne doit pas bloquer les autres
        for (Meeting meeting : meetingsDue) {
            try {
                sendConvocationForMeeting(meeting);
            } catch (Exception e) {
                // On logue l'erreur et on continue avec la réunion suivante
                log.error("Échec envoi convocation, réunion id={} : {}", meeting.getId(), e.getMessage(), e);
            }
        }
    }

    private void sendConvocationForMeeting(Meeting meeting) {

        // Envoi effectif sur les canaux choisis (email/sms/notification)
        convocationSenderService.sendConvocation(meeting);

        // Marque la convocation comme envoyée pour éviter un double envoi
        // au prochain passage du job
        meeting.setConvocationSent(true);
        meetingRepository.save(meeting);

        // Trace l'événement dans l'historique de la résidence
        ActivityLog activityLog = ActivityLog.builder()
                .residence(meeting.getResidence())
                .type(ActivityType.MEETING_PUBLISHED)
                .relatedEntityType("MEETING")
                .relatedEntityId(meeting.getId())
                .message("Convocation envoyée")
                .detail(meeting.getTitle())
                .build();
        activityLogRepository.save(activityLog);
    }
}
