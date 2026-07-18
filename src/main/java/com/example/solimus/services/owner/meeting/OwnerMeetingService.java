package com.example.solimus.services.owner.meeting;

import com.example.solimus.dtos.owner.meeting.*;

public interface OwnerMeetingService {

    // Liste des réunions à venir du copropriétaire connecté (onglet Réunion, vue Liste)
    OwnerMeetingListResponseDTO getOwnerMeetings(int page , int size);

    // Détail complet d'une réunion précise (ordre du jour, documents, organisateur...)
    OwnerMeetingDetailDTO getOwnerMeetingDetail(Long meetingId,int documentPage, int documentSize);

    // Réunions à venir d'un mois précis, groupées par jour (vue Calendrier)
    OwnerMeetingCalendarDTO getOwnerMeetingsCalendar(int year, int month);
}