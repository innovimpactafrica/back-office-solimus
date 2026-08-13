package com.example.solimus.services.syndic.signalement;

import com.example.solimus.dtos.owner.signalement.SignalementHistoryItemDTO;
import com.example.solimus.dtos.syndic.signalement.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.shared.StatusRecalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalementServiceImpl implements SignalementService {

    private final SignalementRepository signalementRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final StatusRecalculationService statusRecalculationService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // =========================================================================
    // DASHBOARD
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SignalementDashboardDTO getDashboard() {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Compte les signalements par statut, restreints aux résidences de ce syndic
        long total = signalementRepository.countByResidenceSyndicId(currentSyndic.getId());
        long inProgress = signalementRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), SignalementStatus.IN_PROGRESS);
        long resolved = signalementRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), SignalementStatus.RESOLVED);
        long pending = signalementRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), SignalementStatus.PENDING);

        return SignalementDashboardDTO.builder()
                .total(total)
                .inProgress(inProgress)
                .resolved(resolved)
                .pending(pending)
                .build();
    }

    // =========================================================================
    // LISTER LES SIGNALEMENTS (SYNDIC)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicSignalementListResponse getSignalementsForSyndic(
            String search, SignalementStatus status, Long residenceId, int page, int size) {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Construit la pagination, triée du signalement le plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupère les signalements du syndic, avec les filtres optionnels appliqués
        Page<Signalement> signalementPage = signalementRepository.searchForSyndic(
                currentSyndic.getId(), search, status, residenceId, pageable);

        // Transforme chaque signalement de la page en carte
        List<SyndicSignalementCardDTO> cards = signalementPage.getContent().stream()
                .map(this::buildSignalementCard)
                .toList();

        return SyndicSignalementListResponse.builder()
                .signalements(cards)
                .currentPage(page)
                .totalPages(signalementPage.getTotalPages())
                .totalElements(signalementPage.getTotalElements())
                .build();
    }

    // =========================================================================
    // DÉTAIL D'UN SIGNALEMENT (SYNDIC)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicSignalementDetailDTO getSignalementDetailForSyndic(Long id) {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupère le signalement, erreur si introuvable
        Signalement signalement = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        // Vérifie que ce signalement appartient bien à une résidence gérée par ce syndic
        if (!signalement.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce signalement");
        }

        return buildSignalementDetail(signalement);
    }

    // =========================================================================
    // RÉSOUDRE SANS TRAVAUX
    // =========================================================================

    @Override
    @Transactional
    public void resolveWithoutWork(Long id, ResolveSignalementDTO dto) {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupère le signalement, erreur si introuvable
        Signalement signalement = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        // Vérifie que ce signalement appartient bien à une résidence gérée par ce syndic
        if (!signalement.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à traiter ce signalement");
        }

        // Vérifie que le signalement n'est pas déjà résolu ou converti en travaux
        if (signalement.getStatus() == SignalementStatus.RESOLVED) {
            throw new BadRequestException("Ce signalement est déjà résolu");
        }
        if (signalement.getStatus() == SignalementStatus.CONVERTED_TO_WORK) {
            throw new BadRequestException("Ce signalement a déjà été transformé en demande de travaux");
        }

        // Enregistre la note de clôture et la date de fermeture
        signalement.setClosingNote(dto.getClosingNote());
        signalement.setClosedAt(java.time.LocalDateTime.now());

        // Trace le changement de statut dans l'historique
        signalement.addStatusHistory(SignalementStatus.RESOLVED, currentSyndic, "Résolu par le syndic — " + dto.getClosingNote());

        signalementRepository.save(signalement);

        // Notifie le copropriétaire (et le locataire si c'est lui qui a déclaré), push + email
        notifyOwnerAndTenant(signalement, "Signalement résolu",
                signalement.getTitle() + " a été traité par le syndic");
    }

    // =========================================================================
    // TRANSFORMER EN TRAVAUX
    // =========================================================================

    @Override
    @Transactional
    public Long convertToWork(Long id, ConvertToWorkDTO dto) {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupère le signalement, erreur si introuvable
        Signalement signalement = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        // Vérifie que ce signalement appartient bien à une résidence gérée par ce syndic
        if (!signalement.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à traiter ce signalement");
        }

        // Vérifie que le signalement n'est pas déjà résolu ou converti en travaux
        if (signalement.getStatus() == SignalementStatus.RESOLVED) {
            throw new BadRequestException("Ce signalement est déjà résolu");
        }
        if (signalement.getStatus() == SignalementStatus.CONVERTED_TO_WORK) {
            throw new BadRequestException("Ce signalement a déjà été transformé en demande de travaux");
        }

        // Récupère la spécialité choisie
        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        // Crée la nouvelle demande d'intervention, en reprenant les infos du signalement
        InterventionRequest request = new InterventionRequest();
        request.setReference(generateInterventionReference());
        request.setTitle(signalement.getTitle());
        request.setDescription(dto.getWorkDescription());
        request.addStatusHistory(InterventionStatus.PENDING, currentSyndic);
        request.setInitiatedBy(InitiatedBy.SYNDIC);
        request.setSyndic(currentSyndic);
        request.setResidence(signalement.getResidence());
        request.setSpecialty(specialty);
        request.setPhotoUrls(new ArrayList<>(signalement.getPhotoUrls()));
        request.setUrgencyLevel(dto.getPriority());
        request.setLocationType(signalement.getLocationType());
        request.setManagementMode(InterventionManagementMode.SYNDIC);

        // Reporte systématiquement le copropriétaire et le locataire d'origine sur l'intervention,
        // pour qu'ils puissent tous les deux la suivre sur leur propre écran "Mes travaux"
        // (findByOwnerWithFiltersAndResidence / findByTenantWithFilters filtrent strictement sur ces champs)
        request.setOwner(signalement.getOwner());
        request.setTenant(signalement.getTenant());

        if (signalement.getLocationType() == IncidentLocationType.APPARTEMENT) {
            request.setProperty(signalement.getProperty());
            // Privilégie le propriétaire actuel du bien s'il a changé depuis la création du signalement
            if (signalement.getProperty() != null && signalement.getProperty().getOwner() != null) {
                request.setOwner(signalement.getProperty().getOwner());
            }
        } else {
            request.setCommonFacility(signalement.getCommonFacility());
        }

        InterventionRequest savedIntervention = interventionRequestRepository.save(request);

        // Une intervention URGENT active peut faire passer la résidence en CRITIQUE
        statusRecalculationService.recalculateResidenceHealthStatus(signalement.getResidence());

        // Lie le signalement à cette nouvelle intervention et met à jour son statut
        signalement.setLinkedIntervention(savedIntervention);
        signalement.addStatusHistory(SignalementStatus.CONVERTED_TO_WORK, currentSyndic,
                "Transformé en demande de travaux — " + dto.getWorkDescription());

        signalementRepository.save(signalement);

        // Notifie le copropriétaire (et le locataire si c'est lui qui a déclaré), push + email
        notifyOwnerAndTenant(signalement, "Signalement transformé en travaux",
                signalement.getTitle() + " nécessite une intervention");

        return savedIntervention.getId();
    }

    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    // Notifie le copropriétaire (toujours) et le locataire (uniquement s'il est à l'origine du
    // signalement — Signalement.tenant), en push + email pour chacun, de façon non-bloquante
    private void notifyOwnerAndTenant(Signalement signalement, String title, String message) {
        notifyUser(signalement.getOwner(), title, message);
        if (signalement.getTenant() != null) {
            notifyUser(signalement.getTenant(), title, message);
        }
    }

    // Envoie une notification push (si activée) + un email (toujours) à un utilisateur donné,
    // sans jamais bloquer l'appelant en cas d'échec d'envoi
    private void notifyUser(User user, String title, String message) {
        try {
            if (user.isNotificationsEnabled()) {
                notificationService.sendPush(user.getId(), title, message);
            }
            emailService.sendEmail(user.getEmail(), title, message);
        } catch (Exception e) {
            System.err.println("Erreur envoi notification signalement à " + user.getEmail() + ": " + e.getMessage());
        }
    }

    // Récupère l'utilisateur actuellement authentifié via le SecurityContext
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    // Construit le libellé de position affiché (appartement ou équipement commun)
    private String buildPositionLabel(Signalement signalement) {
        if (signalement.getLocationType() == IncidentLocationType.APPARTEMENT) {
            return signalement.getProperty() != null ? "Appartement " + signalement.getProperty().getReference() : "";
        } else {
            return signalement.getCommonFacility() != null && signalement.getCommonFacility().getFacilityType() != null
                    ? signalement.getCommonFacility().getFacilityType().getName() : "";
        }
    }

    // Construit une carte de signalement pour la liste syndic
    private SyndicSignalementCardDTO buildSignalementCard(Signalement signalement) {
        return SyndicSignalementCardDTO.builder()
                .id(signalement.getId())
                .title(signalement.getTitle())
                .positionLabel(buildPositionLabel(signalement))
                .residenceName(signalement.getResidence().getName())
                .declaredByName(signalement.getOwner().getFirstName() + " " + signalement.getOwner().getLastName())
                .createdAt(signalement.getCreatedAt())
                .urgencyLevel(signalement.getUrgencyLevel().name())
                .status(signalement.getStatus().getLabel())
                .build();
    }

    // Construit le détail complet d'un signalement pour la vue syndic
    private SyndicSignalementDetailDTO buildSignalementDetail(Signalement signalement) {

        List<SignalementHistoryItemDTO> historyDtos = signalement.getHistory().stream()
                .map(h -> SignalementHistoryItemDTO.builder()
                        .status(h.getStatus().getLabel())
                        .label(h.getNote())
                        .changedByName(h.getChangedBy() != null
                                ? h.getChangedBy().getFirstName() + " " + h.getChangedBy().getLastName() : null)
                        .date(h.getCreatedAt())
                        .build())
                .toList();

        // Convertir les photos en URLs publiques directes
        List<String> photoUrls = signalement.getPhotoUrls() != null
                ? signalement.getPhotoUrls()
                : new ArrayList<>();

        return SyndicSignalementDetailDTO.builder()
                .id(signalement.getId())
                .reference(signalement.getReference())
                .title(signalement.getTitle())
                .description(signalement.getDescription())
                .residenceName(signalement.getResidence().getName())
                .positionLabel(buildPositionLabel(signalement))
                .createdAt(signalement.getCreatedAt())
                .urgencyLevel(signalement.getUrgencyLevel().name())
                .status(signalement.getStatus().getLabel())
                .photoUrls(photoUrls)
                .declaredByName(signalement.getOwner().getFirstName() + " " + signalement.getOwner().getLastName())
                .declaredByPhone(signalement.getOwner().getPhone())
                .declaredByEmail(signalement.getOwner().getEmail())
                .closingNote(signalement.getClosingNote())
                .linkedInterventionId(signalement.getLinkedIntervention() != null ? signalement.getLinkedIntervention().getId() : null)
                .history(historyDtos)
                .build();
    }

    // Génère une référence unique pour une intervention créée depuis un signalement
    private String generateInterventionReference() {
        long total = interventionRequestRepository.count();
        return String.format("TRV-%03d", total + 1);
    }
}