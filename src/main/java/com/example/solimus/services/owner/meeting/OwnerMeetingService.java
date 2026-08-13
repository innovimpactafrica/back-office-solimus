package com.example.solimus.services.owner.meeting;

import com.example.solimus.dtos.owner.meeting.*;

public interface OwnerMeetingService {

    // Liste des réunions à venir du copropriétaire connecté (onglet Réunion, vue Liste)
    OwnerMeetingListResponseDTO getOwnerMeetings(int page , int size);

    // Détail complet d'une réunion précise (ordre du jour, documents, organisateur...)
    OwnerMeetingDetailDTO getOwnerMeetingDetail(Long meetingId,int documentPage, int documentSize);

    // Réunions à venir d'un mois précis, groupées par jour (vue Calendrier)
    OwnerMeetingCalendarDTO getOwnerMeetingsCalendar(int year, int month);

    // Le copropriétaire connecté déclare sa présence physique à une réunion
    void markPresent(Long meetingId);

    // Le copropriétaire connecté déclare donner procuration à un mandataire
    void giveProcuration(Long meetingId, GiveProcurationDTO dto);
}