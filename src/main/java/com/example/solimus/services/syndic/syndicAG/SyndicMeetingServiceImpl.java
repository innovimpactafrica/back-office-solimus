/**package com.example.solimus.services.syndic.syndicAG;

import com.example.solimus.dtos.meeting.*;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ActivityType;
import com.example.solimus.enums.AttendanceStatus;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private final MeetingPresenceRepository presenceRepository;
    private final ResidenceRepository residenceRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ActivityLogRepository activityLogRepository;
    private final MinioService minioService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // =========================================================================
    // SYNDIC — CRÉATION RÉUNION
    // =========================================================================

    /**
     * Crée une AG en statut DRAFT (Étape 1 — informations générales).
     * Les étapes suivantes (ordre du jour, documents, participants) complètent
     * cette même AG via son id.
     */
   /** @Override
    @Transactional
    public MeetingSummaryDTO createMeeting(CreateMeetingDTO dto) {
        // 1. Récupérer le syndic connecté
        User syndic = getCurrentUser();

        // 2. Vérifier que la résidence existe et appartient au syndic
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (residence.getSyndic() == null || !residence.getSyndic().getId().equals(syndic.getId())) {
            throw new ForbiddenException("Cette résidence ne vous appartient pas");
        }

        // 3. Créer l'AG en statut DRAFT
        Meeting meeting = new Meeting();
        meeting.setTitle(dto.getTitle());
        meeting.setType(dto.getType());
        meeting.setStatus(MeetingStatus.DRAFT);
        meeting.setMeetingDate(dto.getMeetingDate());
        meeting.setStartTime(dto.getStartTime());
        meeting.setEndTime(dto.getEndTime());
        meeting.setLocation(dto.getLocation());
        meeting.setResidence(residence);
        meeting.setSyndic(syndic);

        Meeting saved = meetingRepository.save(meeting);

        // Enregistrer l'événement dans le journal d'activité de la résidence
        ActivityLog activityLog = new ActivityLog();
        activityLog.setResidence(residence);
        activityLog.setType(ActivityType.MEETING_CREATED);
        activityLog.setRelatedEntityType("MEETING");
        activityLog.setRelatedEntityId(saved.getId());
        activityLog.setActor(syndic);
        activityLog.setMessage("AG créée");
        activityLog.setDetail(saved.getTitle());
        activityLogRepository.save(activityLog);

        log.info("AG créée (DRAFT) : {} pour la résidence {}", saved.getTitle(), residence.getName());

        return mapToSummary(saved);
    }

    /**
     * Dashboard des AG — KPIs + liste filtrable
     */
   /** @Override
    @Transactional(readOnly = true)
    public MeetingDashboardResponseDTO getMeetingsDashboard(String search, String status, Integer page, Integer size) {
        // Récupérer le syndic connecté
        User syndic = getCurrentUser();

        // Calculer les KPIs
        Integer totalCount = (int) meetingRepository.countBySyndicId(syndic.getId());
        Integer planifieesCount = (int) meetingRepository.countBySyndicIdAndStatus(syndic.getId(), MeetingStatus.UPCOMING);
        Integer termineesCount = (int) meetingRepository.countBySyndicIdAndStatusAndCurrentYear(syndic.getId(), MeetingStatus.COMPLETED);
        Integer brouillonsCount = (int) meetingRepository.countBySyndicIdAndStatus(syndic.getId(), MeetingStatus.DRAFT);

        // Convertir le statut string en enum si fourni
        MeetingStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = MeetingStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                // Statut invalide, ignorer le filtre
            }
        }

        // Récupérer les AG paginées avec filtres
        Pageable pageable = PageRequest.of(page, size);
        Page<Meeting> meetingsPage = meetingRepository.findBySyndicIdWithFilters(
                syndic.getId(), search, statusEnum, pageable);

        // Récupérer les IDs des AG de la page pour précharger les présences
        List<Long> meetingIds = new ArrayList<>();
        for (Meeting meeting : meetingsPage.getContent()) {
            meetingIds.add(meeting.getId());
        }

        // Précharger toutes les présences pour ces AG (éviter N+1)
        List<MeetingPresence> allPresences = new ArrayList<>();
        for (Long meetingId : meetingIds) {
            List<MeetingPresence> presences = presenceRepository.findByMeetingParticipantMeetingId(meetingId);
            allPresences.addAll(presences);
        }

        // Construire la liste de DTOs
        List<MeetingCardDTO> cards = new ArrayList<>();
        for (Meeting meeting : meetingsPage.getContent()) {
            // Trouver les présences de cette AG
            List<MeetingPresence> meetingPresences = new ArrayList<>();
            for (MeetingPresence presence : allPresences) {
                if (presence.getMeetingParticipant().getMeeting().getId().equals(meeting.getId())) {
                    meetingPresences.add(presence);
                }
            }

            // Calculer headcountRatio (comptage de têtes)
            Integer presentOrProxyCount = 0;
            for (MeetingPresence presence : meetingPresences) {
                if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT 
                    || presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
                    presentOrProxyCount++;
                }
            }

            Integer convocatedCount = participantRepository.findByMeetingId(meeting.getId()).size();
            String headcountRatio = presentOrProxyCount + "/" + convocatedCount;

            // Calculer quorumPercentage (pondéré par tantième)
            java.math.BigDecimal sumTantiemePresentOrRepresented = java.math.BigDecimal.ZERO;
            for (MeetingPresence presence : meetingPresences) {
                if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT 
                    || presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
                    if (presence.getTantiemeSnapshot() != null) {
                        sumTantiemePresentOrRepresented = sumTantiemePresentOrRepresented.add(presence.getTantiemeSnapshot());
                    }
                }
            }

            java.math.BigDecimal sumTantiemeTotal = java.math.BigDecimal.ZERO;
            for (MeetingPresence presence : meetingPresences) {
                if (presence.getTantiemeSnapshot() != null) {
                    sumTantiemeTotal = sumTantiemeTotal.add(presence.getTantiemeSnapshot());
                }
            }

            Double quorumPercentage = 0.0;
            if (sumTantiemeTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                quorumPercentage = sumTantiemePresentOrRepresented
                        .divide(sumTantiemeTotal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .doubleValue();
            }

            // Construire le DTO
            MeetingCardDTO card = MeetingCardDTO.builder()
                    .title(meeting.getTitle())
                    .residenceName(meeting.getResidence().getName())
                    .status(meeting.getStatus())
                    .type(meeting.getType())
                    .meetingDate(meeting.getMeetingDate())
                    .startTime(meeting.getStartTime())
                    .location(meeting.getLocation())
                    .headcountRatio(headcountRatio)
                    .quorumPercentage(quorumPercentage)
                    .resolutionsCount(0) // 0 pour l'instant, sera rempli quand Resolution existera
                    .documentsCount(meeting.getDocuments() != null ? meeting.getDocuments().size() : 0)
                    .build();
            cards.add(card);
        }

        // Construire et retourner la réponse
        return MeetingDashboardResponseDTO.builder()
                .totalCount(totalCount)
                .planifieesCount(planifieesCount)
                .termineesCount(termineesCount)
                .brouillonsCount(brouillonsCount)
                .meetings(cards)
                .build();
    }

    /**
     * Met à jour les paramètres de convocation (Étape 2).
     * Enregistre seulement les préférences, aucun envoi réel à ce stade.
     */
   /** @Override
    @Transactional
    public MeetingSummaryDTO updateConvocation(Long meetingId, UpdateMeetingConvocationDTO dto) {
        // 1. Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // 2. Récupérer l'AG et vérifier qu'elle appartient au syndic
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("AG introuvable"));

        if (!meeting.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette AG ne vous appartient pas");
        }

        // 3. Vérifier qu'au moins un canal d'envoi est sélectionné
        boolean atLeastOneChannel = (dto.getSendByEmail() != null && dto.getSendByEmail())
                || (dto.getSendByPlatformNotification() != null && dto.getSendByPlatformNotification())
                || (dto.getSendBySms() != null && dto.getSendBySms());

        if (!atLeastOneChannel) {
            throw new BadRequestException("Sélectionnez au moins un canal d'envoi pour la convocation");
        }

        // 4. Mettre à jour les champs de convocation
        meeting.setConvocationSentDate(dto.getConvocationSentDate());
        meeting.setConvocationMessage(dto.getConvocationMessage());
        meeting.setSendByEmail(dto.getSendByEmail() != null ? dto.getSendByEmail() : false);
        meeting.setSendByPlatformNotification(dto.getSendByPlatformNotification() != null ? dto.getSendByPlatformNotification() : false);
        meeting.setSendBySms(dto.getSendBySms() != null ? dto.getSendBySms() : false);

        Meeting saved = meetingRepository.save(meeting);

        log.info("Paramètres de convocation mis à jour pour l'AG {}", meetingId);

        return mapToSummary(saved);
    }

    /**
     * Publie une AG — envoie les convocations et fige les participants.
     */
   /** @Override
    @Transactional
    public MeetingSummaryDTO publishMeeting(Long meetingId) {
        // 1. Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // 2. Récupérer l'AG et vérifier qu'elle appartient au syndic
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("AG introuvable"));

        if (!meeting.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette AG ne vous appartient pas");
        }

        // 3. Vérifier que l'AG est encore en DRAFT
        if (meeting.getStatus() != MeetingStatus.DRAFT) {
            throw new BadRequestException("Cette AG a déjà été publiée");
        }

        // 4. Vérifications préalables
        if (meeting.getAgendaItems() == null || meeting.getAgendaItems().isEmpty()) {
            throw new BadRequestException("Ajoutez au moins un point à l'ordre du jour avant de publier");
        }
        if (meeting.getConvocationSentDate() == null) {
            throw new BadRequestException("Complétez les informations de convocation avant de publier");
        }
        boolean atLeastOneChannel = (meeting.getSendByEmail() != null && meeting.getSendByEmail())
                || (meeting.getSendByPlatformNotification() != null && meeting.getSendByPlatformNotification())
                || (meeting.getSendBySms() != null && meeting.getSendBySms());
        if (!atLeastOneChannel) {
            throw new BadRequestException("Sélectionnez au moins un canal d'envoi avant de publier");
        }

        // 5. Ajouter les participants (copropriétaires de la résidence)
        List<Property> properties = propertyRepository.findByResidenceIdAndOwnerIsNotNull(meeting.getResidence().getId());
        List<Long> alreadyAddedOwnerIds = new ArrayList<>();

        for (Property property : properties) {
            Long ownerId = property.getOwner().getId();

            boolean alreadyAdded = false;
            for (Long addedId : alreadyAddedOwnerIds) {
                if (addedId.equals(ownerId)) {
                    alreadyAdded = true;
                    break;
                }
            }

            if (!alreadyAdded) {
                MeetingParticipant participant = new MeetingParticipant();
                participant.setMeeting(meeting);
                participant.setUser(property.getOwner());
                participantRepository.save(participant);

                alreadyAddedOwnerIds.add(ownerId);
            }
        }

        // 6. Envoi des convocations
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(meetingId);

        for (MeetingParticipant participant : participants) {
            User coOwner = participant.getUser();

            // Email
            if (meeting.getSendByEmail() != null && meeting.getSendByEmail()) {
                try {
                    String subject = "Convocation — " + meeting.getTitle();
                    String body = buildConvocationEmailBody(meeting);
                    emailService.sendEmail(coOwner.getEmail(), subject, body);
                } catch (Exception e) {
                    log.error("Erreur envoi email convocation à {}", coOwner.getEmail(), e);
                }
            }

            // Notification push
            if (meeting.getSendByPlatformNotification() != null && meeting.getSendByPlatformNotification()) {
                if (coOwner.isNotificationsEnabled()) {
                    String title = "Convocation AG";
                    String body = meeting.getTitle() + " — " + meeting.getMeetingDate();
                    notificationService.sendPush(coOwner.getId(), title, body);
                }
            }
        }

        // 7. Changement de statut
        meeting.setStatus(MeetingStatus.UPCOMING);
        Meeting saved = meetingRepository.save(meeting);

        // 8. Log d'activité
        ActivityLog activityLog = new ActivityLog();
        activityLog.setResidence(meeting.getResidence());
        activityLog.setType(ActivityType.MEETING_PUBLISHED);
        activityLog.setRelatedEntityType("MEETING");
        activityLog.setRelatedEntityId(meeting.getId());
        activityLog.setActor(currentSyndic);
        activityLog.setMessage("AG publiée");
        activityLog.setDetail(meeting.getTitle());
        activityLogRepository.save(activityLog);

        log.info("AG publiée : {} pour la résidence {}", meeting.getTitle(), meeting.getResidence().getName());

        return mapToSummary(saved);
    }

    private String buildConvocationEmailBody(Meeting meeting) {
        StringBuilder body = new StringBuilder();
        body.append("Convocation à l'assemblée générale\n\n");
        body.append("Titre : ").append(meeting.getTitle()).append("\n");
        body.append("Date : ").append(meeting.getMeetingDate()).append("\n");
        if (meeting.getStartTime() != null) {
            body.append("Heure : ").append(meeting.getStartTime()).append("\n");
        }
        if (meeting.getLocation() != null) {
            body.append("Lieu : ").append(meeting.getLocation()).append("\n");
        }
        if (meeting.getConvocationMessage() != null && !meeting.getConvocationMessage().isEmpty()) {
            body.append("\n").append(meeting.getConvocationMessage());
        }
        return body.toString();
    }

    // =========================================================================
    // SYNDIC — ORDRE DU JOUR
    // =========================================================================

    /**
     * Ajoute un point à l'ordre du jour d'une réunion.
     * Vérifie que c'est bien le syndic organisateur qui fait la demande.
     */
   /** @Override
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
    /**@Override
    @Transactional
    public MeetingDocumentDTO uploadDocument(Long meetingId, MultipartFile file,
                                              String fileName) {
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
        document.setDocumentType(MeetingDocumentType.PV_AG);

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
    /**@Override
    @Transactional
    public void addExternalParticipant(Long meetingId, AddExternalParticipantDTO dto) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        checkIsSyndicOrganizer(meeting);

        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeeting(meeting);
        participant.setUser(null);                  // pas un user système// pas organisateur
        participant.setExternalName(dto.getFullName());
        participant.setRoleLabel(dto.getRoleLabel());
        participantRepository.save(participant);
    }

    /**
     * Invite des copropriétaires à une réunion.
     * Vérifie que chaque ID correspond bien à un COPROPRIETAIRE.
     * Ignore les doublons (un copropriétaire déjà invité ne sera pas ajouté deux fois).
     */
   /** @Override
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
    /**@Override
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
   /** @Override
    @Transactional(readOnly = true)
    public MeetingDetailDTO getMeetingDetail(Long meetingId) {
        return mapToDetail(getMeetingOrThrow(meetingId));
    }

    /**
     * Détail d'une AG pour le syndic — KPIs + Vue Générale
     * Calcule les compteurs de présence et quorum
     */
   /** @Override
    @Transactional(readOnly = true)
    public MeetingDetailSyndicDTO getMeetingDetailSyndic(Long meetingId) {
        // Récupérer l'AG
        Meeting meeting = getMeetingOrThrow(meetingId);

        // Vérifier que l'AG appartient au syndic connecté
        checkIsSyndicOrganizer(meeting);

        // Récupérer tous les participants (convocés)
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(meetingId);
        Integer convocatedCount = participants.size();

        // Récupérer toutes les présences enregistrées
        List<MeetingPresence> presences = presenceRepository.findByMeetingParticipantMeetingId(meetingId);

        // Compter les présents (PRESENT)
        Integer presentCount = 0;
        for (MeetingPresence presence : presences) {
            if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT) {
                presentCount++;
            }
        }

        // Compter les procurations (REPRESENTE)
        Integer proxyCount = 0;
        for (MeetingPresence presence : presences) {
            if (presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
                proxyCount++;
            }
        }

        // Compter les absents (ABSENT)
        Integer absentCount = 0;
        for (MeetingPresence presence : presences) {
            if (presence.getAttendanceStatus() == AttendanceStatus.ABSENT) {
                absentCount++;
            }
        }

        // Calculer le pourcentage de participation (comptage de têtes, sans pondération)
        Double participationPercentage = 0.0;
        if (convocatedCount > 0) {
            participationPercentage = (double)(presentCount + proxyCount) / convocatedCount * 100;
        }

        // Calculer le pourcentage de quorum (pondéré par tantième)
        // Somme des tantièmes des présents/représentés
        java.math.BigDecimal sumTantiemePresentOrRepresented = java.math.BigDecimal.ZERO;
        for (MeetingPresence presence : presences) {
            if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT 
                || presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
                if (presence.getTantiemeSnapshot() != null) {
                    sumTantiemePresentOrRepresented = sumTantiemePresentOrRepresented.add(presence.getTantiemeSnapshot());
                }
            }
        }

        // Somme de tous les tantièmes (tous les convoqués)
        java.math.BigDecimal sumTantiemeTotal = java.math.BigDecimal.ZERO;
        for (MeetingPresence presence : presences) {
            if (presence.getTantiemeSnapshot() != null) {
                sumTantiemeTotal = sumTantiemeTotal.add(presence.getTantiemeSnapshot());
            }
        }

        // Calculer le pourcentage de quorum
        Double quorumPercentage = 0.0;
        if (sumTantiemeTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
            quorumPercentage = sumTantiemePresentOrRepresented
                    .divide(sumTantiemeTotal, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // Nom de l'organisateur (syndic créateur)
        String organizerName = meeting.getSyndic().getFirstName() + " " + meeting.getSyndic().getLastName();

        // Construire et retourner le DTO
        return MeetingDetailSyndicDTO.builder()
                // KPIs
                .convocatedCount(convocatedCount)
                .presentCount(presentCount)
                .proxyCount(proxyCount)
                .participationPercentage(participationPercentage)
                .quorumPercentage(quorumPercentage)
                .resolvedCount(0) // 0 pour l'instant, sera rempli quand Resolution existera
                .totalCount(0) // 0 pour l'instant, sera rempli quand Resolution existera
                // Informations générales
                .budgetAmount(null) // null pour l'instant, dépend de Resolution/ExceptionalCall
                .residenceName(meeting.getResidence().getName())
                .meetingDate(meeting.getMeetingDate())
                .startTime(meeting.getStartTime())
                .location(meeting.getLocation())
                .organizerName(organizerName)
                // Quorum (réutilisation des compteurs)
                .quorumPresentCount(presentCount)
                .quorumProxyCount(proxyCount)
                .absentCount(absentCount)
                .build();
    }

    /**
     * Liste des participants d'une AG avec filtre optionnel sur le statut de présence
     */
  /**  @Override
    @Transactional(readOnly = true)
    public MeetingParticipantsResponseDTO getMeetingParticipants(Long meetingId, String status) {
        // Récupérer l'AG
        Meeting meeting = getMeetingOrThrow(meetingId);

        // Vérifier que l'AG appartient au syndic connecté
        checkIsSyndicOrganizer(meeting);

        // Récupérer tous les participants (convocés)
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(meetingId);
        Integer totalCount = participants.size();

        // Récupérer toutes les présences enregistrées
        List<MeetingPresence> presences = presenceRepository.findByMeetingParticipantMeetingId(meetingId);

        // Calculer les compteurs globaux (indépendants du filtre)
        Integer presentCount = 0;
        Integer absentCount = 0;
        Integer proxyCount = 0;

        for (MeetingPresence presence : presences) {
            if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT) {
                presentCount++;
            } else if (presence.getAttendanceStatus() == AttendanceStatus.ABSENT) {
                absentCount++;
            } else if (presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
                proxyCount++;
            }
        }

        // Construire la liste des participants
        List<MeetingParticipantRowDTO> participantRows = new ArrayList<>();

        for (MeetingParticipant participant : participants) {
            // Trouver la présence de ce participant
            MeetingPresence presence = null;
            for (MeetingPresence p : presences) {
                if (p.getMeetingParticipant().getId().equals(participant.getId())) {
                    presence = p;
                    break;
                }
            }

            // Appliquer le filtre status si fourni
            if (status != null && !status.isEmpty()) {
                if (presence == null) {
                    continue; // Pas de présence enregistrée, ne pas inclure si filtre actif
                }
                if (!presence.getAttendanceStatus().name().equals(status)) {
                    continue; // Statut ne correspond pas au filtre
                }
            }

            // Nom du copropriétaire
            String coOwnerName = participant.getUser().getFirstName() + " " + participant.getUser().getLastName();

            // Récupérer les références des lots de ce copropriétaire dans cette résidence
            List<Property> properties = propertyRepository.findByOwnerIdAndResidenceId(
                    participant.getUser().getId(), meeting.getResidence().getId());
            List<String> apartmentReferences = new ArrayList<>();
            for (Property property : properties) {
                apartmentReferences.add(property.getReference());
            }

            // Champs de présence (null si pas encore prise)
            java.math.BigDecimal tantieme = null;
            AttendanceStatus attendanceStatus = null;
            String representedByName = null;
            Boolean hasSigned = null;

            if (presence != null) {
                tantieme = presence.getTantiemeSnapshot();
                attendanceStatus = presence.getAttendanceStatus();
                if (presence.getRepresentedByUser() != null) {
                    representedByName = presence.getRepresentedByUser().getFirstName() + " " 
                            + presence.getRepresentedByUser().getLastName();
                }
                hasSigned = presence.getHasSigned();
            }

            // Construire le DTO
            MeetingParticipantRowDTO row = MeetingParticipantRowDTO.builder()
                    .coOwnerName(coOwnerName)
                    .apartmentReferences(apartmentReferences)
                    .tantieme(tantieme)
                    .attendanceStatus(attendanceStatus)
                    .representedByName(representedByName)
                    .hasSigned(hasSigned)
                    .build();

            participantRows.add(row);
        }

        // Construire et retourner la réponse
        return MeetingParticipantsResponseDTO.builder()
                .totalCount(totalCount)
                .presentCount(presentCount)
                .absentCount(absentCount)
                .proxyCount(proxyCount)
                .participants(participantRows)
                .build();
    }

    /**
     * Liste des points de l'ordre du jour d'une AG
     */
   /** @Override
    @Transactional(readOnly = true)
    public List<MeetingAgendaItemDTO> getAgendaItems(Long meetingId) {
        // Récupérer l'AG
        Meeting meeting = getMeetingOrThrow(meetingId);

        // Vérifier que l'AG appartient au syndic connecté
        checkIsSyndicOrganizer(meeting);

        // Récupérer les points de l'ordre du jour (triés par orderIndex)
        List<MeetingAgendaItem> agendaItems = agendaItemRepository.findByMeetingIdOrderByOrderIndexAsc(meetingId);

        // Construire la liste de DTOs
        List<MeetingAgendaItemDTO> result = new ArrayList<>();
        for (MeetingAgendaItem item : agendaItems) {
            MeetingAgendaItemDTO dto = MeetingAgendaItemDTO.builder()
                    .id(item.getId())
                    .orderIndex(item.getOrderIndex())
                    .title(item.getTitle())
                    .build();
            result.add(dto);
        }

        return result;
    }

    /**
     * Liste des documents d'une AG
     */
    /**@Override
    @Transactional(readOnly = true)
    public List<MeetingDocumentDTO> getDocuments(Long meetingId) {
        // Récupérer l'AG
        Meeting meeting = getMeetingOrThrow(meetingId);

        // Vérifier que l'AG appartient au syndic connecté
        checkIsSyndicOrganizer(meeting);

        // Récupérer les documents (triés par createdAt)
        List<MeetingDocument> documents = documentRepository.findByMeetingId(meetingId);

        // Construire la liste de DTOs
        List<MeetingDocumentDTO> result = new ArrayList<>();
        for (MeetingDocument doc : documents) {
            MeetingDocumentDTO dto = mapToDocumentDTO(doc);
            result.add(dto);
        }

        return result;
    }

    /**
     * Ajouter un document à une AG
     */
   /** @Override
    @Transactional
    public MeetingDocumentDTO addDocument(Long meetingId, MultipartFile file, MeetingDocumentType documentType) {
        // Récupérer l'AG
        Meeting meeting = getMeetingOrThrow(meetingId);

        // Vérifier que l'AG appartient au syndic connecté
        checkIsSyndicOrganizer(meeting);

        // Upload vers Minio
        String fileUrl = minioService.uploadFile(file, "meetings");
        if (fileUrl == null) {
            throw new BadRequestException("Erreur lors de l'upload du document");
        }

        // Calculer la taille en KB
        long fileSizeKb = file.getSize() / 1024;

        // Créer l'entité document
        MeetingDocument document = new MeetingDocument();
        document.setMeeting(meeting);
        document.setFileName(file.getOriginalFilename());
        document.setFileUrl(fileUrl);
        document.setFileSizeKb(fileSizeKb);
        document.setDocumentType(documentType);

        // Sauvegarder
        MeetingDocument saved = documentRepository.save(document);

        // Retourner le DTO
        return mapToDocumentDTO(saved);
    }

    /**
     * Historique d'une AG (via ActivityLog)
     */
    /**@Override
    @Transactional(readOnly = true)
    public List<ActivityLogItemDTO> getMeetingHistory(Long meetingId) {
        // Récupérer l'AG
        Meeting meeting = getMeetingOrThrow(meetingId);

        // Vérifier que l'AG appartient au syndic connecté
        checkIsSyndicOrganizer(meeting);

        // Récupérer les logs d'activité pour cette AG
        List<ActivityLog> logs = activityLogRepository.findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                "MEETING", meetingId);

        // Construire la liste de DTOs
        List<ActivityLogItemDTO> result = new ArrayList<>();
        for (ActivityLog log : logs) {
            String actorName = null;
            if (log.getActor() != null) {
                actorName = log.getActor().getFirstName() + " " + log.getActor().getLastName();
            }

            ActivityLogItemDTO dto = ActivityLogItemDTO.builder()
                    .id(log.getId())
                    .type(log.getType())
                    .message(log.getMessage())
                    .detail(log.getDetail())
                    .actorName(actorName)
                    .createdAt(log.getCreatedAt())
                    .build();
            result.add(dto);
        }

        return result;
    }

    /**
     * Vue calendrier — retourne les jours du mois qui ont au moins une réunion.
     * Le front colorie ces jours et affiche les réunions au clic.
     */
    /**@Override
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
                        start,
                        end);

        // Grouper par jour
        Map<LocalDate, List<Meeting>> grouped = meetings.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getMeetingDate()));

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
    /**private List<MeetingParticipantDTO> buildParticipants(List<MeetingParticipant> all) {

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
   /** private MeetingSummaryDTO mapToSummary(Meeting meeting) {
        return MeetingSummaryDTO.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .type(meeting.getType())
                .status(meeting.getStatus())
                .meetingDate(meeting.getMeetingDate())
                .meetingStartTime(meeting.getStartTime() != null ? meeting.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
                .meetingEndTime(meeting.getEndTime() != null ? meeting.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
                .location(meeting.getLocation())
                .participantCount(meeting.getParticipants().size())
                .documentCount(meeting.getDocuments().size())
                .residenceId(meeting.getResidence() != null ? meeting.getResidence().getId() : null)
                .build();
    }

    /** Mapping MeetingDocument → MeetingDocumentDTO */
    /**private MeetingDocumentDTO mapToDocumentDTO(MeetingDocument doc) {
        return MeetingDocumentDTO.builder()
    /**            .id(doc.getId())
                .fileName(doc.getFileName())
                .fileUrl(doc.getFileName())
                .fileSizeKb(doc.getFileSizeKb())
                .documentType(doc.getDocumentType())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    /** Mapping Meeting → MeetingDetailDTO (écran détail) */
    /**private MeetingDetailDTO mapToDetail(Meeting meeting) {
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
                .meetingDate(meeting.getMeetingDate())
                .meetingStartTime(meeting.getStartTime() != null ? meeting.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
                .meetingEndTime(meeting.getEndTime() != null ? meeting.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
                .organizerName(organizerName)
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
}**/
