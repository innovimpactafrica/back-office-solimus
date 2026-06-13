package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.meeting.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ParticipantRole;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.coproprietaire.CoOwnerMeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerMeetingServiceImpl implements CoOwnerMeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MeetingParticipantRepository participantRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MeetingSummaryDTO> getMyMeetings() {
        User currentOwner = getCurrentUser();

        List<com.example.solimus.entities.MeetingParticipant> participations = participantRepository.findByUserId(currentOwner.getId());

        return participations.stream()
            .map(p -> toSummaryDTO(p.getMeeting()))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingDetailDTO getMeetingDetail(Long meetingId) {
        User currentOwner = getCurrentUser();

        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        boolean isParticipant = meeting.getParticipants().stream()
            .anyMatch(p -> p.getUser() != null && p.getUser().getId().equals(currentOwner.getId()));

        if (!isParticipant) {
            throw new ForbiddenException("Accès non autorisé à cette réunion");
        }

        return toDetailDTO(meeting);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MeetingSummaryDTO> getMeetingsByResidence(
            Long residenceId,
            int page,
            int size) {
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("meetingDate").ascending());

        org.springframework.data.domain.Page<Meeting> meetings =
                meetingRepository.findByResidence(residence, pageable);

        return meetings.map(this::toSummaryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MeetingCalendarDayDTO> getMeetingsCalendar(
            int year,
            int month,
            int page,
            int size) {
        User currentOwner = getCurrentUser();

        // Récupérer la résidence du copropriétaire via son bien
        Property property = propertyRepository
                .findFirstByOwnerId(currentOwner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun bien trouvé pour ce copropriétaire"));

        Residence residence = property.getResidence();

        // Bornes du mois demandé
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Meeting> meetings = meetingRepository
                .findByResidenceAndMeetingDateBetween(residence,
                        start,
                        end);

        // Grouper par jour
        Map<LocalDate, List<Meeting>> grouped = meetings.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getMeetingDate()));

        // Construire la liste triée par date
        List<MeetingCalendarDayDTO> calendarDays = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> MeetingCalendarDayDTO.builder()
                        .date(entry.getKey())
                        .meetings(entry.getValue().stream()
                                .map(this::toSummaryDTO)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        // Appliquer la pagination
        int startIdx = (int) org.springframework.data.domain.PageRequest.of(page, size).getOffset();
        int endIdx = Math.min(startIdx + size, calendarDays.size());
        List<MeetingCalendarDayDTO> pagedDays = calendarDays.subList(startIdx, endIdx);

        return new org.springframework.data.domain.PageImpl<>(
                pagedDays,
                org.springframework.data.domain.PageRequest.of(page, size),
                calendarDays.size());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUpcomingMeetingsCount() {
        User currentOwner = getCurrentUser();
        LocalDate now = LocalDate.now();

        List<MeetingParticipant> participations = participantRepository.findByUserId(currentOwner.getId());

        return participations.stream()
            .filter(p -> p.getMeeting().getMeetingDate().isAfter(now))
            .count();
    }

    private MeetingSummaryDTO toSummaryDTO(Meeting meeting) {
        return MeetingSummaryDTO.builder()
            .id(meeting.getId())
            .title(meeting.getTitle())
            .type(meeting.getType())
            .status(meeting.getStatus())
            // Date séparée de l'heure
            .meetingDate(meeting.getMeetingDate())
            .meetingStartTime(meeting.getStartTime() != null ? meeting.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
            .meetingEndTime(meeting.getEndTime() != null ? meeting.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
            .location(meeting.getLocation())
            .participantCount(meeting.getParticipants().size())
            .documentCount(meeting.getDocuments().size())
            .residenceId(meeting.getResidence() != null ? meeting.getResidence().getId() : null)
            .build();
    }

    private MeetingDetailDTO toDetailDTO(Meeting meeting) {
        List<MeetingAgendaItemDTO> agendaItems = meeting.getAgendaItems() != null
            ? meeting.getAgendaItems().stream()
                .map(item -> MeetingAgendaItemDTO.builder()
                    .id(item.getId())
                    .orderIndex(item.getOrderIndex())
                    .title(item.getTitle())
                    .build())
                .collect(Collectors.toList())
            : List.of();

        List<MeetingDocumentDTO> documents = meeting.getDocuments() != null
            ? meeting.getDocuments().stream()
                .map(doc -> MeetingDocumentDTO.builder()
                    .id(doc.getId())
                    .fileName(doc.getFileName())
                    .fileUrl(doc.getFileUrl())
                    .fileSizeKb(doc.getFileSizeKb())
                    .documentType(doc.getDocumentType())
                    .createdAt(doc.getCreatedAt())
                    .build())
                .collect(Collectors.toList())
            : List.of();

        List<MeetingParticipant> allParticipants = participantRepository.findByMeetingId(meeting.getId());
        List<MeetingParticipantDTO> participants = buildParticipants(allParticipants);

        return MeetingDetailDTO.builder()
            .id(meeting.getId())
            .title(meeting.getTitle())
            .location(meeting.getLocation())
            .type(meeting.getType())
            .status(meeting.getStatus())
            .mode(meeting.getMode())
            .meetingDate(meeting.getMeetingDate())
            .meetingStartTime(meeting.getStartTime() != null ? meeting.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null)
            .meetingEndTime(meeting.getEndTime() != null ? meeting.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null)
            .organizerName(meeting.getSyndic().getFirstName() + " " + meeting.getSyndic().getLastName())
            .description(meeting.getDescription())
            .participantCount(allParticipants.size())
            .agendaItems(agendaItems)
            .documents(documents)
            .participants(participants)
            .build();
    }

    /**
     * Construit la liste des participants pour l'écran détail.
     * Ordre d'affichage :
     * 1. Organisateur (syndic)
     * 2. Externes avec rôle spécial (Mme Fall, M. Sow...)
     * 3. Copropriétaires ordinaires → groupés
     */
    private List<MeetingParticipantDTO> buildParticipants(List<MeetingParticipant> all) {

        List<MeetingParticipantDTO> result = new ArrayList<>();

        // 1. Organisateur — le syndic créateur de la réunion
        all.stream()
            .filter(p -> p.getRole() == ParticipantRole.ORGANISATEUR)
            .findFirst()
            .ifPresent(p -> result.add(MeetingParticipantDTO.builder()
                    .id(p.getUser() != null ? p.getUser().getId() : null)
                    .fullName(p.getUser() != null ? p.getUser().getFirstName() + " " + p.getUser().getLastName() : "Organisateur")
                    .subtitle("Syndic SOLIMUS")
                    .isOrganisateur(true)
                    .grouped(false)
                    .groupCount(0)
                    .build()));

        // 2. Participants externes — pas de user, juste externalName + roleLabel
        all.stream()
            .filter(p -> p.getRole() == null && p.getExternalName() != null)
            .forEach(p -> result.add(MeetingParticipantDTO.builder()
                    .id(null)
                    .fullName(p.getExternalName())   // "Mme Fall"
                    .subtitle(p.getRoleLabel())       // "Présidente du conseil"
                    .isOrganisateur(false)
                    .grouped(false)
                    .groupCount(0)
                    .build()));

        // 3. Copropriétaires système — user != null, externalName == null → groupés
        long coproCount = all.stream()
            .filter(p -> p.getUser() != null && p.getRole() == null && p.getExternalName() == null)
            .count();

        if (coproCount > 0) {
            result.add(MeetingParticipantDTO.builder()
                    .id(null)
                    .fullName(coproCount + " copropriétaires invités")
                    .subtitle("Copropriétaires")
                    .isOrganisateur(false)
                    .grouped(true)
                    .groupCount((int) coproCount)
                    .build());
        }

        return result;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
