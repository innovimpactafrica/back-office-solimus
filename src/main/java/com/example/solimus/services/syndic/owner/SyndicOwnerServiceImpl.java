package com.example.solimus.services.syndic.owner;

import com.example.solimus.dtos.owner.CoOwnerDocumentItemDTO;
import com.example.solimus.dtos.owner.CoOwnerInterventionRowDTO;
import com.example.solimus.dtos.owner.CoOwnerInterventionsResponseDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingsDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingHistoryItemDTO;
import com.example.solimus.dtos.syndic.owner.*;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.CoOwnerAlreadyExistsException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;

import com.example.solimus.services.auth.ActivationCodeService;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
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
    private final ActivationCodeService activationCodeService;
    private final EmailService emailService;
    private final MinioService minioService;
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


    //----------------------------------------------------------------------
    // Autocomplete — recherche un copropriétaire par nom, email ou téléphone
    //-----------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public List<CoOwnerSearchResultDTO> searchCoOwners(String q) {

        // on limite à 5 résultats — suffisant pour un autocomplete
        Pageable limit = PageRequest.of(0,5);

        // on lance la recherche en base sur prénom, nom, email et téléphone
        return userRepository.searchCoOwners(q, limit)
                .stream()
                .map (user -> {

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
    }

    /**
     * Assemblées Générales d'un copropriétaire (onglet AG du détail)
     */
    @Override
    @Transactional(readOnly = true)
    public CoOwnerMeetingsDTO getCoOwnerMeetings(Long coOwnerId) {
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
                syndicParticipants.add(participant);
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
                    if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT 
                        || presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
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

            java.math.BigDecimal sumTantiemePresentOrRepresented = java.math.BigDecimal.ZERO;
            for (MeetingPresence presence : allPresences) {
                if (presence.getAttendanceStatus() == AttendanceStatus.PRESENT 
                    || presence.getAttendanceStatus() == AttendanceStatus.REPRESENTE) {
                    if (presence.getTantiemeSnapshot() != null) {
                        sumTantiemePresentOrRepresented = sumTantiemePresentOrRepresented.add(presence.getTantiemeSnapshot());
                    }
                }
            }

            java.math.BigDecimal sumTantiemeTotal = java.math.BigDecimal.ZERO;
            for (MeetingPresence presence : allPresences) {
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

            // Trouver la présence de ce copropriétaire
            AttendanceStatus attendanceStatus = null;
            for (MeetingPresence presence : allPresences) {
                if (presence.getMeetingParticipant().getId().equals(participant.getId())) {
                    attendanceStatus = presence.getAttendanceStatus();
                    break;
                }
            }

            // Construire le DTO
            CoOwnerMeetingHistoryItemDTO item = CoOwnerMeetingHistoryItemDTO.builder()
                    .meetingDate(meeting.getMeetingDate())
                    .meetingTitle(meeting.getTitle())
                    .quorumPercentage(quorumPercentage)
                    .vote(null) // null pour l'instant, dépend de Vote
                    .attendanceStatus(attendanceStatus)
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

        // Construire et retourner la réponse
        return CoOwnerMeetingsDTO.builder()
                .participationRate(participationRate)
                .votedCount(0) // 0 pour l'instant, sera rempli quand Vote existera
                .totalMeetingsCount(totalMeetings)
                .lastMeetingTitle(lastMeetingTitle)
                .lastMeetingVote(null) // null pour l'instant, dépend de Vote
                .meetingHistory(history)
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

    /**
     * Documents d'un copropriétaire (onglet Documents du détail)
     */
    @Override
    @Transactional(readOnly = true)
    public List<CoOwnerDocumentItemDTO> getCoOwnerDocuments(Long coOwnerId, String category) {
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

        // Liste unifiée de tous les documents
        List<CoOwnerDocumentItemDTO> allDocuments = new ArrayList<>();

        // 1. Documents uploadés manuellement (CoOwnerDocument)
        if (category == null || category.equals("TITRE_PROPRIETE") 
            || category.equals("CONTRAT") || category.equals("PIECE_IDENTITE")) {
            
            List<CoOwnerDocument> coOwnerDocs;
            if (category == null) {
                coOwnerDocs = coOwnerDocumentRepository.findByCoOwnerId(coOwnerId);
            } else {
                CoOwnerDocumentCategory docCategory = CoOwnerDocumentCategory.valueOf(category);
                coOwnerDocs = coOwnerDocumentRepository.findByCoOwnerIdAndCategory(coOwnerId, docCategory);
            }

            for (CoOwnerDocument doc : coOwnerDocs) {
                String categoryLabel = getCategoryLabel(doc.getCategory());
                CoOwnerDocumentItemDTO item = CoOwnerDocumentItemDTO.builder()
                        .title(doc.getTitle())
                        .category(categoryLabel)
                        .date(doc.getCreatedAt())
                        .fileSizeKb(doc.getFileSizeKb())
                        .fileType(doc.getFileType())
                        .fileUrl(doc.getFileName())
                        .build();
                allDocuments.add(item);
            }
        }

        // 2. PV d'assemblée (MeetingDocument)
        if (category == null || category.equals("PV_ASSEMBLEE")) {
            // Récupérer les AG où ce copropriétaire était convoqué
            List<MeetingParticipant> participants = meetingParticipantRepository.findByUser(coOwner);
            for (MeetingParticipant participant : participants) {
                Meeting meeting = participant.getMeeting();
                // Vérifier que l'AG appartient au syndic
                if (meeting.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
                    // Récupérer les documents de cette AG
                    List<MeetingDocument> meetingDocs = meetingDocumentRepository.findByMeetingId(meeting.getId());
                    for (MeetingDocument meetingDoc : meetingDocs) {
                        String title = "PV — " + meeting.getTitle();
                        CoOwnerDocumentItemDTO item = CoOwnerDocumentItemDTO.builder()
                                .title(title)
                                .category("PV d'assemblée")
                                .date(meetingDoc.getCreatedAt())
                                .fileSizeKb(meetingDoc.getFileSizeKb())
                                .fileType(meetingDoc.getDocumentType() != null ? meetingDoc.getDocumentType().name() : null)
                                .fileUrl(meetingDoc.getFileName())
                                .build();
                        allDocuments.add(item);
                    }
                }
            }
        }

        // 3. Reçus de paiement — liste vide pour l'instant (pas de source disponible)
        // Rien à ajouter

        // Trier par date décroissante
        for (int i = 0; i < allDocuments.size() - 1; i++) {
            for (int j = 0; j < allDocuments.size() - i - 1; j++) {
                if (allDocuments.get(j).getDate().isBefore(allDocuments.get(j + 1).getDate())) {
                    CoOwnerDocumentItemDTO temp = allDocuments.get(j);
                    allDocuments.set(j, allDocuments.get(j + 1));
                    allDocuments.set(j + 1, temp);
                }
            }
        }

        return allDocuments;
    }

    /**
     * Ajouter un document à un copropriétaire
     */
    @Override
    @Transactional
    public CoOwnerDocumentItemDTO addDocument(Long coOwnerId, CoOwnerDocumentCategory category, 
            String title, MultipartFile file) {
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

        // Upload vers MinIO
        String fileUrl;
        Long fileSizeKb;
        try {
            fileUrl = minioService.uploadFile(file, "co-owner-documents");
            fileSizeKb = file.getSize() / 1024;
        } catch (Exception e) {
            throw new BadRequestException("Erreur lors de l'upload du fichier");
        }

        // Déduire le type de fichier depuis l'extension
        String fileType = getFileType(file.getOriginalFilename());

        // Créer et sauvegarder le document
        CoOwnerDocument document = new CoOwnerDocument();
        document.setCoOwner(coOwner);
        document.setCategory(category);
        document.setTitle(title);
        document.setFileName(file.getOriginalFilename());
        document.setFileUrl(fileUrl);
        document.setFileSizeKb(fileSizeKb);
        document.setFileType(fileType);
        coOwnerDocumentRepository.save(document);

        // Retourner le DTO
        String categoryLabel = getCategoryLabel(category);
        return CoOwnerDocumentItemDTO.builder()
                .title(title)
                .category(categoryLabel)
                .date(document.getCreatedAt())
                .fileSizeKb(fileSizeKb)
                .fileType(fileType)
                .fileUrl(fileUrl)
                .build();
    }

    /**
     * Convertir l'enum en label affichable
     */
    private String getCategoryLabel(CoOwnerDocumentCategory category) {
        switch (category) {
            case TITRE_PROPRIETE:
                return "Titre de propriété";
            case CONTRAT:
                return "Contrats";
            case PIECE_IDENTITE:
                return "Pièces d'identité";
            default:
                return category.name();
        }
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

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);
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
        relation.setSyndic(getCurrentUser());
        relation.setCoOwner(saved);
        syndicCoOwnerRelationRepository.save(relation);

        /**
         * Affectation des biens si fournis
         * Pour chaque résidence → pour chaque lot sélectionné :
         * - Vérifier que le lot existe et appartient à la résidence
         * - Vérifier que le lot est VACANT (pas de owner)
         * - Affecter le copropriétaire + passer le statut à OCCUPE
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

                    // Affecter le copropriétaire → statut automatiquement OCCUPE
                    property.setOwner(saved);
                    property.setStatus(PropertyStatus.OCCUPE);
                    property.setAssignedAt(LocalDateTime.now());
                    propertyRepository.save(property);
                }
            }
        }
                               
        // Envoi du code d'activation par email
        String code = activationCodeService.generateAndStoreCodeMobile(saved);
        emailService.sendActivationCode(saved.getEmail(), code, saved.getFirstName());
        
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
    public List<PropertySummaryDTO> getAvailableProperties(Long residenceId) {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Vérifier que la résidence appartient à ce syndic
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (residence.getSyndic() == null || !residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette résidence ne vous appartient pas");
        }

        // Retourne uniquement les biens VACANT de cette résidence
        return propertyRepository
                .findByResidenceIdAndStatus(residenceId, PropertyStatus.VACANT)
                .stream()
                .map(this::mapToPropertySummaryDTO)
                .collect(Collectors.toList());
    }

    //-------------------------------------------------------
    //Lister les résidences géré par le Syndic qui ont au moins un bien Vaccant
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public List<ResidenceSummaryDTO> getResidencesWithVacantProperties() {
        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupérer toutes les résidences qui ont des biens vacants géré par le syndic connecté
        return residenceRepository
                .findResidencesWithVacantProperties()
                .stream()
                // Filtrer pour ne garder que les résidences du syndic connecté
                .filter(r -> r.getSyndic() != null && r.getSyndic().getId().equals(currentSyndic.getId()))
                .map(this::mapToResidenceSummaryDTO)
                .collect(Collectors.toList());
    }

    //-------------------------------------------------------
    //Lister les copropriétaires du syndic connecté (ayant au moins un bien)
    // Avec filtres search, residenceId, status et pagination manuelle
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerListDTO> getCoOwners(String search, Long residenceId, String status, Integer page, Integer size) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Étape 1 : récupérer TOUS les copropriétaires candidats du syndic,
        // filtrés par search et residenceId (vraies colonnes SQL, filtrables directement),
        // SANS pagination SQL ici — le filtre status vient après, en mémoire.
        //  Cette approche charge tous les copropriétaires avant de paginer.
          List<SyndicOwnerRelation> allRelations = syndicCoOwnerRelationRepository
                .findCoOwnersWithPropertiesBySyndicId(currentSyndic.getId(), search, residenceId);

        // Étape 2 : construire le DTO complet de chaque copropriétaire (avec status et solde calculés)
        List<CoOwnerListDTO> allDtos = allRelations.stream()
                .map(relation -> mapToCoOwnerListDTO(relation.getCoOwner(), currentSyndic))
                .collect(Collectors.toList());

        // Étape 3 : filtrer par status si demandé — sur la liste complète, pas sur une page
        List<CoOwnerListDTO> filteredDtos = allDtos.stream()
                .filter(dto -> status == null || status.isEmpty() || dto.getStatus().equals(status))
                .collect(Collectors.toList());

        // Étape 4 : pagination manuelle, APRÈS le filtre status
        int totalElements = filteredDtos.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<CoOwnerListDTO> pageContent = filteredDtos.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), totalElements);
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
        // Récupère le nombre de jours de retard le plus important parmi tous ses items impayés
        Integer maxDaysLate = chargeCallItemRepository
                .findMaxDaysLateByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());

       // Applique la même logique de statut que le reste de l'application (Paiements/Impayés)
        String status;
        if (maxDaysLate == null || maxDaysLate <= 0) {
            status = "A_JOUR";
        } else if (maxDaysLate <= 30) {
            status = "RETARD";
        } else {
            status = "CRITIQUE";
        }

        // Calculer annualCharges (basé sur le budget annuel et tantièmes)
        BigDecimal annualCharges = BigDecimal.ZERO;
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

            // Calculer le tantième total du copropriétaire dans cette résidence
            BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
            for (Property p : allProperties) {
                if (p.getResidence().getId().equals(residenceId)) {
                    tantiemeCoOwner = tantiemeCoOwner.add(p.getTantieme());
                }
            }

            // Calculer la part : budgetTotal * (tantiemeCoOwner / 100)
            BigDecimal partResidence = budget.getBudgetTotal()
                    .multiply(tantiemeCoOwner)
                    .divide(BigDecimal.valueOf(100));

            annualCharges = annualCharges.add(partResidence);
        }

        // Calculer les autres KPIs
        BigDecimal currentBalance = chargeCallItemRepository
                .calculateSoldeByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        BigDecimal paymentsMade = chargeCallItemRepository
                .sumPaymentsMadeByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());
        BigDecimal unpaidAmount = chargeCallItemRepository
                .sumUnpaidAmountByCoOwnerAndSyndic(coOwnerId, currentSyndic.getId());

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
                .status(status)
                .lastName(coOwner.getLastName())
                .firstName(coOwner.getFirstName())
                .phone(coOwner.getPhone())
                .email(coOwner.getEmail())
                .address(address)
                .acquisitionDate(acquisitionDate)
                .annualCharges(annualCharges)
                .currentBalance(currentBalance)
                .paymentsMade(paymentsMade)
                .unpaidAmount(unpaidAmount)
                .build();
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

    private CoOwnerListDTO mapToCoOwnerListDTO(User user, User currentSyndic) {

        // Calculer le nombre d'appartements (lots) restreint aux résidences du syndic
        long apartmentsCount = propertyRepository
                .countApartmentsByCoOwnerAndSyndic(user.getId(), currentSyndic.getId());

        // Calculer le nombre de résidences distinctes restreint au syndic
        long residencesCount = propertyRepository
                .countResidencesByCoOwnerAndSyndic(user.getId(), currentSyndic.getId());

        // Récupère le nombre de jours de retard le plus important parmi tous ses items impayés
        Integer maxDaysLate = chargeCallItemRepository
                .findMaxDaysLateByCoOwnerAndSyndic(user.getId(), currentSyndic.getId());

        // Applique la même logique de statut que le reste de l'application (Paiements/Impayés)
        String status;
        if (maxDaysLate == null || maxDaysLate <= 0) {
            status = "A_JOUR";
        } else if (maxDaysLate <= 30) {
            status = "RETARD";
        } else {
            status = "CRITIQUE";
        }

        // Calculer le solde global : SUM(paidAmount) - SUM(quotePart)
        // Négatif = doit de l'argent, Zéro = il ne doit pas d'argent
        BigDecimal solde = chargeCallItemRepository
                .calculateSoldeByCoOwnerAndSyndic(user.getId(), currentSyndic.getId());

        // Construire le DTO avec tous les champs calculés
        return CoOwnerListDTO.builder()
                .id(user.getId())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .photoUrl(user.getProfilePhotoUrl())
                .email(user.getEmail())
                .phone(user.getPhone())
                .apartmentsCount((int) apartmentsCount)
                .residencesCount((int) residencesCount)
                .status(status)
                .solde(solde)
                .build();
    }
    @Override
    @Transactional(readOnly = true)
    public List<CoOwnerPropertyItemDTO> getCoOwnerProperties(Long coOwnerId) {

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

        // Récupérer tous les lots du copropriétaire
        List<Property> allProperties = propertyRepository.findAllByOwnerId(coOwnerId);
        ArrayList<CoOwnerPropertyItemDTO> result = new ArrayList<>();
        int currentYear = Year.now().getValue();

        // Filtrer par syndic et construire les DTOs
        for (Property p : allProperties) {
            if (p.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
                BigDecimal annualCharge = calculateAnnualChargeForProperty(p, currentYear);
                CoOwnerPropertyItemDTO dto = CoOwnerPropertyItemDTO.builder()
                        .reference(p.getReference())
                        .bloc(p.getBloc())
                        .floor(p.getFloor())
                        .superficie(p.getSuperficie())
                        .tantieme(p.getTantieme())
                        .residenceName(p.getResidence().getName())
                        .annualCharge(annualCharge)
                        .build();
                result.add(dto);
            }
        }

        return result;
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

        // 1. Calculer annualCharges
        BigDecimal annualCharges = BigDecimal.ZERO;
        var budgetOpt = budgetRepository.findByResidenceIdAndAnnee(residenceId, currentYear);
        if (budgetOpt.isPresent()) {
            var budget = budgetOpt.get();
            // Calculer le tantième total du copropriétaire dans cette résidence
            BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
            List<Property> properties = propertyRepository.findAllByOwnerId(coOwnerId);
            for (Property p : properties) {
                if (p.getResidence().getId().equals(residenceId)) {
                    tantiemeCoOwner = tantiemeCoOwner.add(p.getTantieme());
                }
            }
            annualCharges = budget.getBudgetTotal()
                    .multiply(tantiemeCoOwner)
                    .divide(BigDecimal.valueOf(100));
        }

        // 2. Calculer monthlyCharges
        BigDecimal monthlyCharges = annualCharges.divide(BigDecimal.valueOf(12));

        // 3. Calculer currentBalance
        BigDecimal currentBalance = chargeCallItemRepository
                .calculateSoldeByCoOwnerAndResidence(coOwnerId, residenceId);

        // 4. Calculer paymentsMade et paymentsPercentage
        BigDecimal paymentsMade = chargeCallItemRepository
                .sumPaymentsMadeByCoOwnerAndResidence(coOwnerId, residenceId, currentYear);
        Double paymentsPercentage = 0.0;
        if (annualCharges.compareTo(BigDecimal.ZERO) > 0) {
            paymentsPercentage = paymentsMade.divide(annualCharges, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // 5. Calculer remainingToBill (montant restant à facturer)
        BigDecimal sumQuotePartGenerated = chargeCallItemRepository
                .sumQuotePartGeneratedByCoOwnerAndResidenceAndYear(coOwnerId, residenceId, currentYear);
        BigDecimal remainingToBill = annualCharges.subtract(sumQuotePartGenerated);
        if (remainingToBill.compareTo(BigDecimal.ZERO) < 0) {
            remainingToBill = BigDecimal.ZERO;
        }

        // 6. Répartition des charges (donut)
        ArrayList<ChargeBreakdownItemDTO> chargeBreakdown = new ArrayList<>();
        if (budgetOpt.isPresent()) {
            var budget = budgetOpt.get();
            for (var item : budget.getItems()) {
                Double percentage = 0.0;
                if (budget.getBudgetTotal().compareTo(BigDecimal.ZERO) > 0) {
                    percentage = item.getMontant().divide(budget.getBudgetTotal(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();
                }
                ChargeBreakdownItemDTO dto = ChargeBreakdownItemDTO.builder()
                        .label(item.getLibelle())
                        .percentage(percentage)
                        .amount(item.getMontant())
                        .build();
                chargeBreakdown.add(dto);
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
                .annualCharges(annualCharges)
                .monthlyCharges(monthlyCharges)
                .currentBalance(currentBalance)
                .paymentsMade(paymentsMade)
                .paymentsPercentage(paymentsPercentage)
                .remainingToBill(remainingToBill)
                .chargeBreakdown(chargeBreakdown)
                .monthlyPayments(monthlyPayments)
                .chargeCalls(chargeCalls)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerPaymentItemDTO> getCoOwnerPayments(Long coOwnerId, String status, Integer page, Integer size) {

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

        // Paginer les paiements
        Pageable pageable = PageRequest.of(page, size);
        Page<ChargeCallPayment> paymentPage = chargeCallPaymentRepository
                .findByCoOwnerAndSyndicAndStatus(coOwnerId, currentSyndic.getId(), status, pageable);

        // Mapper en DTOs
        ArrayList<CoOwnerPaymentItemDTO> dtos = new ArrayList<>();
        for (ChargeCallPayment payment : paymentPage.getContent()) {
            LocalDateTime date = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
            String paymentMethod = payment.getMethod() != null ? payment.getMethod().name() : null;
            String statusStr = payment.getStatus().name();
            Boolean receiptAvailable = payment.getStatus().name().equals("COMPLETED");

            CoOwnerPaymentItemDTO dto = CoOwnerPaymentItemDTO.builder()
                    .date(date)
                    .reference(payment.getReference())
                    .amount(payment.getAmount())
                    .paymentMethod(paymentMethod)
                    .status(statusStr)
                    .receiptAvailable(receiptAvailable)
                    .build();
            dtos.add(dto);
        }

        return new PageImpl<>(dtos, pageable, paymentPage.getTotalElements());
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
        return budget.getBudgetTotal()
                .multiply(property.getTantieme())
                .divide(BigDecimal.valueOf(100));
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
            propertyRepository.save(property);
        }

        // Supprimer le profil du copropriétaire
        CoOwnerProfile profile = coOwnerProfileRepository.findByUserId(coOwnerId)
                .orElse(null);
        if (profile != null) {
            coOwnerProfileRepository.delete(profile);
        }

        // Supprimer les relations syndic-copropriétaire
        List<SyndicOwnerRelation> relations = syndicCoOwnerRelationRepository.findAllBySyndicId(currentSyndic.getId(), Pageable.unpaged()).getContent();
        relations.stream()
                .filter(r -> r.getCoOwner().getId().equals(coOwnerId))
                .forEach(syndicCoOwnerRelationRepository::delete);

        // Supprimer l'utilisateur
        userRepository.delete(coOwner);
    }
}
