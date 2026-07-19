package com.example.solimus.services.syndic.syndicAG;


import com.example.solimus.dtos.syndic.meeting.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.repositories.meeting.MeetingDocumentCount;
import com.example.solimus.repositories.meeting.MeetingParticipationStats;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyndicMeetingServiceImpl implements SyndicMeetingService {

    private final MeetingRepository meetingRepository;
    private final ResidenceRepository residenceRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final ActivityLogRepository activityLogRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final PropertyRepository propertyRepository;
    private final MeetingPresenceRepository meetingPresenceRepository;
    private final MeetingAgendaItemRepository meetingAgendaItemRepository;
    private final MeetingDocumentRepository meetingDocumentRepository;
    private final BudgetRepository budgetRepository;

    // =========================================================================
    // Créer Réunion
    // =========================================================================
    @Override
    @Transactional
    public void createMeeting(CreateMeetingDTO dto, List<MultipartFile> documents) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère la résidence, erreur si introuvable
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifie que cette résidence appartient bien au syndic connecté
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à créer une réunion pour cette résidence");
        }

        // Construit l'entité Meeting à partir des champs reçus
        Meeting meeting = new Meeting();
        meeting.setResidence(residence);
        meeting.setSyndic(currentSyndic);
        meeting.setTitle(dto.getTitle());
        meeting.setType(dto.getType() != null ? dto.getType() : MeetingType.ORDINARY);
        meeting.setMeetingDate(dto.getMeetingDate());
        meeting.setStartTime(dto.getStartTime());
        meeting.setEndTime(dto.getEndTime());
        meeting.setLocation(dto.getLocation());
        meeting.setConvocationSentDate(dto.getConvocationSentDate());
        meeting.setConvocationMessage(dto.getConvocationMessage());
        meeting.setSendByEmail(dto.getSendByEmail() != null ? dto.getSendByEmail() : false);
        meeting.setSendByPlatformNotification(dto.getSendByPlatformNotification() != null ? dto.getSendByPlatformNotification() : false);
        meeting.setSendBySms(dto.getSendBySms() != null ? dto.getSendBySms() : false);

        // Construit l'ordre du jour à partir de la liste de points (titre + description optionnelle)
        List<MeetingAgendaItem> agendaItems = new ArrayList<>();
        if (dto.getAgendaItems() != null) {
            int index = 0;
            for (AgendaItemDTO itemDto : dto.getAgendaItems()) {
                MeetingAgendaItem item = new MeetingAgendaItem();
                item.setMeeting(meeting);
                item.setOrderIndex(index++);
                item.setTitle(itemDto.getTitle());
                item.setDescription(itemDto.getDescription()); // description optionnelle
                item.setRequiresResolution(itemDto.getRequiresResolution() != null ? itemDto.getRequiresResolution() : false);
                agendaItems.add(item);
            }
        }
        meeting.setAgendaItems(agendaItems);

        // Sauvegarde initiale pour obtenir l'ID généré par la base,
        // nécessaire avant de pouvoir attacher les documents (relation ManyToOne vers meeting)
        Meeting savedMeeting = meetingRepository.save(meeting);

        // Upload de chaque document fourni et rattachement à la réunion.
        if (documents != null) {

            // ETAPE 1 : on valide TOUS les fichiers d'abord, avant d'uploader quoi que ce soit.
            // Si un seul fichier est invalide, on arrête tout ici — aucun fichier n'aura été
            // envoyé a MinIO, donc pas de fichier orphelin en cas d'erreur au milieu de la liste.
            for (MultipartFile file : documents) {

                if (file.isEmpty()) {
                    throw new BadRequestException("Un des fichiers fournis est vide");
                }

                long maxSizeBytes = 20L * 1024 * 1024; // 20 Mo en octets
                if (file.getSize() > maxSizeBytes) {
                    throw new BadRequestException("Le fichier " + file.getOriginalFilename() + " dépasse la taille maximale autorisée (20 Mo)");
                }

                String originalFileName = file.getOriginalFilename();
                if (originalFileName == null || !hasAllowedExtension(originalFileName)) {
                    throw new BadRequestException("Format de fichier non autorisé pour " + originalFileName + ". Formats acceptés : PDF, DOCX, XLSX");
                }
            }

            // ETAPE 2 : tous les fichiers sont validés, on peut maintenant les uploader un par un
            // Aucun type n'est imposé à ce stade — le syndic pourra préciser le type,
            // le titre et la description plus tard, depuis la page Documents générale.
            for (MultipartFile file : documents) {
                String fileUrl = minioService.uploadFile(file, "meetings");

                MeetingDocument doc = new MeetingDocument();
                doc.setMeeting(savedMeeting);
                doc.setUploadedBy(currentSyndic);
                doc.setFileName(file.getOriginalFilename());
                doc.setFileUrl(fileUrl);
                doc.setFileSizeKb(file.getSize() / 1024);
                // documentType, title, description, documentDate : rien, restent tous null

                savedMeeting.getDocuments().add(doc);
            }
        }

        // Statut décidé en dernier, selon le choix "Publier" ou "Brouillon" du syndic —
        // une fois que tout (agenda, documents) est bien construit et prêt
        if (Boolean.TRUE.equals(dto.getPublish())) {

            savedMeeting.setStatus(MeetingStatus.UPCOMING);

            // Génère la photo figée des participants (copropriétaires + tantièmes)
            // directement si l'AG est publiée dès sa création
            generateParticipants(savedMeeting);

        } else {
            // Reste en brouillon : pas de participants générés tant que le syndic n'a pas publié
            savedMeeting.setStatus(MeetingStatus.DRAFT);
        }

        // Sauvegarde finale avec tout (agenda, documents, statut définitif)
        meetingRepository.save(savedMeeting);

        // Tracer l'activité dans le journal
        ActivityLog activityLog = ActivityLog.builder()
                .residence(residence)
                .type(ActivityType.MEETING_CREATED)
                .relatedEntityType("MEETING")
                .relatedEntityId(savedMeeting.getId())
                .actor(currentSyndic)
                .message("Nouvelle assemblée générale créée")
                .detail(savedMeeting.getTitle())
                .build();
        activityLogRepository.save(activityLog);

    }


    // =========================================================================
    // Lister Réunion
    // =========================================================================

    @Override
    @Transactional (readOnly = true)
    public AGListResponseDTO getMeetingsList(MeetingStatus status, String search, Integer page, Integer size) {

        User currentSyndic = getCurrentUser();

        // Pagination : valeurs par défaut si non fournies
        int actualPage = page != null ? page : 0;
        int actualSize = size != null ? size : 10;
        Pageable pageable = PageRequest.of(actualPage, actualSize);

        // Nettoyage du texte de recherche (retire les espaces inutiles)
        String searchFilter = null;
        if (search != null && !search.isBlank()) {
            searchFilter = search.trim();
        }

        // Récupère la page de reunions correspondant aux filtres
        Page<Meeting> meetingPage = meetingRepository.searchMeetings(
                currentSyndic.getId(), status, searchFilter, pageable);

        // Extrait uniquement les id des reunions de cette page,
        // nécessaires pour interroger les stats en une seule requête groupée
        List<Long> meetingIds = meetingPage.getContent().stream()
                .map(Meeting::getId) // pour chaque reunion, on garde seulement son id
                .collect(Collectors.toList());

        // Récupère les stats de participation et le nombre de documents
        // pour TOUTES les reunions de la page, en 2 requêtes seulement
        List<MeetingParticipationStats> statsList = new ArrayList<>();
        List<MeetingDocumentCount> docCountList = new ArrayList<>();

        if (!meetingIds.isEmpty()) {
            statsList = meetingRepository.findParticipationStats(meetingIds);
            docCountList = meetingRepository.countDocumentsByMeetingIds(meetingIds);
        }

        // Nécessaire pour pouvoir utiliser ces listes a l'interieur du stream juste apres
        final List<MeetingParticipationStats> finalStatsList = statsList;
        final List<MeetingDocumentCount> finalDocCountList = docCountList;

        // Construit une carte AG (AGCardDTO) pour chaque reunion de la page
        List<AGCardDTO> cardList = meetingPage.getContent().stream()
                .map(meeting -> {

                    // Cherche, dans la liste des stats, celle qui correspond a CETTE reunion
                    MeetingParticipationStats stats = finalStatsList.stream()
                            .filter(s -> s.getMeetingId().equals(meeting.getId()))
                            .findFirst()
                            .orElse(null);

                    // Cherche, dans la liste des comptages, celui qui correspond à CETTE réunion
                    long documentsCount = finalDocCountList.stream()
                            .filter(d -> d.getMeetingId().equals(meeting.getId()))
                            .findFirst()
                            .map(d -> d.getDocumentCount())
                            .orElse(0L);

                    // Si aucune stat trouvée (stats == null), on met 0 par défaut
                    // Sinon, on récupère le nombre total de convoqués
                    long totalParticipants = stats != null ? stats.getTotalParticipants() : 0L;

                   // Même logique : nombre de personnes ayant signé leur présence
                    long presentCount = stats != null ? stats.getSignedCount() : 0L;

                    // Taux de participation pondere par tantieme, 0% par defaut (si pas de stats du tout)
                    double participationRate = 0.0;

                    // On ne calcule le pourcentage que si :
                    // 1. on a bien trouvé des stats pour cette reunion (stats != null)
                    // 2. le tantième total n'est pas vide (getTotalTantieme() != null)
                    // 3. le tantième total est supérieur a 0 (protection contre une division par zero)
                    if (stats != null && stats.getTotalTantieme() != null
                            && stats.getTotalTantieme().compareTo(BigDecimal.ZERO) > 0) {

                        // Formule : (tantieme des presents / tantieme total) x 100
                        // .doubleValue() convertit le BigDecimal en nombre à virgule classique pour la division
                        participationRate = stats.getSignedTantieme().doubleValue()
                                / stats.getTotalTantieme().doubleValue() * 100.0;
                    }

                    // Construit la carte finale avec toutes les infos calculees
                    return AGCardDTO.builder()
                            .id(meeting.getId())
                            .title(meeting.getTitle())
                            .residenceName(meeting.getResidence().getName())
                            .residenceId(meeting.getResidence().getId())
                            .status(meeting.getStatus().name())
                            .statusLabel(meeting.getStatus().getLabel())
                            .type(meeting.getType().name())
                            .typeLabel(meeting.getType().getLabel())
                            .meetingDate(meeting.getMeetingDate())
                            .startTime(meeting.getStartTime())
                            .location(meeting.getLocation())
                            .presentCount(presentCount)
                            .totalParticipants(totalParticipants)
                            .participationRate(participationRate)
                            .resolutionsCount(countResolutions(meeting.getAgendaItems()))
                            .documentsCount(documentsCount)
                            .build();
                })
                .collect(Collectors.toList());

        // Calcul des 4 compteurs KPI du haut de page (non filtrés par statut/recherche)
        long totalCount = meetingRepository.countBySyndicId(currentSyndic.getId());
        long completedCount = meetingRepository.countBySyndicIdAndStatus(currentSyndic.getId(), MeetingStatus.COMPLETED);
        long draftCount = meetingRepository.countBySyndicIdAndStatus(currentSyndic.getId(), MeetingStatus.DRAFT);
        long plannedCount = meetingRepository.countBySyndicIdAndStatusIn(
                currentSyndic.getId(), List.of(MeetingStatus.UPCOMING, MeetingStatus.IN_PROGRESS));

        AGKpiDTO kpis = AGKpiDTO.builder()
                .totalCount(totalCount)
                .plannedCount(plannedCount)
                .completedCount(completedCount)
                .draftCount(draftCount)
                .build();

        // Réponse finale : KPIs + cartes + infos de pagination
        return AGListResponseDTO.builder()
                .kpis(kpis)
                .totalMeetings((int) meetingPage.getTotalElements())
                .meetings(cardList)
                .currentPage(meetingPage.getNumber())
                .totalPages(meetingPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Détail d'une réunion (vue générale de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public MeetingDetailAGDTO getMeetingDetail(Long meetingId) {

        // Récupère la réunion, erreur si introuvable
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        // Récupère les stats de participation de CETTE réunion uniquement
        // (on réutilise la même requête groupée que pour le listing, avec une liste d'un seul id)
        List<MeetingParticipationStats> statsList =
                meetingRepository.findParticipationStats(List.of(meetingId));
        MeetingParticipationStats stats = statsList.isEmpty() ? null : statsList.get(0);

        // Nombre de convoqués et de présents, 0 par défaut si aucune stat trouvée
        int convoquesCount = stats != null ? stats.getTotalParticipants().intValue() : 0;
        int presentCount = stats != null ? stats.getSignedCount().intValue() : 0;

        // Taux de participation pondéré par tantième, réutilisé pour le bloc Quorum
        double participationRate = 0.0;
        if (stats != null && stats.getTotalTantieme() != null
                && stats.getSignedTantieme() != null
                && stats.getTotalTantieme().compareTo(BigDecimal.ZERO) > 0) {
            participationRate = stats.getSignedTantieme().doubleValue()
                    / stats.getTotalTantieme().doubleValue() * 100.0;
        }

        // Points de l'ordre du jour de cette réunion
        List<MeetingAgendaItem> agendaItems = meeting.getAgendaItems();

        // Compte les points marqués "nécessite une résolution", peu importe leur statut
        long totalResolutionsCount = countResolutions(agendaItems);

        // Parmi ces points marqués, compte ceux déjà traités (statut != EN_ATTENTE)
        long resolvedResolutionsCount = countTreatedResolutions(agendaItems);

        // Nombre de documents déjà liés à cette réunion
        long documentsCount = meeting.getDocuments().size();

        // Nombre d'entrées dans l'historique (ActivityLog) liées à cette réunion
        long historyCount = activityLogRepository
                .countByRelatedEntityTypeAndRelatedEntityId("MEETING", meetingId);

        // Récupère le budget actif de la résidence pour afficher le budget total
        BigDecimal budgetTotal = budgetRepository
                .findByResidenceIdAndStatus(meeting.getResidence().getId(), BudgetStatus.ACTIVE)
                .map(Budget::getBudgetTotal)
                .orElse(null);

        // Construit et retourne le DTO complet
        return MeetingDetailAGDTO.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .residenceName(meeting.getResidence().getName())
                .status(meeting.getStatus().name())
                .statusLabel(meeting.getStatus().getLabel())
                .type(meeting.getType().name())
                .typeLabel(meeting.getType().getLabel())
                .meetingDate(meeting.getMeetingDate())
                .startTime(meeting.getStartTime())
                .location(meeting.getLocation())

                // KPIs du haut
                .convoquesCount(convoquesCount)
                .presentCount(presentCount)
                .participationRate(participationRate)
                .resolvedResolutionsCount(resolvedResolutionsCount)
                .totalResolutionsCount(totalResolutionsCount)

                // Bloc "Informations générales"
                .budget(budgetTotal)
                .organizerName(formatActorName(meeting.getSyndic()))

                // Bloc "Quorum" (mêmes valeurs que les KPIs, réaffichées dans un bloc à part)
                .quorumPresentCount(presentCount)
                .quorumAbsentCount(convoquesCount - presentCount)

                // Badges des onglets
                .participantsTabCount(convoquesCount)
                .agendaTabCount(agendaItems.size()) // TOUS les points, marqués ou non
                .resolutionsTabCount(totalResolutionsCount) // seulement les points marqués
                .documentsTabCount(documentsCount)
                .historyTabCount(historyCount)

                .build();
    }

    // =========================================================================
    // Publier une réunion (brouillon -> planifiée)
    // =========================================================================
    @Override
    @Transactional
    public void publishMeeting(Long meetingId) {

        // Récupère la réunion, erreur si introuvable
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Vérifie que le syndic connecté est bien celui de cette réunion
        if (!meeting.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à publier cette réunion");
        }

        // On ne peut publier qu'un brouillon (pas une réunion déjà planifiée, terminée, etc.)
        if (meeting.getStatus() != MeetingStatus.DRAFT) {
            throw new BadRequestException("Cette réunion n'est pas en brouillon");
        }

        // Passe la réunion en statut "planifiée"
        meeting.setStatus(MeetingStatus.UPCOMING);

        // Sauvegarde d'abord le meeting pour que le statut soit persisté
        meetingRepository.save(meeting);

        // Génère la photo figée des participants (copropriétaires + tantièmes) au moment de la publication
        generateParticipants(meeting);

        // Trace l'événement dans l'historique de la résidence
        ActivityLog activityLog = ActivityLog.builder()
                .residence(meeting.getResidence())
                .type(ActivityType.MEETING_PUBLISHED)
                .relatedEntityType("MEETING")
                .relatedEntityId(meeting.getId())
                .actor(currentSyndic)
                .message("Assemblée générale publiée")
                .detail(meeting.getTitle())
                .build();
        activityLogRepository.save(activityLog);
    }

    // =========================================================================
    // Liste des participants d'une réunion (onglet Participants de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public MeetingParticipantsTabResponseDTO getMeetingParticipants(Long meetingId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        // Récupère tous les participants de cette réunion
        Page<MeetingParticipant> participants = meetingParticipantRepository.findByMeetingId(meetingId,pageable);

        List<MeetingParticipantRowDTO> rows = new ArrayList<>();
        long presentCount = 0;

        // Construit chaque ligne du tableau
        for (MeetingParticipant participant : participants) {

            // Récupère la présence liée à ce participant
            MeetingPresence presence = meetingPresenceRepository
                    .findByMeetingParticipantId(participant.getId());

            boolean hasSigned = presence != null && Boolean.TRUE.equals(presence.getHasSigned());
            if (hasSigned) {
                presentCount++;
            }

            MeetingParticipantRowDTO row = MeetingParticipantRowDTO.builder()
                    .participantId(participant.getId())
                    .fullName(participant.getUser().getFirstName() + " " + participant.getUser().getLastName())
                    .apartments(participant.getApartments())
                    .tantieme(presence != null ? presence.getTantiemeSnapshot() : BigDecimal.ZERO)
                    .hasSigned(hasSigned)
                    .presenceLabel(hasSigned ? "Présent" : "Absent")
                    .build();

            rows.add(row);
        }

        // Construit la réponse avec les compteurs pour les pills de filtre
        return MeetingParticipantsTabResponseDTO.builder()
                .totalCount(participants.getTotalElements())
                .presentCount(meetingParticipantRepository.countSignedByMeetingId(meetingId))
                .absentCount(participants.getTotalElements() - meetingParticipantRepository.countSignedByMeetingId(meetingId))
                .participants(rows)
                .currentPage(participants.getNumber())
                .totalPages(participants.getTotalPages())
                .build();
    }

    // =========================================================================
    // Marque un participant comme présent/absent (signature de la feuille de présence)
    // =========================================================================
    @Override
    @Transactional
    public void signPresence(Long meetingId, Long participantId, SignPresenceDTO dto) {

        // Vérifie que le participant appartient bien à cette réunion
        MeetingParticipant participant = meetingParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant introuvable"));

        if (!participant.getMeeting().getId().equals(meetingId)) {
            throw new BadRequestException("Ce participant n'appartient pas à cette réunion");
        }

        MeetingPresence presence = meetingPresenceRepository
                .findByMeetingParticipantId(participantId);

        if (presence == null) {
            throw new ResourceNotFoundException("Présence introuvable pour ce participant");
        }

        presence.setHasSigned(dto.isHasSigned());
        meetingPresenceRepository.save(presence);
    }

    // =========================================================================
    // Liste des points de l'ordre du jour (onglet Ordre du jour de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public AgendaItemsTabResponseDTO getAgendaItems(Long meetingId) {

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        List<MeetingAgendaItem> agendaItems = meeting.getAgendaItems();

        List<AgendaItemRowDTO> rows = new ArrayList<>();

        // Construit chaque ligne, avec sa position (orderIndex + 1 pour un affichage commençant à 1, pas 0)
        for (MeetingAgendaItem item : agendaItems) {
            AgendaItemRowDTO row = AgendaItemRowDTO.builder()
                    .id(item.getId())
                    .orderIndex(item.getOrderIndex() + 1)
                    .title(item.getTitle())
                    .description(item.getDescription()) // description optionnelle
                    .build();
            rows.add(row);
        }

        return AgendaItemsTabResponseDTO.builder()
                .totalCount(rows.size())
                .items(rows)
                .build();
    }

    // =========================================================================
    // Liste des résolutions d'une réunion (onglet Résolutions de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public ResolutionsTabResponseDTO getResolutions(Long meetingId) {

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        List<ResolutionRowDTO> rows = new ArrayList<>();

        // Ne garde que les points marqués comme nécessitant une résolution
        for (MeetingAgendaItem item : meeting.getAgendaItems()) {

            if (!Boolean.TRUE.equals(item.getRequiresResolution())) {
                continue; // point purement informatif, on l'ignore dans cet onglet
            }

            ResolutionRowDTO row = ResolutionRowDTO.builder()
                    .id(item.getId())
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .resolutionStatus(item.getResolutionStatus().name())
                    .resolutionStatusLabel(item.getResolutionStatus().getDescription())
                    .observations(item.getResolutionText())
                    .build();

            rows.add(row);
        }

        return ResolutionsTabResponseDTO.builder()
                .totalCount(rows.size())
                .resolutions(rows)
                .build();
    }

    // =========================================================================
    // Met à jour le statut et les observations d'une résolution
    // =========================================================================
    @Override
    @Transactional
    public void updateResolution(Long meetingId, Long agendaItemId, UpdateResolutionDTO dto) {

        MeetingAgendaItem item = meetingAgendaItemRepository.findById(agendaItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Point de l'ordre du jour introuvable"));

        // Vérifie que ce point appartient bien à cette réunion
        if (!item.getMeeting().getId().equals(meetingId)) {
            throw new BadRequestException("Ce point n'appartient pas à cette réunion");
        }

        // Vérifie que ce point est bien marqué comme nécessitant une résolution
        if (!Boolean.TRUE.equals(item.getRequiresResolution())) {
            throw new BadRequestException("Ce point n'est pas marqué comme nécessitant une résolution");
        }

        if (dto.getResolutionStatus() != null) {
            item.setResolutionStatus(dto.getResolutionStatus());
        }
        item.setResolutionText(dto.getObservations());

        meetingAgendaItemRepository.save(item);
    }

    // =========================================================================
    // Liste des documents d'une réunion (onglet Documents de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public MeetingDocumentsTabResponseDTO getMeetingDocuments(Long meetingId, int page, int size) {

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        Pageable pageable = PageRequest.of(page, size);
        Page<MeetingDocument> docPage = meetingDocumentRepository.findByMeetingId(meetingId, pageable); // Retourne les docs d'une réunion précise

        List<MeetingDocumentRowDTO> rows = new ArrayList<>();

        for (MeetingDocument doc : docPage.getContent()) {
            rows.add(toDocumentRowDTO(doc));
        }

        return MeetingDocumentsTabResponseDTO.builder()
                .totalCount(docPage.getTotalElements())
                .documents(rows)
                .currentPage(docPage.getNumber())
                .totalPages(docPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Ajoute un document à une réunion existante (bouton "Ajouter un document")
    // =========================================================================
    @Override
    @Transactional
    public List<MeetingDocumentRowDTO> addMeetingDocuments(Long meetingId, List<MultipartFile> files) {

        // Récupère la réunion, erreur si introuvable
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        // ÉTAPE 1 : on valide TOUS les fichiers d'abord, avant d'uploader quoi que ce soit
        for (MultipartFile file : files) {

            if (file.isEmpty()) {
                throw new BadRequestException("Un des fichiers fournis est vide");
            }

            long maxSizeBytes = 20L * 1024 * 1024; // 20 Mo en octets
            if (file.getSize() > maxSizeBytes) {
                throw new BadRequestException("Le fichier " + file.getOriginalFilename() + " dépasse la taille maximale autorisée (20 Mo)");
            }

            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || !hasAllowedExtension(originalFileName)) {
                throw new BadRequestException("Format de fichier non autorisé pour " + originalFileName + ". Formats acceptés : PDF, DOCX, XLSX");
            }
        }

        // ÉTAPE 2 : tous les fichiers sont validés, on peut maintenant les uploader un par un
        // Aucun type n'est imposé à ce stade — le syndic pourra préciser le type,
        // le titre et la description plus tard, depuis la page Documents générale.
        List<MeetingDocumentRowDTO> results = new ArrayList<>();

        for (MultipartFile file : files) {

            String fileUrl = minioService.uploadFile(file, "meetings");

            MeetingDocument doc = new MeetingDocument();
            doc.setMeeting(meeting);
            doc.setUploadedBy(getCurrentUser());
            doc.setFileName(file.getOriginalFilename());
            doc.setFileUrl(fileUrl);
            doc.setFileSizeKb(file.getSize() / 1024);
            // documentType, title, description, documentDate : rien, restent tous null

            MeetingDocument savedDoc = meetingDocumentRepository.save(doc);

            ActivityLog activityLog = ActivityLog.builder()
                    .residence(meeting.getResidence())
                    .type(ActivityType.MEETING_DOCUMENT_ADDED)
                    .relatedEntityType("MEETING_DOCUMENT")
                    .relatedEntityId(savedDoc.getId())
                    .message("Document ajouté")
                    .detail(savedDoc.getFileName())
                    .build();
            activityLogRepository.save(activityLog);

            results.add(toDocumentRowDTO(savedDoc));
        }

        return results;
    }

    // =========================================================================
    // Historique des événements d'une réunion (onglet Historique de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public MeetingHistoryTabResponseDTO getMeetingHistory(Long meetingId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        // Récupère tous les logs liés à cette réunion, triés du plus récent au plus ancien
        Page<ActivityLog> logs = activityLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc("MEETING", meetingId,pageable);

        List<MeetingHistoryRowDTO> rows = new ArrayList<>();

        for (ActivityLog log : logs) {

            MeetingHistoryRowDTO row = MeetingHistoryRowDTO.builder()
                    .message(log.getMessage())
                    .actorName(formatActorName(log.getActor()))
                    .createdAt(log.getCreatedAt())
                    .build();

            rows.add(row);
        }

        return MeetingHistoryTabResponseDTO.builder()
                .totalCount(logs.getTotalElements())
                .history(rows)
                .currentPage(logs.getNumber())
                .totalPages(logs.getTotalPages())
                .build();
    }

    // =========================================================================
    // Méthodes Utilitaires
    // =========================================================================

    // =========================================================================
    // Génère les participants d'une réunion
    // =========================================================================
    // Crée un MeetingParticipant + MeetingPresence pour chaque copropriétaire de la résidence,
    // avec son tantième cumulé (somme de tous ses lots dans cette résidence).
    // Cette photo est figée au moment de la publication : elle ne change plus ensuite,
    // même si un lot change de propriétaire ou de tantième par la suite.
    private void generateParticipants(Meeting meeting) {

        // Récupère tous les lots occupés de la résidence (lots sans propriétaire ignorés)
        List<Property> properties = propertyRepository.findByResidenceIdAndOwnerIsNotNull(
                meeting.getResidence().getId());

        // Casier qui regroupe les lots par copropriétaire : clé = id du propriétaire, valeur = son regroupement
        Map<Long, OwnerAggregate> aggregatesByOwnerId = new HashMap<>();

        // Parcourt chaque lot et cumule les tantièmes et les références de lots par copropriétaire
        for (Property property : properties) {

            Long ownerId = property.getOwner().getId();

            if (aggregatesByOwnerId.containsKey(ownerId)) {
                // Ce propriétaire a déjà un casier : on ajoute le tantième et la référence de ce lot supplémentaire
                OwnerAggregate existing = aggregatesByOwnerId.get(ownerId);
                existing.totalTantieme = existing.totalTantieme.add(property.getTantieme());
                existing.apartmentReferences.add(property.getReference());
            } else {
                // Nouveau propriétaire : on crée son casier avec le tantième et la référence de son premier lot
                OwnerAggregate newAggregate = new OwnerAggregate();
                newAggregate.owner = property.getOwner();
                newAggregate.totalTantieme = property.getTantieme();
                newAggregate.apartmentReferences.add(property.getReference());
                aggregatesByOwnerId.put(ownerId, newAggregate);
            }
        }

        // Pour chaque copropriétaire regroupé, crée sa ligne de convocation et de présence
        for (OwnerAggregate aggregate : aggregatesByOwnerId.values()) {

            // Assemble les références de lots en un seul texte séparé par virgule (ex: "Apt 8D, Apt 4E")
            String apartmentsText = String.join(", ", aggregate.apartmentReferences);

            // Crée le lien entre ce copropriétaire et cette réunion
            MeetingParticipant participant = new MeetingParticipant();
            participant.setMeeting(meeting);
            participant.setUser(aggregate.owner);
            participant.setApartments(apartmentsText); // fige la liste des lots au moment de la publication
            MeetingParticipant savedParticipant = meetingParticipantRepository.save(participant);

            // Crée sa ligne de présence, avec le tantième figé et aucune signature pour l'instant
            MeetingPresence presence = new MeetingPresence();
            presence.setMeetingParticipant(savedParticipant);
            presence.setTantiemeSnapshot(aggregate.totalTantieme); // fige le tantième au moment de la publication
            presence.setHasSigned(false); // personne n'a encore signé, l'AG n'a pas encore eu lieu
            meetingPresenceRepository.save(presence);
        }
    }


    // Classe interne : regroupe les lots d'un même copropriétaire pour la génération des participants
    private static class OwnerAggregate {
        User owner;
        BigDecimal totalTantieme = BigDecimal.ZERO;              // somme des tantièmes de tous ses lots
        List<String> apartmentReferences = new ArrayList<>();    // références de tous ses lots (ex: "Apt 8D")
    }

    // Convertit une entité MeetingDocument en DTO d'affichage, en gérant les champs optionnels
    private MeetingDocumentRowDTO toDocumentRowDTO(MeetingDocument doc) {
        return MeetingDocumentRowDTO.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileUrl(doc.getFileUrl())
                .fileSizeKb(doc.getFileSizeKb())
                .documentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null)
                .documentTypeLabel(doc.getDocumentType() != null ? doc.getDocumentType().getLabel() : null)
                .title(doc.getTitle())
                .description(doc.getDescription())
                .documentDate(doc.getDocumentDate())
                .uploadedByName(formatActorName(doc.getUploadedBy()))
                .meetingId(doc.getMeeting().getId())
                .meetingTitle(doc.getMeeting().getTitle())
                .residenceName(doc.getMeeting().getResidence().getName())
                .createdAt(doc.getCreatedAt())
                .build();
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

    // Vérifie que le nom de fichier se termine par une extension autorisée (insensible a la casse)
    private boolean hasAllowedExtension(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        return lowerCaseName.endsWith(".pdf")
                || lowerCaseName.endsWith(".docx")
                || lowerCaseName.endsWith(".xlsx");
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Syndic non trouvé"));
    }

    // Compte les points marqués comme "nécessitant une résolution", peu importe leur statut
    private long countResolutions(List<MeetingAgendaItem> agendaItems) {
        long count = 0;
        for (MeetingAgendaItem item : agendaItems) {
            if (Boolean.TRUE.equals(item.getRequiresResolution())) {
                count++;
            }
        }
        return count;
    }
    // Parmi les points nécessitant une résolution, compte ceux déjà traités (statut != EN_ATTENTE)
    private long countTreatedResolutions(List<MeetingAgendaItem> agendaItems) {
        long count = 0;
        for (MeetingAgendaItem item : agendaItems) {
            boolean needsResolution = Boolean.TRUE.equals(item.getRequiresResolution());
            boolean isTreated = item.getResolutionStatus() != ResolutionStatus.EN_ATTENTE;
            if (needsResolution && isTreated) {
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // Supprimer une réunion
    // =========================================================================
    @Override
    @Transactional
    public void deleteMeeting(Long meetingId) {

        // Récupère la réunion, erreur si introuvable
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Vérifie que le syndic connecté est bien celui de cette réunion
        if (!meeting.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à supprimer cette réunion");
        }

        // On ne peut supprimer que les réunions en brouillon
        if (meeting.getStatus() != MeetingStatus.DRAFT) {
            throw new BadRequestException("Seules les réunions en brouillon peuvent être supprimées");
        }

        // Supprime les fichiers MinIO associés aux documents
        for (MeetingDocument doc : meeting.getDocuments()) {
            if (doc.getFileUrl() != null) {
                minioService.deleteFile(doc.getFileUrl());
            }
        }

        // La suppression en cascade (CascadeType.ALL) sur les relations
        // supprimera automatiquement : agendaItems, documents, participants, présences
        meetingRepository.delete(meeting);

        // Trace l'événement dans l'historique de la résidence
        ActivityLog activityLog = ActivityLog.builder()
                .residence(meeting.getResidence())
                .type(ActivityType.MEETING_DELETED)
                .relatedEntityType("MEETING")
                .relatedEntityId(meetingId)
                .actor(currentSyndic)
                .message("Assemblée générale supprimée")
                .detail(meeting.getTitle())
                .build();
        activityLogRepository.save(activityLog);
    }

    // =========================================================================
    // Module Document
    // =========================================================================

    // =========================================================================
    // Liste légère des réunions d'une résidence (pour le sélecteur de la modale)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<MeetingSummaryDTO> getMeetingSummariesByResidence(Long residenceId) {
        return meetingRepository.findMeetingSummariesByResidenceId(residenceId);
    }

    // =========================================================================
    // Ajoute un nouveau document complet (page Documents générale)
    // =========================================================================
    @Override
    @Transactional
    public MeetingDocumentRowDTO createMeetingDocument(CreateMeetingDocumentDTO dto, MultipartFile file) {

        // Récupère la réunion, erreur si introuvable
        Meeting meeting = meetingRepository.findById(dto.getMeetingId())
                .orElseThrow(() -> new ResourceNotFoundException("Réunion introuvable"));

        // Vérifie que le fichier n'est pas vide
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Aucun fichier fourni");
        }

        // Vérifie la taille maximale : 20 Mo
        long maxSizeBytes = 20L * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException("Le fichier dépasse la taille maximale autorisée (20 Mo)");
        }

        // Vérifie l'extension autorisée
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !hasAllowedExtension(originalFileName)) {
            throw new BadRequestException("Format de fichier non autorisé. Formats acceptés : PDF, DOCX, XLSX");
        }

        // Envoie le fichier vers MinIO, une fois toutes les vérifications passées
        String fileUrl = minioService.uploadFile(file, "meetings");

        // Construit et sauvegarde l'entité document, avec toutes les métadonnées fournies
        MeetingDocument doc = new MeetingDocument();
        doc.setMeeting(meeting);
        doc.setUploadedBy(getCurrentUser());
        doc.setFileName(originalFileName);
        doc.setFileUrl(fileUrl);
        doc.setFileSizeKb(file.getSize() / 1024);
        doc.setDocumentType(dto.getDocumentType()); // reste null si non fourni
        doc.setTitle(dto.getTitle());
        doc.setDescription(dto.getDescription());
        doc.setDocumentDate(dto.getDocumentDate());

        MeetingDocument savedDoc = meetingDocumentRepository.save(doc);

        // Trace l'événement dans l'historique
        ActivityLog activityLog = ActivityLog.builder()
                .residence(meeting.getResidence())
                .type(ActivityType.MEETING_DOCUMENT_ADDED)
                .relatedEntityType("MEETING_DOCUMENT")
                .relatedEntityId(savedDoc.getId())
                .message("Document ajouté")
                .detail(savedDoc.getTitle() != null ? savedDoc.getTitle() : savedDoc.getFileName())
                .build();
        activityLogRepository.save(activityLog);

        return toDocumentRowDTO(savedDoc);
    }

    // =========================================================================
    // Met à jour les métadonnées d'un document déjà existant
    // =========================================================================
    @Override
    @Transactional
    public MeetingDocumentRowDTO updateMeetingDocument(Long documentId, UpdateMeetingDocumentDTO dto) {

        // Récupère le document, erreur si introuvable
        MeetingDocument doc = meetingDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));

        // Ne modifie que les champs fournis, laisse les autres intacts
        if (dto.getTitle() != null) {
            doc.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            doc.setDescription(dto.getDescription());
        }
        if (dto.getDocumentDate() != null) {
            doc.setDocumentDate(dto.getDocumentDate());
        }
        if (dto.getDocumentType() != null) {
            doc.setDocumentType(dto.getDocumentType());
        }

        MeetingDocument savedDoc = meetingDocumentRepository.save(doc);

        // Trace la modification dans l'historique
        ActivityLog activityLog = ActivityLog.builder()
                .residence(doc.getMeeting().getResidence())
                .type(ActivityType.MEETING_DOCUMENT_UPDATED)
                .relatedEntityType("MEETING_DOCUMENT")
                .relatedEntityId(savedDoc.getId())
                .message("Document modifié")
                .detail(savedDoc.getTitle() != null ? savedDoc.getTitle() : savedDoc.getFileName())
                .build();
        activityLogRepository.save(activityLog);

        return toDocumentRowDTO(savedDoc);
    }

    // =========================================================================
    // Listing général des documents AG (page Documents, toutes résidences confondues)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public MeetingDocumentListResponseDTO getMeetingDocumentsList(String search, MeetingDocumentType documentType,
                                                                  Long residenceId, int page, int size) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Prépare la pagination avec la page et la taille demandées
        Pageable pageable = PageRequest.of(page, size);

        // Nettoie le texte de recherche (retire les espaces inutiles, met null si vide)
        String searchFilter = null;
        if (search != null && !search.isBlank()) {
            searchFilter = search.trim();
        }

        // Récupère la page de documents correspondant aux filtres (recherche, type, résidence)
        Page<MeetingDocument> docPage = meetingDocumentRepository.searchDocuments(
                currentSyndic.getId(), searchFilter, documentType, residenceId, pageable);

        // Convertit chaque document trouvé en DTO d'affichage
        List<MeetingDocumentRowDTO> rows = new ArrayList<>();
        for (MeetingDocument doc : docPage.getContent()) {
            rows.add(toDocumentRowDTO(doc));
        }

        // Construit la réponse finale : documents de la page + infos de pagination
        return MeetingDocumentListResponseDTO.builder()
                .totalCount(docPage.getTotalElements())
                .documents(rows)
                .currentPage(docPage.getNumber())
                .totalPages(docPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Détail d'un document AG (page détail, avec quorum + documents liés + historique)
    // =========================================================================
    @Override
    @Transactional (readOnly = true)
    public MeetingDocumentDetailDTO getMeetingDocumentDetail(Long documentId) {

        // Récupère le document, erreur si introuvable
        MeetingDocument document = meetingDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));

        Meeting meeting = document.getMeeting();

        // Récupère les stats de participation, sous forme de liste d'un seul élément
        // (findParticipationStats est conçue pour plusieurs réunions à la fois, ici on ne lui donne qu'un id)
        List<MeetingParticipationStats> statsList = meetingRepository.findParticipationStats(List.of(meeting.getId()));

        // Si la liste est vide (réunion pas encore publiée, aucune stat trouvée), stats = null
        // Sinon, on prend le seul élément de la liste
        MeetingParticipationStats stats = statsList.isEmpty() ? null : statsList.get(0);

        // Nombre de convoqués : 0 par défaut si aucune stat trouvée
        long convoquesCount = stats != null ? stats.getTotalParticipants() : 0;

        // Nombre de présents (ayant signé) : 0 par défaut si aucune stat trouvée
        long participantsCount = stats != null ? stats.getSignedCount() : 0;

        // Taux de participation pondéré par tantième, 0% par défaut
        double quorumPercentage = 0.0;

        // On ne calcule le pourcentage que si on a bien des stats ET un tantième total valide (> 0),
        // pour éviter une division par zéro
        if (stats != null && stats.getTotalTantieme() != null
                && stats.getTotalTantieme().compareTo(BigDecimal.ZERO) > 0) {
            quorumPercentage = stats.getSignedTantieme().doubleValue()
                    / stats.getTotalTantieme().doubleValue() * 100.0;
        }

        // Récupère tous les autres documents de la même réunion, en excluant celui qu'on regarde actuellement
        List<MeetingDocument> linkedDocs = meetingDocumentRepository.findByMeetingIdAndIdNot(meeting.getId(), documentId);

        // Convertit chaque document lié trouvé en DTO d'affichage
        List<MeetingDocumentRowDTO> linkedDocumentDTOs = new ArrayList<>();
        for (MeetingDocument linked : linkedDocs) {
            linkedDocumentDTOs.add(toDocumentRowDTO(linked));
        }

        // Parcourt tous les points de l'ordre du jour de la réunion parente
        List<ResolutionRowDTO> resolutionDTOs = new ArrayList<>();
        for (MeetingAgendaItem item : meeting.getAgendaItems()) {

            // Ignore les points purement informatifs, ne garde que ceux marqués "nécessite une résolution"
            if (!Boolean.TRUE.equals(item.getRequiresResolution())) {
                continue;
            }

            // Construit la ligne résolution pour ce point
            resolutionDTOs.add(ResolutionRowDTO.builder()
                    .id(item.getId())
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .resolutionStatus(item.getResolutionStatus().name())
                    .resolutionStatusLabel(item.getResolutionStatus().getDescription())
                    .observations(item.getResolutionText())
                    .build());
        }

        // Récupère tous les logs d'historique liés spécifiquement à CE document (pas à la réunion entière)
        List<ActivityLog> logs = activityLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc("MEETING_DOCUMENT", documentId);

        // Convertit chaque log en DTO d'affichage
        List<MeetingHistoryRowDTO> historyDTOs = new ArrayList<>();
        for (ActivityLog log : logs) {
            historyDTOs.add(MeetingHistoryRowDTO.builder()
                    .message(log.getMessage())
                    .actorName(formatActorName(log.getActor()))
                    .createdAt(log.getCreatedAt())
                    .build());
        }

        // Extrait le format depuis le nom de fichier (ex: "PV.pdf" -> "PDF")
        String format = null;
        if (document.getFileName() != null && document.getFileName().contains(".")) {
            String extension = document.getFileName().substring(document.getFileName().lastIndexOf(".") + 1);
            format = extension.toUpperCase();
        }

        // Construit et retourne le DTO complet, avec toutes les infos assemblées
        return MeetingDocumentDetailDTO.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileUrl(document.getFileUrl())
                .documentType(document.getDocumentType() != null ? document.getDocumentType().name() : null)
                .documentTypeLabel(document.getDocumentType() != null ? document.getDocumentType().getLabel() : null)
                .title(document.getTitle())
                .description(document.getDescription())
                .documentDate(document.getDocumentDate())
                .fileSizeKb(document.getFileSizeKb())
                .format(format)
                .uploadedByName(formatActorName(document.getUploadedBy()))
                .createdAt(document.getCreatedAt())
                .meetingId(meeting.getId())
                .meetingTitle(meeting.getTitle())
                .meetingType(meeting.getType().name())
                .meetingTypeLabel(meeting.getType().getLabel())
                .residenceName(meeting.getResidence().getName())
                .meetingDate(meeting.getMeetingDate())
                .meetingStartTime(meeting.getStartTime())
                .location(meeting.getLocation())
                .organizerName(formatActorName(meeting.getSyndic()))
                .convoquesCount(convoquesCount)
                .participantsCount(participantsCount)
                .quorumPercentage(quorumPercentage)
                .linkedDocuments(linkedDocumentDTOs)
                .resolutions(resolutionDTOs)
                .history(historyDTOs)
                .build();
    }

}
