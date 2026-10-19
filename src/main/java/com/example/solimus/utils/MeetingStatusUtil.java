package com.example.solimus.utils;

import com.example.solimus.entities.Meeting;
import com.example.solimus.enums.MeetingStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Statut affiché d'une réunion / AG — seule source de vérité, commune aux profils syndic et
 * copropriétaire. Le statut stocké ne change que sur action du syndic (publier, démarrer,
 * clôturer, annuler) ; sans action, une réunion passée restait "À venir". Ici on le déduit de la date :
 *   - DRAFT, CANCELLED, COMPLETED : statut stocké (décision explicite du syndic)
 *   - UPCOMING / IN_PROGRESS      : À venir avant l'heure de début, En cours jusqu'à l'heure de fin,
 *                                   Terminée ensuite (fin de journée si aucune heure de fin)
 * Une réunion démarrée manuellement en avance reste "En cours".
 */
public final class MeetingStatusUtil {

    private MeetingStatusUtil() {
    }

    public static MeetingStatus effectiveStatus(Meeting meeting) {
        return effectiveStatus(meeting, LocalDateTime.now());
    }

    public static MeetingStatus effectiveStatus(Meeting meeting, LocalDateTime now) {
        MeetingStatus stored = meeting.getStatus();
        if (stored != MeetingStatus.UPCOMING && stored != MeetingStatus.IN_PROGRESS) {
            return stored;
        }
        if (meeting.getMeetingDate() == null) {
            return stored;
        }

        LocalDateTime start = meeting.getMeetingDate()
                .atTime(meeting.getStartTime() != null ? meeting.getStartTime() : LocalTime.MIN);
        LocalDateTime end = meeting.getMeetingDate()
                .atTime(meeting.getEndTime() != null ? meeting.getEndTime() : LocalTime.MAX);

        if (now.isAfter(end)) {
            return MeetingStatus.COMPLETED;
        }
        if (!now.isBefore(start)) {
            return MeetingStatus.IN_PROGRESS;
        }
        return stored; // avant l'heure de début : À venir (ou En cours si démarrée manuellement)
    }
}
