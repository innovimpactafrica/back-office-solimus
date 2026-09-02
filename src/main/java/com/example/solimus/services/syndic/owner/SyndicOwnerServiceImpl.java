package com.example.solimus.services.syndic.owner;

import com.example.solimus.dtos.owner.CoOwnerInterventionRowDTO;
import com.example.solimus.dtos.owner.CoOwnerInterventionsResponseDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingsDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingHistoryItemDTO;
import com.example.solimus.dtos.owner.CoOwnerResidenceDTO;
import com.example.solimus.dtos.shared.PdfFileDTO;
import com.example.solimus.dtos.syndic.owner.*;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.CoOwnerAlreadyExistsException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;

import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.shared.StatusRecalculationService;
import com.example.solimus.utils.PasswordGeneratorUtil;
import com.example.solimus.utils.PdfExportUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicOwnerServiceImpl implements SyndicOwnerService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CoOwnerProfileRepository coOwnerProfileRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final SyndicCoOwnerRelationRepository syndicCoOwnerRelationRepository;
    private final EmailService emailService;
    private final MinioService minioService;
    private final PasswordEncoder passwordEncoder;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ChargeCallRepository chargeCallRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final BudgetRepository budgetRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final MeetingPresenceRepository meetingPresenceRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final CoOwnerDocumentRepository coOwnerDocumentRepository;
    private final MeetingDocumentRepository meetingDocumentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CoOwnerDocumentUnifiedRepository coOwnerDocumentUnifiedRepository;
    private final BudgetCoOwnerAllocationRepository budgetCoOwnerAllocationRepository;
    private final StatusRecalculationService statusRecalculationService;


    //----------------------------------------------------------------------
    // Autocomplete — recherche un copropriétaire par nom, email ou téléphone
    //-----------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerSearchResultDTO> searchCoOwners(String q, Integer page, Integer size) {

        Pageable pageable = PageRequest.of(page, size);

        // on lance la recherche en base sur prénom, nom, email et téléphone
        Page<User> userPage = userRepository.searchCoOwners(q, pageable);

        List<CoOwnerSearchResultDTO> dtos = userPage.getContent().stream().map(user -> {

            //on récupère le profil complémentaire du copropriétaire
            CoOwnerProfile profile = coOwnerProfileRepository
                    .findByUserId(user.getId())
                    .orElse(null); // null si le profil n'existe pas encore — cas défensif

            return CoOwnerSearchResultDTO.builder()
                    .id(user.getId())
                    .fullName(user.getFirstName() + " " + user.getLastName())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .photoUrl(user.getProfilePhotoUrl())
                    // si le profil existe on prend ses données, sinon null
                    .title(profile != null ? profile.getTitle() : null)
                    .birthDate(profile != null ? profile.getBirthDate() : null)
                    .nationality(profile != null ? profile.getNationality() : null)
                    .secondaryPhone(profile != null ? profile.getSecondaryPhone() : null)
                    .address(profile != null ? profile.getAddress() : null)
                    .build();

        }).toList();

        return new PageImpl<>(dtos, pageable, userPage.getTotalElements());
    }

    /**
     * Assemblées Générales d'un copropriétaire (onglet AG du détail)
     */
    @Override
    @Transactional(readOnly = true)
    public CoOwnerMeetingsDTO getCoOwnerMeetings(Long coOwnerId, Long residenceId, String type, Integer year, Integer page, Integer size) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Récupérer tous les participants de ce copropriétaire pour les AG du syndic
        List<MeetingParticipant> participants = meetingParticipantRepository.findByUser(coOwner);
        List<MeetingParticipant> syndicParticipants = new ArrayList<>();
        for (MeetingParticipant participant : participants) {
            if (participant.getMeeting().getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
                // Filtre par résidence si fourni
                if (residenceId == null || participant.getMeeting().getResidence().getId().equals(residenceId)) {
                    // Filtre par type si fourni
                    if (type == null || type.isBlank() || participant.getMeeting().getType().name().equals(type)) {
                        // Filtre par année si fourni
                        if (year == null || participant.getMeeting().getMeetingDate() != null 
                                && participant.getMeeting().getMeetingDate().getYear() == year) {
                            syndicParticipants.add(participant);
                        }
                    }
                }
            }
        }

        // Calculer le taux de participation
        Integer totalMeetings = syndicParticipants.size();
        Integer presentOrProxyMeetings = 0;

        for (MeetingParticipant participant : syndicParticipants) {
            List<MeetingPresence> presences = meetingPresenceRepository.findByMeetingParticipantMeetingId(
                    participant.getMeeting().getId());
            for (MeetingPresence presence : presences) {
                if (presence.getMeetingParticipant().getId().equals(participant.getId())) {
                    if (presence.getAttendanceType() != AttendanceType.ABSENT) {
                        presentOrProxyMeetings++;
                    }
                    break;
                }
            }
        }

        Double participationRate = 0.0;
        if (totalMeetings > 0) {
            participationRate = (double) presentOrProxyMeetings / totalMeetings * 100;
        }

        // Trouver la dernière AG (meetingDate maximum)
        Meeting lastMeeting = null;
        for (MeetingParticipant participant : syndicParticipants) {
            if (lastMeeting == null || participant.getMeeting().getMeetingDate().isAfter(lastMeeting.getMeetingDate())) {
                lastMeeting = participant.getMeeting();
            }
        }

        String lastMeetingTitle = null;
        if (lastMeeting != null) {
            lastMeetingTitle = lastMeeting.getTitle();
        }

        // Construire l'historique des AG
        List<CoOwnerMeetingHistoryItemDTO> history = new ArrayList<>();
        for (MeetingParticipant participant : syndicParticipants) {
            Meeting meeting = participant.getMeeting();

            // Calculer le quorum de cette AG
            List<MeetingPresence> allPresences = meetingPresenceRepository.findByMeetingParticipantMeetingId(
                    meeting.getId());

            BigDecimal sumTantiemePresentOrRepresented = BigDecimal.ZERO;
            for (MeetingPresence presence : allPresences) {
                if (presence.getAttendanceType() != AttendanceType.ABSENT) {
                    if (presence.getTantiemeSnapshot() != null) {
                        sumTantiemePresentOrRepresented = sumTantiemePresentOrRepresented.add(presence.getTantiemeSnapshot());
                    }
                }
            }

            BigDecimal sumTantiemeTotal = java.math.BigDecimal.ZERO;
            for (MeetingPresence presence : allPresences) {
                if (presence.getTantiemeSnapshot() != null) {
                    sumTantiemeTotal = sumTantiemeTotal.add(presence.getTantiemeSnapshot());
                }
            }

            Double quorumPercentage = 0.0;
            if (sumTantiemeTotal.compareTo(BigDecimal.ZERO) > 0) {
                quorumPercentage = sumTantiemePresentOrRepresented
                        .divide(sumTantiemeTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            // Trouver la présence de ce copropriétaire (compte Présent ET Procuration comme "signé")
            Boolean hasSigned = null;
            for (MeetingPresence presence : allPresences) {
                if (presence.getMeetingParticipant().getId().equals(participant.getId())) {
                    hasSigned = presence.getAttendanceType() != AttendanceType.ABSENT;
                    break;
                }
            }

            // Construire le DTO
            CoOwnerMeetingHistoryItemDTO item = CoOwnerMeetingHistoryItemDTO.builder()
                    .meetingDate(meeting.getMeetingDate())
                    .meetingTitle(meeting.getTitle())
                    .quorumPercentage(quorumPercentage)
                    .hasSigned(hasSigned)
                    .build();
            history.add(item);
        }

        // Trier par date décroissante
        // (simple tri à bulles pour éviter les streams)
        for (int i = 0; i < history.size() - 1; i++) {
            for (int j = 0; j < history.size() - i - 1; j++) {
                if (history.get(j).getMeetingDate().isBefore(history.get(j + 1).getMeetingDate())) {
                    CoOwnerMeetingHistoryItemDTO temp = history.get(j);
                    history.set(j, history.get(j + 1));
                    history.set(j + 1, temp);
                }
            }
        }

        // Pagination manuelle sur la liste triée
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, history.size());
        List<CoOwnerMeetingHistoryItemDTO> paginatedHistory = new ArrayList<>();
        if (startIndex < history.size()) {
            for (int i = startIndex; i < endIndex; i++) {
                paginatedHistory.add(history.get(i));
            }
        }

        // Construire et retourner la réponse
        return CoOwnerMeetingsDTO.builder()
                .participationRate(participationRate)
                .votedCount(presentOrProxyMeetings)
                .totalMeetingsCount(totalMeetings)
                .lastMeetingTitle(lastMeetingTitle)
                .meetingHistory(paginatedHistory)
                .build();
    }

    /**
     * Travaux d'un copropriétaire (onglet Travaux du détail)
     */
    @Override
    @Transactional(readOnly = true)
    public CoOwnerInterventionsResponseDTO getCoOwnerInterventions(Long coOwnerId) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Récupérer les interventions sur les appartements de ce copropriétaire
        List<InterventionRequest> interventions = interventionRequestRepository.findByPropertyOwnerAndSyndic(
                coOwner, currentSyndic);

        // Construire la liste de DTOs
        List<CoOwnerInterventionRowDTO> rows = new ArrayList<>();
        Integer activeCount = 0;

        for (InterventionRequest intervention : interventions) {
            // Calculer le statut composite
            String statusGroup = calculateInterventionStatusGroup(intervention.getStatus());

            // Compter les interventions actives (non résolues)
            if (!"RESOLU".equals(statusGroup)) {
                activeCount++;
            }

            // Nom du prestataire
            String providerName = "Non affecté";
            if (intervention.getSelectedProvider() != null) {
                providerName = intervention.getSelectedProvider().getFirstName() + " " 
                        + intervention.getSelectedProvider().getLastName();
            }

            // Catégorie (spécialité)
            String category = null;
            if (intervention.getSpecialty() != null) {
                category = intervention.getSpecialty().getName();
            }

            // Construire le DTO
            CoOwnerInterventionRowDTO row = CoOwnerInterventionRowDTO.builder()
                    .reference(intervention.getReference())
                    .category(category)
                    .apartmentReference(intervention.getProperty().getReference())
                    .date(intervention.getCreatedAt())
                    .status(statusGroup)
                    .providerName(providerName)
                    .build();
            rows.add(row);
        }

        // Construire et retourner la réponse
        return CoOwnerInterventionsResponseDTO.builder()
                .activeCount(activeCount)
                .interventions(rows)
                .build();
    }

    // =========================================================================
    // Documents d'un copropriétaire précis, toutes sources confondues (onglet Documents, côté syndic)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public CoOwnerDocumentUnifiedListResponseDTO getCoOwnerDocuments(Long coOwnerId, String search, String category,
                                                                     int page, int size) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Prépare la pagination
        Pageable pageable = PageRequest.of(page, size);

        // Nettoie le texte de recherche (retire les espaces inutiles, met null si vide)
        String searchFilter = null;
        if (search != null && !search.isBlank()) {
            searchFilter = search.trim();
        }

        // Nettoie le filtre de catégorie
        String categoryFilter = null;
        if (category != null && !category.isBlank()) {
            categoryFilter = category.trim();
        }

        // Récupère la page de résultats fusionnés (manuel + AG + charges exceptionnelles)
        Page<Object[]> resultPage = coOwnerDocumentUnifiedRepository.searchCoOwnerDocumentsUnified(
                coOwnerId, searchFilter, categoryFilter, pageable);

        List<CoOwnerDocumentUnifiedDTO> documents = new ArrayList<>();

        // Chaque ligne est un tableau brut de colonnes, il faut extraire chaque valeur une par une,
        // dans l'ordre exact du SELECT (source_type, source_id, title, file_name, file_url, file_size_kb, category, created_at)
        for (Object[] row : resultPage.getContent()) {

            String sourceType = (String) row[0];
            Long sourceId = ((Number) row[1]).longValue();
            String title = (String) row[2];
            String fileName = (String) row[3];
            String fileUrl = (String) row[4];
            Long fileSizeKb = row[5] != null ? ((Number) row[5]).longValue() : null;
            String cat = (String) row[6];

            // La date revient sous forme de Timestamp SQL, à convertir en LocalDateTime
            Timestamp createdAtTimestamp = (Timestamp) row[7];
            LocalDateTime createdAt = createdAtTimestamp != null ? createdAtTimestamp.toLocalDateTime() : null;

            // Extrait le format depuis le nom de fichier (ex: "PV.pdf" -> "PDF")
            String format = extractFormat(fileName);

            documents.add(CoOwnerDocumentUnifiedDTO.builder()
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .title(title)
                    .fileName(fileName)
                    .fileUrl(fileUrl)
                    .fileSizeKb(fileSizeKb)
                    .format(format)
                    .category(cat)
                    .createdAt(createdAt)
                    .build());
        }

        // Construit la réponse finale : documents de la page + infos de pagination
        return CoOwnerDocumentUnifiedListResponseDTO.builder()
                .totalCount(resultPage.getTotalElements())
                .documents(documents)
                .currentPage(resultPage.getNumber())
                .totalPages(resultPage.getTotalPages())
                .build();
    }


    /**
     * Convertir l'enum en label affichable
     */
    private String getCategoryLabel(CoOwnerDocumentCategory category) {
        return category.getDescription();
    }

    /**
     * Déduire le type de fichier depuis l'extension
     */
    private String getFileType(String fileName) {
        if (fileName == null) {
            return null;
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "pdf":
                return "PDF";
            case "jpg":
            case "jpeg":
                return "JPG";
            case "png":
                return "PNG";
            case "doc":
            case "docx":
                return "DOC";
            default:
                return extension.toUpperCase();
        }
    }

    /**
     * Activité récente d'un copropriétaire (panneau Activité Récente du détail)
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ActivityLogItemDTO> getCoOwnerActivityLog(Long coOwnerId, Integer page, Integer size) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Liste unifiée de tous les logs
        List<ActivityLog> allLogs = new ArrayList<>();

        // 1. Logs où le copropriétaire est l'acteur direct
        List<ActivityLog> actorLogs = activityLogRepository.findByActorIdOrderByCreatedAtDesc(coOwnerId);
        for (ActivityLog log : actorLogs) {
            allLogs.add(log);
        }

        // 2. Logs de type CHARGE_CALL_GENERATED pour les résidences du copropriétaire
        // Récupérer les résidences où ce copropriétaire a des lots chez le syndic
        List<Property> properties = propertyRepository.findByOwnerIdAndResidenceSyndicId(coOwnerId, currentSyndic.getId());
        List<Long> residenceIds = new ArrayList<>();
        for (Property property : properties) {
            if (!residenceIds.contains(property.getResidence().getId())) {
                residenceIds.add(property.getResidence().getId());
            }
        }

        if (!residenceIds.isEmpty()) {
            List<ActivityLog> chargeCallLogs = activityLogRepository
                    .findChargeCallGeneratedByResidenceIdsOrderByCreatedAtDesc(residenceIds);
            for (ActivityLog log : chargeCallLogs) {
                allLogs.add(log);
            }
        }

        // 3. Logs de type MEETING_DOCUMENT_ADDED pour les AG du copropriétaire
        // Récupérer les AG où ce copropriétaire était convoqué
        List<MeetingParticipant> participants = meetingParticipantRepository.findByUser(coOwner);
        List<Long> meetingIds = new ArrayList<>();
        for (MeetingParticipant participant : participants) {
            if (participant.getMeeting().getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
                if (!meetingIds.contains(participant.getMeeting().getId())) {
                    meetingIds.add(participant.getMeeting().getId());
                }
            }
        }

        if (!meetingIds.isEmpty()) {
            List<ActivityLog> meetingDocLogs = activityLogRepository
                    .findMeetingDocumentAddedByMeetingIdsOrderByCreatedAtDesc(meetingIds);
            for (ActivityLog log : meetingDocLogs) {
                allLogs.add(log);
            }
        }

        // Trier par date décroissante
        for (int i = 0; i < allLogs.size() - 1; i++) {
            for (int j = 0; j < allLogs.size() - i - 1; j++) {
                if (allLogs.get(j).getCreatedAt().isBefore(allLogs.get(j + 1).getCreatedAt())) {
                    ActivityLog temp = allLogs.get(j);
                    allLogs.set(j, allLogs.get(j + 1));
                    allLogs.set(j + 1, temp);
                }
            }
        }

        // Pagination manuelle
        int totalElements = allLogs.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<ActivityLog> paginatedLogs = new ArrayList<>();
        if (fromIndex < totalElements) {
            for (int i = fromIndex; i < toIndex; i++) {
                paginatedLogs.add(allLogs.get(i));
            }
        }

        // Mapper en DTOs
        List<ActivityLogItemDTO> dtos = new ArrayList<>();
        for (ActivityLog log : paginatedLogs) {
            String actorName = null;
            String actorPhotoUrl = null;
            if (log.getActor() != null) {
                actorName = log.getActor().getFirstName() + " " + log.getActor().getLastName();
                actorPhotoUrl = log.getActor().getProfilePhotoUrl();
            }

            ActivityLogItemDTO dto = ActivityLogItemDTO.builder()
                    .type(log.getType())
                    .message(log.getMessage())
                    .detail(log.getDetail())
                    .actorName(actorName)
                    .actorPhotoUrl(actorPhotoUrl)
                    .createdAt(log.getCreatedAt())
                    .build();
            dtos.add(dto);
        }

        return new PageImpl<>(dtos, PageRequest.of(page, size), totalElements);
    }

    //-------------------------------------------------------
    // Lier un copropriétaire existant au syndic connecté
    //-------------------------------------------------------
    @Override
    @Transactional
    public void linkCoOwner(Long coOwnerId) {

        User currentSyndic = getCurrentUser();

        // on vérifie que le copropriétaire existe bien en base
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // on vérifie que c'est bien un copropriétaire — pas un syndic ou un admin
        if (!coOwner.getRole().getName().equals(ERole.ROLE_COPROPRIETAIRE)) {
            throw new BadRequestException("Cet utilisateur n'est pas un copropriétaire");
        }

        // on vérifie que la relation n'existe pas déjà — évite les doublons
        boolean relationExiste = syndicCoOwnerRelationRepository
                .findBySyndicIdAndCoOwnerId(currentSyndic.getId(), coOwnerId)
                .isPresent();

        if (relationExiste) {
            throw new BadRequestException("Ce copropriétaire est déjà dans votre liste");
        }
        //* Sinon
        // on crée la relation entre le syndic et le copropriétaire existant
        SyndicOwnerRelation relation = new SyndicOwnerRelation();
        relation.setSyndic(currentSyndic); // le syndic connecté
        relation.setCoOwner(coOwner); // le copropriétaire existant
        syndicCoOwnerRelationRepository.save(relation);

        log.info("Copropriétaire {} lié au syndic {}", coOwner.getEmail(), currentSyndic.getEmail());
    }

    //------------------------------------------------------------------------------------------------------------------------
    // Ajouter un copropriétaire et optionnellement l'affecter à des biens dont  la résidence de chacun appartient au syndic connecté
    //------------------------------------------------------------------------------------------------------------------------
    @Override
    @Transactional
    public void addCoOwner(CreateCoOwnerDTO dto, MultipartFile photo) {

        // Vérifier qu'au moins un bien est fourni
        if (dto.getProperties() == null || dto.getProperties().isEmpty()) {
            throw new BadRequestException("Au moins un bien doit être assigné au copropriétaire");
        }

        User currentSyndic = getCurrentUser();

        // Vérifications préliminaires
        // on vérifie si l'email existe déjà — si oui on retourne l'ID du copropriétaire existant
        userRepository.findByEmail(dto.getEmail()).ifPresent(existing -> {
            throw new CoOwnerAlreadyExistsException(
                    "Un copropriétaire avec cet email existe déjà", // message clair
                    existing.getId() // ID retourné au frontend pour proposer le lien
            );
        });

       // on vérifie si le téléphone existe déjà — même logique
        userRepository.findByPhone(dto.getPhone()).ifPresent(existing -> {
            throw new CoOwnerAlreadyExistsException(
                    "Un copropriétaire avec ce téléphone existe déjà", // message clair
                    existing.getId() // ID retourné au frontend pour proposer le lien
            );
        });

        // Upload de la photo vers MinIO si fournie
        String photoUrl = null;
        if (photo != null && !photo.isEmpty()) {
            photoUrl = minioService.uploadFile(photo, "co-owners");
        }

        // Création du compte User
        Role role = roleRepository.findByName(ERole.ROLE_COPROPRIETAIRE)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle introuvable"));

        // Comme pour le syndic créé par l'admin : le compte est directement actif, avec un mot de
        // passe temporaire envoyé par email — pas de code d'activation à saisir dans l'app
        String temporaryPassword = PasswordGeneratorUtil.generateTemporaryPassword();

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setProfilePhotoUrl(photoUrl);

        User saved = userRepository.save(user);

        // Création du CoOwnerProfile avec les infos complémentaires
        CoOwnerProfile profile = new CoOwnerProfile();
        profile.setUser(saved);
        profile.setTitle(dto.getTitle());
        profile.setBirthDate(dto.getBirthDate());
        profile.setNationality(dto.getNationality());
        profile.setSecondaryPhone(dto.getSecondaryPhone());
        profile.setAddress(dto.getAddress());
        profile.setPhotoUrl(photoUrl);
        coOwnerProfileRepository.save(profile);

        // Créer la relation entre le syndic et le copropriétaire
        SyndicOwnerRelation relation = new SyndicOwnerRelation();
        relation.setSyndic(currentSyndic);
        relation.setCoOwner(saved);
        syndicCoOwnerRelationRepository.save(relation);

        /**
         * Affectation des biens si fournis
         * Pour chaque résidence → pour chaque lot sélectionné :
         * - Vérifier que le lot existe et appartient à la résidence
         * - Vérifier que le lot est VACANT (pas de owner)
         * - Affecter le copropriétaire + passer le statut à OCCUPIED
         */
        if (dto.getProperties() != null && !dto.getProperties().isEmpty()) {
            //Pour chaque affectation de propriété
            for (CoOwnerPropertyAssignmentDTO assignment : dto.getProperties()) {
                // Récupérer la résidence
                Residence residence = residenceRepository
                        .findById(assignment.getResidenceId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Résidence introuvable : " + assignment.getResidenceId()));

                // Vérifier que la résidence appartient au syndic connecté
                if (residence.getSyndic() == null || !residence.getSyndic().getId().equals(getCurrentUser().getId())) {
                    throw new ForbiddenException("Cette résidence ne vous appartient pas");
                }

                // Pour chaque lot sélectionné
                for (Long propertyId : assignment.getPropertyIds()) {
                    // Récupérer le lot
                    Property property = propertyRepository.findById(propertyId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Lot introuvable : " + propertyId));

                    // Vérifier que le lot appartient bien à la résidence
                    if (!property.getResidence().getId().equals(residence.getId())) {
                        throw new BadRequestException(
                                "Le lot " + property.getReference()
                                + " n'appartient pas à cette résidence");
                    }

                    // Vérifier que le lot est disponible
                    if (property.getOwner() != null) {
                        throw new BadRequestException(
                                "Le lot " + property.getReference()
                                + " est déjà occupé");
                    }

                    // Affecter le copropriétaire → statut automatiquement OCCUPIED
                    property.setOwner(saved);
                    property.setStatus(PropertyStatus.OCCUPIED);
                    property.setAssignedAt(LocalDateTime.now());
                    // Recalcule et persiste displayStatus (champ réellement lu par le listing des lots)
                    statusRecalculationService.recalculatePropertyDisplayStatus(property);
                }
            }
        }
                               
        // Envoi des identifiants de connexion par email (mot de passe temporaire, comme pour le syndic)
        emailService.sendCoOwnerAccountCreated(saved.getEmail(), temporaryPassword, saved.getFirstName());
        
        //Retourne l'email et le nombre de biens affectés en parcourant les assignements  et pour chaque assignement on retourne le nombre de biens affectés puis on somme sinon on met 0
        log.info("Copropriétaire créé : {} — {} bien(s) affecté(s)",
                saved.getEmail(),
                dto.getProperties() != null
                    ? dto.getProperties().stream()
                        .mapToInt(a -> a.getPropertyIds().size()).sum()
                    : 0);
    }

    //--------------------------------------------------------------------
    //Lister les biens vacants d'une résidence géré par le syndic connecté
    //--------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<PropertySummaryDTO> getAvailableProperties(Long residenceId, Integer page, Integer size) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Vérifier que la résidence appartient à ce syndic
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (residence.getSyndic() == null || !residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette résidence ne vous appartient pas");
        }

        // Retourne uniquement les biens VACANT de cette résidence avec pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<Property> propertyPage = propertyRepository
                .findByResidenceIdAndStatus(residenceId, PropertyStatus.VACANT, pageable);

        List<PropertySummaryDTO> dtos = propertyPage.getContent().stream()
                .map(this::mapToPropertySummaryDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, propertyPage.getTotalElements());
    }

    //-------------------------------------------------------
    //Lister les résidences géré par le Syndic qui ont au moins un bien Vaccant
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<ResidenceSummaryDTO> getResidencesWithVacantProperties(Integer page, Integer size) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer toutes les résidences qui ont des biens vacants géré par le syndic connecté
        List<Residence> allResidences = residenceRepository
                .findResidencesWithVacantProperties()
                .stream()
                // Filtrer pour ne garder que les résidences du syndic connecté
                .filter(r -> r.getSyndic() != null && r.getSyndic().getId().equals(currentSyndic.getId()))
                .toList();

        // Pagination manuelle
        int totalElements = allResidences.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Residence> pageContent = allResidences.subList(fromIndex, toIndex);

        List<ResidenceSummaryDTO> dtos = pageContent.stream()
                .map(this::mapToResidenceSummaryDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, PageRequest.of(page, size), totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerResidenceDTO> getCoOwnerResidences(Long coOwnerId, Integer page, Integer size) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Pagination au niveau de la base de données
        // La requête utilise DISTINCT pour éviter les doublons quand un copropriétaire a plusieurs lots dans la même résidence
        Page<Residence> residencesPage = propertyRepository.findDistinctResidencesByCoOwnerAndSyndic(
                coOwnerId,
                currentSyndic.getId(),
                PageRequest.of(page, size)
        );

        // Convertir les entités Residence en DTOs
        List<CoOwnerResidenceDTO> dtos = residencesPage.getContent().stream()
                .map(residence -> CoOwnerResidenceDTO.builder()
                        .id(residence.getId())
                        .name(residence.getName())
                        .build())
                .collect(Collectors.toList());

        // Retourner la page avec les métadonnées de pagination (total, nombre de pages, etc.)
        return new PageImpl<>(dtos, residencesPage.getPageable(), residencesPage.getTotalElements());
    }

    //-------------------------------------------------------
    //Lister les copropriétaires du syndic connecté (ayant au moins un bien)
    // Pagination SQL réelle (LIMIT/OFFSET géré par la base via Pageable) — aucun chargement complet
    // en mémoire, aucun état conservé entre deux appels (chaque requête HTTP est autonome, donc
    // deux appels concurrents sur des pages différentes ne s'interfèrent jamais).
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerListDTO> getCoOwners(String search, Long residenceId, Integer page, Integer size) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Page directement filtrée et paginée en base (search/residenceId en SQL, LIMIT/OFFSET via Pageable)
        Pageable pageable = PageRequest.of(page, size);
        Page<SyndicOwnerRelation> relationsPage = syndicCoOwnerRelationRepository
                .findCoOwnersWithPropertiesBySyndicId(currentSyndic.getId(), search, residenceId, pageable);

        List<User> coOwners = relationsPage.getContent().stream()
                .map(SyndicOwnerRelation::getCoOwner)
                .toList();

        if (coOwners.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> coOwnerIds = coOwners.stream().map(User::getId).toList();

        // Batch 1/2 : lots + résidences distinctes de TOUS les copropriétaires de cette page, en une requête
        Map<Long, long[]> apartmentsAndResidencesByCoOwner = propertyRepository
                .countApartmentsAndResidencesByCoOwnerIdsAndSyndic(coOwnerIds, currentSyndic.getId())
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> new long[]{(Long) row[1], (Long) row[2]}));

        // Batch 2/2 : solde de TOUS les copropriétaires de cette page, en une requête
        Map<Long, BigDecimal> soldeByCoOwner = chargeCallItemRepository
                .calculateSoldesByCoOwnerIdsAndSyndic(coOwnerIds, currentSyndic.getId())
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));

        List<CoOwnerListDTO> content = coOwners.stream()
                .map(user -> {
                    long[] counts = apartmentsAndResidencesByCoOwner.getOrDefault(user.getId(), new long[]{0L, 0L});
                    BigDecimal solde = soldeByCoOwner.getOrDefault(user.getId(), BigDecimal.ZERO);
                    return CoOwnerListDTO.builder()
                            .id(user.getId())
                            .fullName(user.getFirstName() + " " + user.getLastName())
                            .photoUrl(user.getProfilePhotoUrl())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .apartmentsCount((int) counts[0])
                            .residencesCount((int) counts[1])
                            .solde(solde)
                            .build();
                })
                .toList();

        return new PageImpl<>(content, pageable, relationsPage.getTotalElements());
    }

    //-------------------------------------------------------
    //Détail d'un copropriétaire (en-tête + KPIs)
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public CoOwnerDetailDTO getCoOwnerDetail(Long coOwnerId) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez ce syndic
        long apartmentsCount = propertyRepository
                .countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Calculer les métriques de base (réutilisées de la liste)
        long residencesCount = propertyRepository
                .countResidencesByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());

        // Card "Retard" : nombre de jours depuis l'échéance de la charge non soldée la plus ancienne
        // — null si aucune charge en retard (affiché "À jour" côté front dans ce cas)
        Integer delayDays = chargeCallItemRepository
                .findMaxDaysLateByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());

        // Card "Charges annuelles" (basé sur le budget de l'année en cours et les tantièmes)
        BigDecimal annualChargesAmount = BigDecimal.ZERO;
        int currentYear = Year.now().getValue();

        // Récupérer toutes les résidences où ce copropriétaire a des lots
        List<Property> allProperties = propertyRepository.findAllByOwnerId(coOwnerId);
        ArrayList<Long> residenceIds = new ArrayList<>();

        // Collecter les IDs de résidences uniques (restreint au syndic)
        for (Property p : allProperties) {
            if (p.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
                if (!residenceIds.contains(p.getResidence().getId())) {
                    residenceIds.add(p.getResidence().getId());
                }
            }
        }

        // Pour chaque résidence, calculer la part du copropriétaire
        for (Long residenceId : residenceIds) {
            // Récupérer le budget de l'année en cours
            var budgetOpt = budgetRepository.findByResidenceIdAndAnnee(residenceId, currentYear);
            if (budgetOpt.isEmpty()) {
                // Pas de budget pour cette année, ignorer cette résidence
                continue;
            }

            var budget = budgetOpt.get();

            BigDecimal partResidence;
            if (budget.getRepartitionMode() == RepartitionMode.CUSTOM) {
                // Mode CUSTOM : sommer les quoteParts des ChargeCallItem générés pour ce copropriétaire
                partResidence = chargeCallItemRepository.sumQuotePartGeneratedByCoOwnerAndResidenceAndYear(
                        coOwnerId, residenceId, currentYear);
            } else {
                // Mode OWNERSHIP_SHARES : lit la quote-part annuelle déjà figée à la création du
                // budget (ChargeAllocationUtil.distributeByLargestRemainder) — plus aucun recalcul ici
                partResidence = budgetCoOwnerAllocationRepository
                        .findByBudgetIdAndCoOwnerId(budget.getId(), coOwnerId)
                        .map(BudgetCoOwnerAllocation::getAnnualQuotePart)
                        .orElse(BigDecimal.ZERO);
            }

            annualChargesAmount = annualChargesAmount.add(partResidence);
        }

        // Card "Montant dû actuellement" (remplace l'ancien doublon Solde actuel / Impayés) —
        // une seule ligne : [currentAmountDue, currentPenaltyAmount]
        Object[] amountDueRow = chargeCallItemRepository
                .sumCurrentAmountDueByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId()).get(0);
        BigDecimal currentAmountDue = (BigDecimal) amountDueRow[0];
        BigDecimal currentPenaltyAmount = (BigDecimal) amountDueRow[1];

        // Card "Taux de paiement à échéance" — null si aucune charge n'a jamais été payée
        Object[] onTimeRow = chargeCallPaymentRepository
                .countOnTimePaymentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId()).get(0);
        long totalPaidCount = ((Number) onTimeRow[0]).longValue();
        Integer onTimePaymentRate = null;
        if (totalPaidCount > 0) {
            long onTimeCount = onTimeRow[1] != null ? ((Number) onTimeRow[1]).longValue() : 0L;
            onTimePaymentRate = (int) Math.round(onTimeCount * 100.0 / totalPaidCount);
        }

        // Récupérer le profil pour l'adresse
        var profileOpt = coOwnerProfileRepository.findByUserId(coOwnerId);
        String address = profileOpt.isPresent() ? profileOpt.get().getAddress() : null;

        // Récupérer la date de première acquisition
        var acquisitionDateOpt = propertyRepository
                .findFirstAcquisitionDateByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        LocalDateTime acquisitionDate = acquisitionDateOpt.orElse(null);

        // Construire le DTO
        return CoOwnerDetailDTO.builder()
                .fullName(coOwner.getFirstName() + " " + coOwner.getLastName())
                .photoUrl(coOwner.getProfilePhotoUrl())
                .residencesCount((int) residencesCount)
                .apartmentsCount((int) apartmentsCount)
                .lastName(coOwner.getLastName())
                .firstName(coOwner.getFirstName())
                .phone(coOwner.getPhone())
                .email(coOwner.getEmail())
                .address(address)
                .acquisitionDate(acquisitionDate)
                .annualChargesAmount(annualChargesAmount)
                .annualChargesYear(currentYear)
                .currentAmountDue(currentAmountDue)
                .currentPenaltyAmount(currentPenaltyAmount)
                .delayDays(delayDays)
                .onTimePaymentRate(onTimePaymentRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerPropertyItemDTO> getCoOwnerProperties(Long coOwnerId, Integer page, Integer size) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez ce syndic
        long apartmentsCount = propertyRepository
                .countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Récupère directement la page demandée des lots du copropriétaire, restreints à ce syndic —
        // LIMIT/OFFSET géré par la base, pas de chargement complet en mémoire
        int currentYear = Year.now().getValue();
        Pageable pageable = PageRequest.of(page, size);
        Page<Property> propertyPage = propertyRepository
                .findByOwnerIdAndResidenceSyndicId(coOwnerId, currentSyndic.getId(), pageable);

        Page<CoOwnerPropertyItemDTO> result = propertyPage.map(p -> {
            BigDecimal annualCharge = calculateAnnualChargeForProperty(p, currentYear);
            return CoOwnerPropertyItemDTO.builder()
                    .reference(p.getReference())
                    .bloc(p.getBloc())
                    .floor(p.getFloor())
                    .area(p.getArea())
                    .share(p.getShare())
                    .residenceName(p.getResidence().getName())
                    .annualCharge(annualCharge)
                    .build();
        });

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerPaymentItemDTO> getCoOwnerPayments(Long coOwnerId, PaymentStatus status, Long residenceId, Integer year, Integer page, Integer size) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Paginer les paiements — historique complet (toutes résidences/années) par défaut, filtres
        // status/residenceId/year tous optionnels, pas de valeur par défaut sur year (contrairement
        // aux autres endroits de l'app) puisque cet onglet EST l'historique complet
        Pageable pageable = PageRequest.of(page, size);
        Page<ChargeCallPayment> paymentPage = chargeCallPaymentRepository
                .findByCoOwnerAndSyndicWithFilters(coOwnerId, currentSyndic.getId(), status, residenceId, year, pageable);

        // Mapper en DTOs
        ArrayList<CoOwnerPaymentItemDTO> dtos = new ArrayList<>();
        for (ChargeCallPayment payment : paymentPage.getContent()) {
            LocalDateTime date = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
            String paymentMethod = payment.getMethod() != null ? payment.getMethod().name() : null;
            String statusStr = payment.getStatus().name();
            Boolean receiptAvailable = payment.getStatus().name().equals("COMPLETED");
            ChargeCall chargeCall = payment.getChargeCallItem().getChargeCall();

            CoOwnerPaymentItemDTO dto = CoOwnerPaymentItemDTO.builder()
                    .id(payment.getId())
                    .date(date)
                    .reference(payment.getReference())
                    .residenceName(chargeCall.getBudget().getResidence().getName())
                    .period(buildSimplePeriodeLabel(chargeCall))
                    .year(chargeCall.getYear())
                    .amount(payment.getAmount())
                    .paymentMethod(paymentMethod)
                    .status(statusStr)
                    .receiptAvailable(receiptAvailable)
                    .build();
            dtos.add(dto);
        }

        return new PageImpl<>(dtos, pageable, paymentPage.getTotalElements());
    }

    // Libellé court de la période d'un appel de charges, ex: "T3" (trimestriel) ou "Jan" (mensuel)
    private String buildSimplePeriodeLabel(ChargeCall chargeCall) {
        if (chargeCall.getFrequency() != null && chargeCall.getFrequency().name().equals("TRIMESTRIEL")) {
            return "T" + chargeCall.getPeriodNumber();
        }
        String[] mois = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        int index = chargeCall.getPeriodNumber() - 1;
        return (index >= 0 && index < mois.length) ? mois[index] : "P" + chargeCall.getPeriodNumber();
    }

    // ============================================================
    // REÇU D'UN PAIEMENT (bouton "Reçu" sur une ligne de l'historique des paiements)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public CoOwnerPaymentReceiptDTO getCoOwnerPaymentReceipt(Long coOwnerId, Long paymentId) {

        User currentSyndic = getCurrentUser();

        ChargeCallPayment payment = chargeCallPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));

        ChargeCallItem item = payment.getChargeCallItem();
        if (!item.getCoOwner().getId().equals(coOwnerId)) {
            throw new ForbiddenException("Ce paiement n'appartient pas à ce copropriétaire");
        }
        if (!item.getChargeCall().getBudget().getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce paiement");
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Ce paiement n'est pas complété, aucun reçu disponible");
        }

        ChargeCall chargeCall = item.getChargeCall();

        return CoOwnerPaymentReceiptDTO.builder()
                .receiptReference(payment.getReference())
                .coOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName())
                .residenceName(chargeCall.getBudget().getResidence().getName())
                .period(buildSimplePeriodeLabel(chargeCall))
                .year(chargeCall.getYear())
                .paymentDate(payment.getPaidAt())
                .paymentMethod(payment.getMethod() != null ? payment.getMethod().name() : null)
                .amountPaid(payment.getAmount())
                .build();
    }

    // ============================================================
    // EXPORT PDF DE L'HISTORIQUE DES PAIEMENTS (mêmes filtres que getCoOwnerPayments, sans pagination)
    // ============================================================

    private static final String[] CO_OWNER_PAYMENTS_EXPORT_HEADERS =
            {"Date", "Référence", "Résidence", "Période", "Montant", "Moyen de paiement", "Statut"};
    private static final String CO_OWNER_PAYMENTS_HEADER_COLOR = "C8E6C9";

    @Override
    @Transactional(readOnly = true)
    public PdfFileDTO exportCoOwnerPayments(Long coOwnerId, PaymentStatus status, Long residenceId, Integer year) {

        User currentSyndic = getCurrentUser();

        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Toutes les lignes filtrées, sans pagination, même tri que la liste paginée
        List<ChargeCallPayment> payments = chargeCallPaymentRepository
                .findByCoOwnerAndSyndicWithFilters(coOwnerId, currentSyndic.getId(), status, residenceId, year, Pageable.unpaged())
                .getContent();

        List<Object[]> rows = payments.stream()
                .map(payment -> {
                    ChargeCall chargeCall = payment.getChargeCallItem().getChargeCall();
                    LocalDateTime date = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
                    return new Object[]{
                            date,
                            payment.getReference(),
                            chargeCall.getBudget().getResidence().getName(),
                            buildSimplePeriodeLabel(chargeCall) + " " + chargeCall.getYear(),
                            payment.getAmount(),
                            paymentMethodLabel(payment.getMethod()),
                            paymentStatusLabel(payment.getStatus())
                    };
                })
                .toList();

        Residence residence = residenceId != null ? residenceRepository.findById(residenceId).orElse(null) : null;
        String coOwnerName = coOwner.getFirstName() + " " + coOwner.getLastName();

        byte[] content = PdfExportUtil.generate(
                coOwnerName,
                "Historique des paiements",
                buildFiltersLine(status, residence, year),
                CO_OWNER_PAYMENTS_EXPORT_HEADERS, rows, CO_OWNER_PAYMENTS_HEADER_COLOR, Set.of(4));

        String fileName = "historique-paiements_" + slugify(coOwnerName) + "_"
                + residenceSegment(residence) + "_" + yearSegment(year) + ".pdf";

        return new PdfFileDTO(fileName, content);
    }

    // Libellé FR affiché dans le PDF (le JSON de /payments renvoie l'enum brut, traduit côté front —
    // mais un PDF est un document final, il doit contenir directement le libellé lisible)
    private String paymentStatusLabel(PaymentStatus status) {
        if (status == null) return "";
        return switch (status) {
            case COMPLETED -> "Payé";
            case PENDING -> "En attente";
            case FAILED -> "Échoué";
        };
    }

    private String paymentMethodLabel(ChargePaymentMethod method) {
        if (method == null) return "";
        return switch (method) {
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            case CARTE_BANCAIRE -> "Carte bancaire";
        };
    }

    // Ligne d'info affichée sous le titre du PDF, résumant les filtres actifs — absente si aucun filtre
    private String buildFiltersLine(PaymentStatus status, Residence residence, Integer year) {
        List<String> parts = new ArrayList<>();
        if (status != null) parts.add("Statut : " + paymentStatusLabel(status));
        if (residence != null) parts.add("Résidence : " + residence.getName());
        if (year != null) parts.add("Année : " + year);
        return parts.isEmpty() ? null : "Filtres appliqués — " + String.join(" · ", parts);
    }

    private String residenceSegment(Residence residence) {
        return residence != null ? slugify(residence.getName()) : "toutes-residences";
    }

    private String yearSegment(Integer year) {
        return year != null ? year.toString() : "toutes-annees";
    }

    // Normalise un texte pour un nom de fichier : sans accents, sans espaces ni caractères spéciaux
    private String slugify(String input) {
        String withoutAccents = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Override
    @Transactional(readOnly = true)
    public CoOwnerFinancesDTO getCoOwnerFinances(Long coOwnerId, Long residenceId) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer le copropriétaire
        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette résidence ne vous appartient pas");
        }

        // Vérifier que le copropriétaire a au moins un lot dans cette résidence
        boolean hasLot = propertyRepository.existsByOwnerIdAndResidenceId(coOwnerId, residenceId);
        if (!hasLot) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans cette résidence");
        }

        int currentYear = Year.now().getValue();

        var budgetOpt = budgetRepository.findByResidenceIdAndAnnee(residenceId, currentYear);
        boolean budgetExists = budgetOpt.isPresent();

        // 1-5. Les 4 cards KPI — null si aucun budget n'existe pour l'année en cours (le front
        // affiche "Budget {year} non généré" plutôt qu'un chiffre trompeur)
        BigDecimal monthlyChargeAmount = null;
        BigDecimal quarterlyChargeAmount = null;
        BigDecimal remainingAmount = null;
        BigDecimal remainingPenaltyAmount = null;
        Integer settlementRate = null;
        Integer paidCallsCount = null;
        Integer totalCallsCount = null;

        if (budgetExists) {
            var budget = budgetOpt.get();

            // Charges annuelles de l'année en cours (calcul interne, pas exposé tel quel — seuls
            // les montants mensuel/trimestriel dérivés sont renvoyés)
            BigDecimal annualCharges;
            if (budget.getRepartitionMode() == RepartitionMode.CUSTOM) {
                // Mode CUSTOM : sommer les quoteParts des ChargeCallItem générés pour ce copropriétaire
                annualCharges = chargeCallItemRepository.sumQuotePartGeneratedByCoOwnerAndResidenceAndYear(
                        coOwnerId, residenceId, currentYear);
            } else {
                // Mode OWNERSHIP_SHARES : lit la quote-part annuelle déjà figée à la création du
                // budget (ChargeAllocationUtil.distributeByLargestRemainder) — plus aucun recalcul ici
                annualCharges = budgetCoOwnerAllocationRepository
                        .findByBudgetIdAndCoOwnerId(budget.getId(), coOwnerId)
                        .map(BudgetCoOwnerAllocation::getAnnualQuotePart)
                        .orElse(BigDecimal.ZERO);
            }

            monthlyChargeAmount = annualCharges.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            quarterlyChargeAmount = annualCharges.divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP);

            // Montant restant : charges non soldées de l'année, pénalité incluse
            Object[] remainingRow = chargeCallItemRepository
                    .sumRemainingAmountByCoOwnerAndResidenceAndYear(coOwnerId, residenceId, currentYear).get(0);
            remainingAmount = (BigDecimal) remainingRow[0];
            remainingPenaltyAmount = (BigDecimal) remainingRow[1];

            // Taux de règlement : appels soldés / appels émis cette année (NO_AMOUNT_DUE exclu des
            // deux côtés — voir countCallsByCoOwnerAndResidenceAndYear). totalCallsCount grandit à
            // chaque nouvel appel généré (T1 puis T2...), ce taux n'est jamais une projection annuelle.
            Object[] callsRow = chargeCallItemRepository
                    .countCallsByCoOwnerAndResidenceAndYear(coOwnerId, residenceId, currentYear).get(0);
            long total = ((Number) callsRow[0]).longValue();
            long paid = callsRow[1] != null ? ((Number) callsRow[1]).longValue() : 0L;
            totalCallsCount = (int) total;
            paidCallsCount = (int) paid;
            if (total > 0) {
                settlementRate = (int) Math.round(paid * 100.0 / total);
            }
        }

        // 7. Historique des paiements mensuels
        ArrayList<MonthlyPaymentDTO> monthlyPayments = new ArrayList<>();
        List<Object[]> paymentData = chargeCallPaymentRepository
                .sumCompletedPaymentsByMonthForCoOwner(coOwnerId, residenceId, currentYear);
        for (int month = 1; month <= 12; month++) {
            BigDecimal monthAmount = BigDecimal.ZERO;
            for (Object[] row : paymentData) {
                Integer rowMonth = (Integer) row[0];
                if (rowMonth.equals(month)) {
                    monthAmount = (BigDecimal) row[1];
                    break;
                }
            }
            MonthlyPaymentDTO dto = MonthlyPaymentDTO.builder()
                    .month(month)
                    .amount(monthAmount)
                    .build();
            monthlyPayments.add(dto);
        }

        // 8. Tableau des appels de charges
        ArrayList<ChargeCallRowDTO> chargeCalls = new ArrayList<>();
        List<ChargeCall> chargeCallList = chargeCallRepository.findByResidenceIdAndYear(residenceId, currentYear);
        for (ChargeCall cc : chargeCallList) {
            // Trouver le ChargeCallItem pour ce copropriétaire
            ChargeCallItem item = null;
            for (ChargeCallItem cci : cc.getItems()) {
                if (cci.getCoOwner().getId().equals(coOwnerId)) {
                    item = cci;
                    break;
                }
            }
            if (item != null) {
                String status;
                if (item.getStatus().name().equals("PAID")) {
                    status = "PAYE";
                } else if (java.time.LocalDate.now().isBefore(cc.getDueDate())) {
                    status = "A_VENIR";
                } else {
                    status = "EN_RETARD";
                }
                ChargeCallRowDTO dto = ChargeCallRowDTO.builder()
                        .reference(item.getReference())
                        .date(cc.getSentDate().atStartOfDay())
                        .amount(item.getQuotePart())
                        .status(status)
                        .build();
                chargeCalls.add(dto);
            }
        }

        return CoOwnerFinancesDTO.builder()
                .year(currentYear)
                .budgetExists(budgetExists)
                .monthlyChargeAmount(monthlyChargeAmount)
                .quarterlyChargeAmount(quarterlyChargeAmount)
                .remainingAmount(remainingAmount)
                .remainingPenaltyAmount(remainingPenaltyAmount)
                .settlementRate(settlementRate)
                .paidCallsCount(paidCallsCount)
                .totalCallsCount(totalCallsCount)
                .monthlyPayments(monthlyPayments)
                .chargeCalls(chargeCalls)
                .build();
    }

    @Override
    @Transactional
    public void updateCoOwner(Long coOwnerId, String firstName, String lastName, String email, String phone,
                              Title title , LocalDate birthDate, Nationality nationality,
                              String secondaryPhone, String address) {
        User currentSyndic = getCurrentUser();

        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Mise à jour partielle des champs User
        if (firstName != null) {
            coOwner.setFirstName(firstName);
        }
        if (lastName != null) {
            coOwner.setLastName(lastName);
        }
        if (email != null) {
            coOwner.setEmail(email);
        }
        if (phone != null) {
            coOwner.setPhone(phone);
        }

        // Mise à jour partielle des champs CoOwnerProfile
        CoOwnerProfile profile = coOwnerProfileRepository.findByUserId(coOwnerId)
                .orElse(null);

        if (profile != null) {
            if (title != null) {
                profile.setTitle(title);
            }
            if (birthDate != null) {
                profile.setBirthDate(birthDate);
            }
            if (nationality != null) {
                profile.setNationality(nationality);
            }
            if (secondaryPhone != null) {
                profile.setSecondaryPhone(secondaryPhone);
            }
            if (address != null) {
                profile.setAddress(address);
            }
            coOwnerProfileRepository.save(profile);
        }

        userRepository.save(coOwner);
    }

    @Override
    @Transactional
    public void deleteCoOwner(Long coOwnerId) {
        User currentSyndic = getCurrentUser();

        User coOwner = userRepository.findById(coOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que le copropriétaire a au moins un lot chez le syndic
        long apartmentsCount = propertyRepository.countApartmentsByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        if (apartmentsCount == 0) {
            throw new ForbiddenException("Ce copropriétaire n'a pas de lot dans vos résidences");
        }

        // Libérer tous les lots du copropriétaire (mettre owner à null et status à VACANT)
        List<Property> properties = propertyRepository.findByOwnerIdAndResidenceSyndicId(coOwnerId, currentSyndic.getId());
        for (Property property : properties) {
            property.setOwner(null);
            property.setStatus(PropertyStatus.VACANT);
            // Recalcule et persiste displayStatus (champ réellement lu par le listing des lots)
            statusRecalculationService.recalculatePropertyDisplayStatus(property);
        }

        // Supprimer le profil du copropriétaire
        CoOwnerProfile profile = coOwnerProfileRepository.findByUserId(coOwnerId)
                .orElse(null);
        if (profile != null) {
            coOwnerProfileRepository.delete(profile);
        }

        // Supprimer uniquement la relation avec CE syndic (le copropriétaire peut être lié à
        // d'autres syndics via linkCoOwner — on ne touche pas à leurs relations)
        List<SyndicOwnerRelation> relations = syndicCoOwnerRelationRepository.findAllBySyndicId(currentSyndic.getId(), Pageable.unpaged()).getContent();
        relations.stream()
                .filter(r -> r.getCoOwner().getId().equals(coOwnerId))
                .forEach(syndicCoOwnerRelationRepository::delete);

        // Ne supprimer le compte User que s'il n'est plus lié à aucun autre syndic et ne possède
        // plus aucun lot ailleurs — sinon on se contente d'avoir libéré ses lots et son lien avec ce syndic
        boolean stillLinkedElsewhere = syndicCoOwnerRelationRepository.countByCoOwnerId(coOwnerId) > 0;
        boolean stillOwnsPropertiesElsewhere = propertyRepository.countByOwnerId(coOwnerId) > 0;
        if (!stillLinkedElsewhere && !stillOwnsPropertiesElsewhere) {
            userRepository.delete(coOwner);
        }
    }

    //------------------------------------------
    //Les méthodes utilitaires
    //------------------------------------------
    private PropertySummaryDTO mapToPropertySummaryDTO(Property property) {
        return PropertySummaryDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .build();
    }

    private ResidenceSummaryDTO mapToResidenceSummaryDTO(Residence residence) {
        return ResidenceSummaryDTO.builder()
                .id(residence.getId())
                .name(residence.getName())
                .build();
    }


    // Extrait l'extension d'un nom de fichier en majuscules (ex: "PV.pdf" -> "PDF")
    // Retourne null si le nom de fichier est vide ou n'a pas d'extension
    private String extractFormat(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        return extension.toUpperCase();
    }


    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    // Calculer la charge annuelle pour un seul lot
    private BigDecimal calculateAnnualChargeForProperty(Property property, int currentYear) {
        var budgetOpt = budgetRepository.findByResidenceIdAndAnnee(property.getResidence().getId(), currentYear);
        if (budgetOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var budget = budgetOpt.get();

        if (property.getOwner() == null) {
            return BigDecimal.ZERO;
        }

        if (budget.getRepartitionMode() == RepartitionMode.CUSTOM) {
            // Mode CUSTOM : sommer les quoteParts des ChargeCallItem générés pour le propriétaire de ce lot
            return chargeCallItemRepository.sumQuotePartGeneratedByCoOwnerAndResidenceAndYear(
                    property.getOwner().getId(), property.getResidence().getId(), currentYear);
        }

        // Mode OWNERSHIP_SHARES : lit la quote-part annuelle du copropriétaire déjà figée à la
        // création du budget (ChargeAllocationUtil.distributeByLargestRemainder), puis la répartit
        // au prorata des lots de ce copropriétaire dans la résidence — plus aucun recalcul depuis budgetTotal
        BudgetCoOwnerAllocation allocation = budgetCoOwnerAllocationRepository
                .findByBudgetIdAndCoOwnerId(budget.getId(), property.getOwner().getId())
                .orElse(null);
        if (allocation == null || property.getShare() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalTantiemeOwner = propertyRepository
                .findByOwnerIdAndResidenceId(property.getOwner().getId(), property.getResidence().getId())
                .stream()
                .map(Property::getShare)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalTantiemeOwner.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return allocation.getAnnualQuotePart()
                .multiply(property.getShare())
                .divide(totalTantiemeOwner, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le statut composite d'une intervention (EN_ATTENTE, EN_COURS, RESOLU)
     * Réutilisé par le Kanban Travaux et l'onglet Travaux du détail copropriétaire
     */
    private String calculateInterventionStatusGroup(InterventionStatus status) {
        if (status == InterventionStatus.PENDING 
            || status == InterventionStatus.SYNDIC_ASSIGNED 
            || status == InterventionStatus.QUOTE_VALIDATED) {
            return "EN_ATTENTE";
        }
        if (status == InterventionStatus.STARTED) {
            return "EN_COURS";
        }
        if (status == InterventionStatus.FINISHED || status == InterventionStatus.FINAL_VALIDATION) {
            return "RESOLU";
        }
        // CANCELLED ne devrait pas arriver ici (exclu des requêtes)
        return null;
    }


}
