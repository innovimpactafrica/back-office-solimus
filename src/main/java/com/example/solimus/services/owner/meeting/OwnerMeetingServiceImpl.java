package com.example.solimus.services.owner.meeting;

import com.example.solimus.dtos.owner.meeting.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.MeetingType;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.repositories.meeting.MeetingParticipationStats;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OwnerMeetingServiceImpl implements OwnerMeetingService {

    private final MeetingPresenceRepository meetingPresenceRepository; // présences du copropriétaire
    private final MeetingRepository meetingRepository;                 // pour les stats de quorum par réunion
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final UserRepository userRepository;
    private final MeetingDocumentRepository meetingDocumentRepository;


    // =========================================================================
    //  Liste des réunions à venir d'un copropriétaire (app mobile, onglet Réunion)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public OwnerMeetingListResponseDTO getOwnerMeetings(int page, int size) {

        // Récupère le copropriétaire actuellement connecté
        User currentUser = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size);

        // Récupère uniquement les réunions ou ce copropriétaire est convoqué,
        // filtrées sur le statut UPCOMING (les brouillons n'ont pas de participants générés)
        Page<Meeting> meetings = meetingRepository.findUpcomingMeetingsByParticipantUserId(currentUser.getId(),pageable);

        List<OwnerMeetingCardDTO> cards = new ArrayList<>();

        // Construit une carte par réunion trouvée
        for (Meeting meeting : meetings) {

            // Nombre total de copropriétaires convoqués à cette réunion
            long participantsCount = meetingParticipantRepository.countByMeetingId(meeting.getId());

            // Nombre de documents déjà liés à cette réunion
            long documentsCount = meeting.getDocuments().size();

            OwnerMeetingCardDTO card = OwnerMeetingCardDTO.builder()
                    .id(meeting.getId())
                    .title(meeting.getTitle())
                    .type(meeting.getType())
                    .typeLabel(meeting.getType().getLabel())
                    .status(meeting.getStatus())
                    .statusLabel(meeting.getStatus().getLabel())
                    .meetingDate(meeting.getMeetingDate())
                    .startTime(meeting.getStartTime())
                    .endTime(meeting.getEndTime()) // peut être null si non renseignée
                    .location(meeting.getLocation())
                    .participantsCount(participantsCount)
                    .documentsCount(documentsCount)
                    .build();

            cards.add(card);
        }

        // Construit la réponse finale : le compteur KPI reprend simplement la taille de la liste,
        // puisque toutes les réunions retournées sont déjà filtrées sur "à venir"
        return OwnerMeetingListResponseDTO.builder()
                .upcomingCount(meetings.getTotalElements())
                .meetings(cards)
                .currentPage(meetings.getNumber())
                .totalPages(meetings.getTotalPages())
                .build();
    }

       // =========================================================================
       // Détail d'une réunion (app mobile, écran détail réunion du copropriétaire)
       // =========================================================================
      @Override
      @Transactional(readOnly = true)
      public OwnerMeetingDetailDTO getOwnerMeetingDetail(Long meetingId,int documentsPage, int documentsSize) {

          // Récupère le copropriétaire actuellement connecté
          User currentUser = getCurrentUser();

          Meeting meeting = meetingRepository.findById(meetingId)
                  .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

          // Vérifie que ce copropriétaire est bien convoqué à cette réunion (sécurité :
          // un copropriétaire ne doit pas pouvoir consulter le détail d'une AG où il n'est pas convoqué)
          boolean isParticipant = meetingParticipantRepository
                  .existsByMeetingIdAndUserId(meetingId, currentUser.getId());
          if (!isParticipant) {
              throw new ForbiddenException("Vous n'êtes pas convoqué à cette réunion");
          }

         long totalParticipants = meetingParticipantRepository.countByMeetingId(meetingId);

          // Construit la liste des points de l'ordre du jour (simple, sans description sur cette vue)
          List<OwnerAgendaItemDTO> agendaItems = new ArrayList<>();
          for (MeetingAgendaItem item : meeting.getAgendaItems()) {
              agendaItems.add(OwnerAgendaItemDTO.builder()
                      .orderIndex(item.getOrderIndex() + 1)
                      .title(item.getTitle())
                      .build());
          }

          // Construit la liste des documents
          Pageable documentsPageable = PageRequest.of(documentsPage, documentsSize);
          Page<MeetingDocument> documentPage = meetingDocumentRepository.findByMeetingId(meetingId, documentsPageable);


          List<OwnerMeetingDocumentDTO> documents = new ArrayList<>();
          for (MeetingDocument doc : documentPage.getContent()) {
              documents.add(OwnerMeetingDocumentDTO.builder()
                      .id(doc.getId())
                      .fileName(doc.getFileName())
                      .fileUrl(doc.getFileUrl())
                      .fileSizeKb(doc.getFileSizeKb())
                      .documentTypeLabel(doc.getDocumentType() != null ? doc.getDocumentType().getLabel() : null)
                      .build());
          }


          return OwnerMeetingDetailDTO.builder()
                  .id(meeting.getId())
                  .title(meeting.getTitle())
                  .type(meeting.getType())
                  .typeLabel(meeting.getType().getLabel())
                  .status(meeting.getStatus())
                  .statusLabel(meeting.getStatus().getLabel())
                  .meetingDate(meeting.getMeetingDate())
                  .startTime(meeting.getStartTime())
                  .endTime(meeting.getEndTime())
                  .location(meeting.getLocation())
                  .organizerName(formatActorName(meeting.getSyndic()))
                  .description(meeting.getConvocationMessage())
                  .totalParticipants(totalParticipants)
                  .agendaItems(agendaItems)
                  .documents(documents)
                  .documentsTotalCount(documentPage.getTotalElements())
                  .documentsCurrentPage(documentPage.getNumber())
                  .documentsTotalPages(documentPage.getTotalPages())
                  .build();
      }

    // =========================================================================
    // Réunions à venir d'un mois précis, groupées par jour (app mobile, vue Calendrier)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public OwnerMeetingCalendarDTO getOwnerMeetingsCalendar(int year, int month) {

        // Récupère le copropriétaire actuellement connecté
        User currentUser = getCurrentUser();

        // Calcule le premier et le dernier jour du mois demandé
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        // Récupère uniquement les réunions à venir de ce copropriétaire, sur ce mois précis
        List<Meeting> meetings = meetingRepository.findUpcomingMeetingsByParticipantUserIdAndMonth(
                currentUser.getId(), startOfMonth, endOfMonth);

        // Regroupe les réunions par date (casier : clé = date, valeur = liste de réunions ce jour-là)
        Map<LocalDate, List<OwnerMeetingCardDTO>> meetingsByDate = new HashMap<>();

        // Parcourt chaque réunion trouvée, une par une
        for (Meeting meeting : meetings) {

            // Nombre total de copropriétaires convoqués à cette réunion
            long participantsCount = meetingParticipantRepository.countByMeetingId(meeting.getId());

            // Nombre de documents déjà liés à cette réunion
            long documentsCount = meeting.getDocuments().size();

            // Construit la carte à afficher pour cette réunion (toutes les infos résumées)
            OwnerMeetingCardDTO card = OwnerMeetingCardDTO.builder()
                    .id(meeting.getId())
                    .title(meeting.getTitle())
                    .type(meeting.getType())
                    .typeLabel(meeting.getType().getLabel())
                    .status(meeting.getStatus())
                    .statusLabel(meeting.getStatus().getLabel())
                    .meetingDate(meeting.getMeetingDate())
                    .startTime(meeting.getStartTime())
                    .endTime(meeting.getEndTime())
                    .location(meeting.getLocation())
                    .participantsCount(participantsCount)
                    .documentsCount(documentsCount)
                    .build();

            // Récupère juste la date de cette réunion, pour savoir dans quel casier la ranger
            LocalDate date = meeting.getMeetingDate();

            if (meetingsByDate.containsKey(date)) {
                // Ce jour a déjà au moins une réunion, on ajoute celle-ci à la liste existante
                meetingsByDate.get(date).add(card);
            } else {
                // Première réunion trouvée pour ce jour, on crée sa liste
                List<OwnerMeetingCardDTO> dayList = new ArrayList<>();
                dayList.add(card);
                meetingsByDate.put(date, dayList);
            }
        }

        // Construit la réponse finale : les réunions groupées par jour
        return OwnerMeetingCalendarDTO.builder()
                .meetingsByDate(meetingsByDate)
                .build();
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Syndic non trouvé"));
    }


    // Formate le nom d'affichage d'un utilisateur avec son rôle, ex: "Syndic - Abdou Diop"
    // Retourne "Système" si l'utilisateur est null (ex: log génère automatiquement par un job planifié)
    private String formatActorName(User user) {
        if (user == null) {
            return "Système";
        }
        String roleLabel = user.getRole().getName().getLabel();
        String fullName = user.getFirstName() + " " + user.getLastName();
        return roleLabel + " - " + fullName;
    }

}
