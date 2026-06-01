package com.example.solimus.services.syndic;

import com.example.solimus.dtos.meeting.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.ParticipantRole;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicMeetingServiceImpl implements SyndicMeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingAgendaItemRepository agendaItemRepository;
    private final MeetingDocumentRepository documentRepository;
    private final MeetingParticipantRepository participantRepository;
    private final ResidenceRepository residenceRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;

    // =========================================================================
    // SYNDIC — CRÉATION RÉUNION
    // =========================================================================

    /**
     * Crée une réunion.
     * Le syndic est automatiquement ajouté comme participant ORGANISATEUR.
     */
    @Override
    @Transactional
    public void createMeeting(CreateMeetingDTO dto) {
        // 1. Récupérer le syndic connecté
        User syndic = getCurrentUser();

        // 2. Vérifier que la résidence existe et appartient au syndic
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (!residence.getSyndic().getId().equals(syndic.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette résidence");
        }

        // 3. Construire la réunion
        Meeting meeting = new Meeting();
        meeting.setTitle(dto.getTitle());
        meeting.setDescription(dto.getDescription());
        meeting.setType(dto.getType());
        meeting.setStatus(MeetingStatus.A_VENIR); // statut par défaut
        meeting.setMeetingDate(dto.getMeetingDate());
        meeting.setLocation(dto.getLocation());
        meeting.setMode(dto.getMode());
        meeting.setResidence(residence);
        meeting.setSyndic(syndic);

        Meeting saved = meetingRepository.save(meeting);

        // 4. Le syndic est automatiquement organisateur
        MeetingParticipant organisateur = new MeetingParticipant();
        organisateur.setMeeting(saved);
        organisateur.setUser(syndic);
        organisateur.setRole(ParticipantRole.ORGANISATEUR);
        participantRepository.save(organisateur);

        log.info("Réunion créée : {} pour la résidence {}", saved.getTitle(), residence.getName());
    }

    // =========================================================================
    // SYNDIC — ORDRE DU JOUR
    // =========================================================================

    /**
     * Ajoute un point à l'ordre du jour d'une réunion.
     * Vérifie que c'est bien le syndic organisateur qui fait la demande.
     */
    @Override
    @Transactional
    public MeetingAgendaItemDTO addAgendaItem(Long meetingId, AddAgendaItemDTO dto) {
        // 1. Récupérer la réunion
        Meeting meeting = getMeetingOrThrow(meetingId);

        // 2. Vérifier que c'est le syndic organisateur
        checkIsSyndicOrganizer(meeting);

        // 3. Créer le point de l'ordre du jour
        MeetingAgendaItem item = new MeetingAgendaItem();
        item.setMeeting(meeting);
        item.setOrderIndex(dto.getOrderIndex());
        item.setTitle(dto.getTitle());

        // 4. Sauvegarder
        MeetingAgendaItem saved = agendaItemRepository.save(item);

        // 5. Retourner le DTO
        return MeetingAgendaItemDTO.builder()
                .id(saved.getId())
                .orderIndex(saved.getOrderIndex())
                .title(saved.getTitle())
                .build();
    }

    // =========================================================================
    // SYNDIC — DOCUMENTS
    // =========================================================================

    /**
     * Upload un document et l'associe à la réunion.
     * Stockage via Minio dans le dossier "meetings".
     */
    @Override
    @Transactional
    public MeetingDocumentDTO uploadDocument(Long meetingId, MultipartFile file,
                                              String fileName, String documentType) {
        // 1. Récupérer la réunion
        Meeting meeting = getMeetingOrThrow(meetingId);

        // 2. Vérifier que c'est le syndic organisateur
        checkIsSyndicOrganizer(meeting);

        // 3. Upload vers Minio
        String fileUrl = minioService.uploadFile(file, "meetings");
        if (fileUrl == null) {
            throw new BadRequestException("Erreur lors de l'upload du document");
        }

        // 4. Calculer la taille en KB
        long fileSizeKb = file.getSize() / 1024;

        // 5. Créer l'entité document
        MeetingDocument document = new MeetingDocument();
        document.setMeeting(meeting);
        document.setFileName(fileName);
        document.setFileUrl(fileUrl);
        document.setFileSizeKb(fileSizeKb);
        document.setDocumentType(MeetingDocumentType.valueOf(documentType.toUpperCase()));

        // 6. Sauvegarder
        MeetingDocument saved = documentRepository.save(document);

        // 7. Retourner le DTO
        return mapToDocumentDTO(saved);
    }

    // =========================================================================
    // SYNDIC — PARTICIPANTS
    // =========================================================================

    /**
     * Ajoute un participant externe (pas un user du système).
     * Ex: Mme Fall - Présidente du conseil
     */
    @Override
    @Transactional
    public void addExternalParticipant(Long meetingId, AddExternalParticipantDTO dto) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        checkIsSyndicOrganizer(meeting);

        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeeting(meeting);
        participant.setUser(null);                  // pas un user système
        participant.setRole(null);                  // pas organisateur
        participant.setExternalName(dto.getFullName());
        participant.setRoleLabel(dto.getRoleLabel());
        participantRepository.save(participant);
    }

    /**
     * Invite des copropriétaires à une réunion.
     * Vérifie que chaque ID correspond bien à un COPROPRIETAIRE.
     * Ignore les doublons (un copropriétaire déjà invité ne sera pas ajouté deux fois).
     */
    @Override
    @Transactional
    public void inviteParticipants(Long meetingId, InviteParticipantsDTO dto) {
        // 1. Récupérer la réunion
        Meeting meeting = getMeetingOrThrow(meetingId);

        // 2. Vérifier que c'est le syndic organisateur
        checkIsSyndicOrganizer(meeting);

        // 3. Récupérer les IDs déjà invités pour éviter les doublons
        //Cette méthode récupère tous les participants de la réunion.
        Set<Long> alreadyInvited = participantRepository.findByMeetingId(meetingId)
                .stream()
                .filter(p -> p.getUser() != null) // Ignorer les participants externes (user = null)
                .map(p -> p.getUser().getId())//Pour chaque participant (p), on récupère son identifiant utilisateur.
                .collect(Collectors.toSet());//On transforme la liste en Set pour éviter les doublons ex: Set<Long> alreadyInvited = {8, 12, 15};

        // 4. Récupérer les utilisateurs à inviter
        List<User> users = userRepository.findAllById(dto.getParticipantIds());

        // 5. Traiter chaque utilisateur
        users.forEach(user -> {
            // 5.1 Vérifier que c'est bien un copropriétaire
            boolean isCoproprietaire = user.getRole().getName() == ERole.ROLE_COPROPRIETAIRE;

            if (!isCoproprietaire) {
                throw new BadRequestException("L'utilisateur " + user.getEmail() + " n'est pas un copropriétaire");
            }

            // 5.2 Ignorer si déjà invité
            if (alreadyInvited.contains(user.getId())) {
                log.info("User {} déjà invité — ignoré", user.getId());
                return;
            }

            // 5.3 Créer et sauvegarder le participant
            MeetingParticipant participant = new MeetingParticipant();
            participant.setMeeting(meeting);
            participant.setUser(user);
            participant.setRole(null); // copropriétaire ordinaire
            participantRepository.save(participant);
        });
    }

    // =========================================================================
    // LECTURE — LISTE + DÉTAIL + CALENDRIER
    // =========================================================================

    /**
     * Liste des réunions d'une résidence triées par date (plus proche en premier).
     */
    @Override
    @Transactional(readOnly = true)
    public List<MeetingSummaryDTO> getMeetingsByResidence(Long residenceId) {
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        return meetingRepository.findByResidenceOrderByMeetingDateAsc(residence)
                .stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    /**
     * Détail complet d'une réunion avec ordre du jour, documents et participants.
     */
    @Override
    @Transactional(readOnly = true)
    public MeetingDetailDTO getMeetingDetail(Long meetingId) {
        return mapToDetail(getMeetingOrThrow(meetingId));
    }

    /**
     * Vue calendrier — retourne les jours du mois qui ont au moins une réunion.
     * Le front colorie ces jours et affiche les réunions au clic.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MeetingCalendarDayDTO> getMeetingsCalendar(Long residenceId,
                                                            int year, int month) {
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Bornes du mois demandé
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Meeting> meetings = meetingRepository
                .findByResidenceAndMeetingDateBetween(residence,
                        start.atStartOfDay(),
                        end.atTime(23, 59, 59));

        // Grouper par jour
        Map<LocalDate, List<Meeting>> grouped = meetings.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getMeetingDate().toLocalDate()));

        // Construire la liste triée par date
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> MeetingCalendarDayDTO.builder()
                        .date(entry.getKey())
                        .meetings(entry.getValue().stream()
                                .map(this::mapToSummary)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // MÉTHODES PRIVÉES / HELPERS
    // =========================================================================

    private Meeting getMeetingOrThrow(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));
    }

    private void checkIsSyndicOrganizer(Meeting meeting) {
        User syndic = getCurrentUser();
        if (!meeting.getSyndic().getId().equals(syndic.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette réunion");
        }
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
            .filter(p -> p.getRole() == null && p.getExternalName() == null)
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

    /** Mapping Meeting → MeetingSummaryDTO (carte liste) */
    private MeetingSummaryDTO mapToSummary(Meeting meeting) {
        return MeetingSummaryDTO.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .type(meeting.getType())
                .status(meeting.getStatus())
                .meetingDate(meeting.getMeetingDate())
                .location(meeting.getLocation())
                .participantCount(meeting.getParticipants().size())
                .documentCount(meeting.getDocuments().size())
                .residenceId(meeting.getResidence() != null ? meeting.getResidence().getId() : null)
                .build();
    }

    /** Mapping MeetingDocument → MeetingDocumentDTO */
    private MeetingDocumentDTO mapToDocumentDTO(MeetingDocument doc) {
        return MeetingDocumentDTO.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileUrl(doc.getFileUrl())
                .fileSizeKb(doc.getFileSizeKb())
                .documentType(doc.getDocumentType())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    /** Mapping Meeting → MeetingDetailDTO (écran détail) */
    private MeetingDetailDTO mapToDetail(Meeting meeting) {
        String organizerName = meeting.getSyndic().getFirstName() + " " + meeting.getSyndic().getLastName();

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
                        .map(this::mapToDocumentDTO)
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
                .organizerName(organizerName)
                .description(meeting.getDescription())
                .participantCount(allParticipants.size())
                .agendaItems(agendaItems)
                .documents(documents)
                .participants(participants)
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
