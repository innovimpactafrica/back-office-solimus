package com.example.solimus.services.owner.meeting;

import com.example.solimus.dtos.owner.meeting.*;
import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.MeetingPresence;
import com.example.solimus.enums.MeetingType;
import com.example.solimus.repositories.MeetingPresenceRepository;
import com.example.solimus.repositories.meeting.MeetingParticipationStats;
import com.example.solimus.repositories.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerMeetingServiceImpl implements OwnerMeetingService {

    private final MeetingPresenceRepository meetingPresenceRepository; // presences du coproprietaire
    private final MeetingRepository meetingRepository;                 // pour les stats de quorum par reunion

    @Override
    public OwnerMeetingsTabResponseDTO getMeetingsTab(Long userId, Long residenceId, OwnerMeetingSearchFilterDTO filter) {

        // ===== 1. TAUX DE PARTICIPATION HISTORIQUE (sur les AG terminees uniquement) =====
        List<MeetingPresence> completedPresences = meetingPresenceRepository.findCompletedPresencesForOwner(userId, residenceId);

        long totalConvened = completedPresences.size(); // nb d'AG terminees ou ce coproprietaire etait convoque
        long totalSigned = 0; // boucle classique, pas de stream
        for (MeetingPresence presence : completedPresences) {
            if (Boolean.TRUE.equals(presence.getHasSigned())) {
                totalSigned++;
            }
        }
        double participationRate = totalConvened > 0 ? ((double) totalSigned / totalConvened) * 100.0 : 0.0;

        // ===== 2. DERNIERE AG =====
        Pageable lastMeetingPageable = PageRequest.of(0, 1); // on ne veut que la 1ere ligne
        Page<MeetingPresence> lastMeetingPage = meetingPresenceRepository.findLastMeetingForOwner(userId, residenceId, lastMeetingPageable);

        OwnerLastMeetingDTO lastMeetingDTO = null;
        if (!lastMeetingPage.getContent().isEmpty()) {
            MeetingPresence lastPresence = lastMeetingPage.getContent().get(0);
            Meeting lastMeeting = lastPresence.getMeetingParticipant().getMeeting();

            boolean present = Boolean.TRUE.equals(lastPresence.getHasSigned());

            lastMeetingDTO = OwnerLastMeetingDTO.builder()
                    .meetingId(lastMeeting.getId())
                    .title(lastMeeting.getTitle())
                    .type(lastMeeting.getType().name())
                    .typeLabel(lastMeeting.getType().getLabel())
                    .meetingDate(lastMeeting.getMeetingDate())
                    .present(present)
                    .presenceLabel(present ? "Présent" : "Absent")
                    .build();
        }

        // ===== 3. HISTORIQUE PAGINE, AVEC FILTRES TYPE + ANNEE =====
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getSize() != null ? filter.getSize() : 10;
        Pageable pageable = PageRequest.of(page, size);

        MeetingType typeFilter = null;
        if (filter.getType() != null && !filter.getType().isBlank()) {
            typeFilter = MeetingType.valueOf(filter.getType());
        }

        Page<MeetingPresence> historyPage = meetingPresenceRepository.searchOwnerMeetingHistory(
                userId, residenceId, typeFilter, filter.getYear(), pageable);

        // ===== 4. QUORUM DE CHAQUE REUNION DE LA PAGE (une seule requete groupee, evite le N+1) =====
        List<Long> meetingIds = new ArrayList<>();
        for (MeetingPresence presence : historyPage.getContent()) {
            meetingIds.add(presence.getMeetingParticipant().getMeeting().getId());
        }

        List<MeetingParticipationStats> statsList = new ArrayList<>();
        if (!meetingIds.isEmpty()) {
            statsList = meetingRepository.findParticipationStats(meetingIds);
        }

        // ===== 5. CONSTRUCTION DES LIGNES DU TABLEAU (boucle classique, recherche manuelle sans Map) =====
        List<OwnerMeetingHistoryRowDTO> historyRows = new ArrayList<>();
        for (MeetingPresence presence : historyPage.getContent()) {

            Meeting meeting = presence.getMeetingParticipant().getMeeting();

            // recherche des stats correspondant a cette reunion
            MeetingParticipationStats stats = null;
            for (MeetingParticipationStats candidate : statsList) {
                if (candidate.getMeetingId().equals(meeting.getId())) {
                    stats = candidate;
                    break;
                }
            }

            double quorumPercentage = 0.0;
            if (stats != null && stats.getTotalTantieme() != null
                    && stats.getTotalTantieme().compareTo(BigDecimal.ZERO) > 0) {
                quorumPercentage = stats.getSignedTantieme().doubleValue()
                        / stats.getTotalTantieme().doubleValue() * 100.0;
            }

            boolean present = Boolean.TRUE.equals(presence.getHasSigned());

            OwnerMeetingHistoryRowDTO row = OwnerMeetingHistoryRowDTO.builder()
                    .meetingId(meeting.getId())
                    .title(meeting.getTitle())
                    .meetingDate(meeting.getMeetingDate())
                    .quorumPercentage(quorumPercentage)
                    .present(present)
                    .presenceLabel(present ? "Présent" : "Absent")
                    .build();

            historyRows.add(row);
        }

        // ===== 6. REPONSE FINALE =====
        return OwnerMeetingsTabResponseDTO.builder()
                .participationRate(participationRate)
                .lastMeeting(lastMeetingDTO)
                .history(historyRows)
                .totalMeetings((int) historyPage.getTotalElements())
                .currentPage(historyPage.getNumber())
                .totalPages(historyPage.getTotalPages())
                .build();
    }
}
