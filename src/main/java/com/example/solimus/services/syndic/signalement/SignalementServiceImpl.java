package com.example.solimus.services.syndic.signalement;

import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.owner.signalement.SignalementTimelineStepDTO;
import com.example.solimus.dtos.syndic.signalement.ResoudreSignalementDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementCardDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementDetailDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementListDTO;
import com.example.solimus.dtos.syndic.signalement.TransformerEnTravauxDTO;

import com.example.solimus.entities.*;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.SignalementEventType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.SignalementRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalementServiceImpl implements SignalementService {

    private final SignalementRepository signalementRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final InterventionRequestRepository interventionRepository;
    private final SpecialtyRepository specialtyRepository;

    @Override
    @Transactional(readOnly = true)
    public SyndicSignalementListDTO getSignalementsForSyndic(
            String search, SignalementStatus status, Long residenceId, int page, int size) {

        User currentSyndic = getCurrentUser();

        long totalSignalements = signalementRepository.countBySyndic(currentSyndic, residenceId);
        long enAttenteCount = signalementRepository.countBySyndicAndStatus(
                currentSyndic, SignalementStatus.PENDING, residenceId);
        long enTravauxCount = signalementRepository.countBySyndicAndStatus(
                currentSyndic, SignalementStatus.IN_TRAVAUX, residenceId);
        long traiteCount = signalementRepository.countBySyndicAndStatus(
                currentSyndic, SignalementStatus.RESOLVED, residenceId);

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Signalement> signalementsPage = signalementRepository
                .findBySyndicWithFiltersAndResidence(currentSyndic, normalizedSearch, status, residenceId, pageable);

        Page<SyndicSignalementCardDTO> dtoPage = signalementsPage.map(this::mapToSyndicCardDTO);

        return SyndicSignalementListDTO.builder()
                .totalSignalements(totalSignalements)
                .enAttenteCount(enAttenteCount)
                .enTravauxCount(enTravauxCount)
                .traiteCount(traiteCount)
                .signalements(dtoPage)
                .build();
    }

    private SyndicSignalementCardDTO mapToSyndicCardDTO(Signalement signalement) {
        String declaredByName = signalement.getDeclaredBy() != null
                ? signalement.getDeclaredBy().getFirstName() + " " + signalement.getDeclaredBy().getLastName()
                : null;

        return SyndicSignalementCardDTO.builder()
                .id(signalement.getId())
                .reference(signalement.getReference())
                .title(signalement.getTitle())
                .propertyReference(signalement.getProperty() != null ? signalement.getProperty().getReference() : null)
                .commonFacilityName(signalement.getCommonFacility() != null ? signalement.getCommonFacility().getFacilityType().getName() : null)
                .locationType(signalement.getLocationType())
                .residenceName(signalement.getResidence() != null ? signalement.getResidence().getName() : null)
                .declaredByName(declaredByName)
                .urgencyLevel(signalement.getUrgencyLevel())
                .urgencyLabel(signalement.getUrgencyLevel() != null ? signalement.getUrgencyLevel().name() : null)
                .status(signalement.getStatus())
                .statusLabel(signalement.getStatus() != null ? signalement.getStatus().getLabel() : null)
                .createdAt(signalement.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SyndicSignalementDetailDTO getSignalementDetailForSyndic(Long signalementId) {

        User currentSyndic = getCurrentUser();

        Signalement signalement = signalementRepository.findById(signalementId)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        if (signalement.getResidence().getSyndic() == null
                || !signalement.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à voir ce signalement");
        }

        return mapToSyndicDetailDTO(signalement);
    }

    private SyndicSignalementDetailDTO mapToSyndicDetailDTO(Signalement signalement) {

        String declaredByName = signalement.getDeclaredBy() != null
                ? signalement.getDeclaredBy().getFirstName() + " " + signalement.getDeclaredBy().getLastName()
                : null;

        List<String> photoUrls = minioService.toPresignedUrls(signalement.getPhotoUrls());

        return SyndicSignalementDetailDTO.builder()
                .id(signalement.getId())
                .reference(signalement.getReference())
                .title(signalement.getTitle())
                .residenceName(signalement.getResidence() != null ? signalement.getResidence().getName() : null)
                .propertyReference(signalement.getProperty() != null ? signalement.getProperty().getReference() : null)
                .commonFacilityName(signalement.getCommonFacility() != null ? signalement.getCommonFacility().getFacilityType().getName() : null)
                .locationType(signalement.getLocationType())
                .declaredByName(declaredByName)
                .declaredByRole("Copropriétaire")
                .status(signalement.getStatus())
                .statusLabel(signalement.getStatus() != null ? signalement.getStatus().getLabel() : null)
                .urgencyLevel(signalement.getUrgencyLevel())
                .urgencyLabel(signalement.getUrgencyLevel() != null ? signalement.getUrgencyLevel().getLabel() : null)
                .description(signalement.getDescription())
                .photoUrls(photoUrls)
                .createdAt(signalement.getCreatedAt())
                .historique(buildSignalementTimeline(signalement))
                .build();
    }

    private List<SignalementTimelineStepDTO> buildSignalementTimeline(Signalement signalement) {

        List<SignalementTimelineStepDTO> timeline = new ArrayList<>();

        // Étape 1 : Création — auteur = déclarant
        timeline.add(SignalementTimelineStepDTO.builder()
                .label("Signalement créé")
                .date(signalement.getCreatedAt())
                .completed(true)
                .auteurName(formatAuteurName(signalement.getDeclaredBy()))
                .auteurRole("Copropriétaire")
                .build());

        List<SignalementHistorique> historique = signalement.getHistorique();
        if (historique == null) {
            historique = new ArrayList<>();
        }

        Optional<SignalementHistorique> transformeEnTravaux = Optional.empty();
        Optional<SignalementHistorique> resoluSansTravaux = Optional.empty();
        Optional<SignalementHistorique> travauxTermines = Optional.empty();

        for (SignalementHistorique h : historique) {
            if (h.getTypeEvenement() == SignalementEventType.TRANSFORME_EN_TRAVAUX) {
                transformeEnTravaux = Optional.of(h);
            } else if (h.getTypeEvenement() == SignalementEventType.RESOLU_SANS_TRAVAUX) {
                resoluSansTravaux = Optional.of(h);
            } else if (h.getTypeEvenement() == SignalementEventType.TRAVAUX_TERMINES) {
                travauxTermines = Optional.of(h);
            }
        }

        if (transformeEnTravaux.isPresent()) {
            SignalementHistorique h = transformeEnTravaux.get();
            timeline.add(SignalementTimelineStepDTO.builder()
                    .label("Transformé en demande de travaux")
                    .date(h.getCreatedAt())
                    .completed(true)
                    .auteurName(formatAuteurName(h.getAuteur()))
                    .auteurRole("Syndic")
                    .build());

            if (travauxTermines.isPresent()) {
                timeline.add(SignalementTimelineStepDTO.builder()
                        .label("Travaux terminés")
                        .date(travauxTermines.get().getCreatedAt())
                        .completed(true)
                        .auteurName(null)
                        .auteurRole(null)
                        .build());
            } else {
                timeline.add(SignalementTimelineStepDTO.builder()
                        .label("Travaux terminés")
                        .date(null)
                        .completed(false)
                        .auteurName(null)
                        .auteurRole(null)
                        .build());
            }

        } else if (resoluSansTravaux.isPresent()) {
            SignalementHistorique h = resoluSansTravaux.get();
            timeline.add(SignalementTimelineStepDTO.builder()
                    .label("Résolu par le syndic")
                    .date(h.getCreatedAt())
                    .completed(true)
                    .auteurName(formatAuteurName(h.getAuteur()))
                    .auteurRole("Syndic")
                    .build());

        } else {
            timeline.add(SignalementTimelineStepDTO.builder()
                    .label("En attente de traitement")
                    .date(null)
                    .completed(false)
                    .auteurName(null)
                    .auteurRole(null)
                    .build());
        }

        return timeline;
    }

    private String formatAuteurName(User user) {
        if (user == null) return null;
        return user.getFirstName() + " " + user.getLastName();
    }

    @Override
    @Transactional
    public void resoudreSansTravaux(Long signalementId, ResoudreSignalementDTO dto) {

        User currentSyndic = getCurrentUser();
        Signalement signalement = getSignalementForSyndic(signalementId, currentSyndic);

        if (signalement.getStatus() != SignalementStatus.PENDING) {
            throw new BadRequestException("Ce signalement a déjà été traité");
        }

        signalement.setNoteCloture(dto.getNoteCloture());
        signalement.addHistorique(SignalementEventType.RESOLU_SANS_TRAVAUX, dto.getNoteCloture(), currentSyndic);

        signalementRepository.save(signalement);

        // Notifier le copropriétaire de la résolution
        notifyOwnerForSignalementResolu(signalement);
    }

    private void notifyOwnerForSignalementResolu(Signalement signalement) {
        User owner = signalement.getDeclaredBy();

        if (owner == null || !owner.isNotificationsEnabled()) {
            return;
        }

        notificationService.sendPush(
                owner.getId(),
                "Signalement résolu",
                "Votre signalement \"" + signalement.getTitle() + "\" a été traité par le syndic."
        );

        emailService.sendSignalementResoluNotification(
                owner.getEmail(),
                owner.getFirstName(),
                signalement.getTitle(),
                signalement.getNoteCloture()
        );
    }

    private Signalement getSignalementForSyndic(Long signalementId, User syndic) {
        Signalement signalement = signalementRepository.findById(signalementId)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        if (signalement.getResidence().getSyndic() == null
                || !signalement.getResidence().getSyndic().getId().equals(syndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à traiter ce signalement");
        }
        return signalement;
    }

    @Override
    @Transactional
    public InterventionRequestDTO transformerEnTravaux(Long signalementId, TransformerEnTravauxDTO dto) {

        User currentSyndic = getCurrentUser();
        Signalement signalement = getSignalementForSyndic(signalementId, currentSyndic);

        if (signalement.getStatus() != SignalementStatus.PENDING) {
            throw new BadRequestException("Ce signalement a déjà été traité");
        }

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        InterventionRequest request = new InterventionRequest();
        request.setReference(genererReferenceIntervention());
        request.setTitle(signalement.getTitle());
        request.setDescription(signalement.getDescription());
        request.setInitiatedBy(InitiatedBy.SYNDIC);
        request.setSyndic(currentSyndic);
        request.setResidence(signalement.getResidence());
        request.setSpecialty(specialty);
        request.setUrgencyLevel(dto.getPriorite());
        request.setLocationType(signalement.getLocationType());
        request.setPhotoUrls(signalement.getPhotoUrls());
        request.setManagementMode(InterventionManagementMode.SYNDIC);

        if (signalement.getLocationType() == IncidentLocationType.APPARTEMENT) {
            request.setProperty(signalement.getProperty());
        } else {
            request.setCommonFacility(signalement.getCommonFacility());
        }

        request.addStatusHistory(InterventionStatus.PENDING, currentSyndic);
        InterventionRequest savedRequest = interventionRepository.save(request);

        signalement.setInterventionRequest(savedRequest);
        signalement.addHistorique(SignalementEventType.TRANSFORME_EN_TRAVAUX, null, currentSyndic);
        signalementRepository.save(signalement);

        notifyNearbyProvidersForApartmentRequest(savedRequest, signalement.getResidence(), specialty);

        return mapToInterventionDTO(savedRequest);
    }

    private String genererReferenceIntervention() {
        long totalExistant = interventionRepository.count();
        long prochainNumero = totalExistant + 1;
        return String.format("TRV-%03d", prochainNumero);
    }

    private void notifyNearbyProvidersForApartmentRequest(InterventionRequest request, Residence residence, Specialty specialty) {
        if (residence.getLatitude() == null || residence.getLongitude() == null) {
            log.warn("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
            return;
        }

       // notificationService.sendPushToNearbyProviders(request, residence, specialty);
    }

    private InterventionRequestDTO mapToInterventionDTO(InterventionRequest request) {
        return InterventionRequestDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .statusLabel(request.getStatus() != null ? request.getStatus().name() : null)
                .initiatedBy(request.getInitiatedBy())
                .locationType(request.getLocationType())
                .managementMode(request.getManagementMode())
                .urgencyLevel(request.getUrgencyLevel())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : null)
                .photoUrls(minioService.toPresignedUrls(request.getPhotoUrls()))
                .createdAt(request.getCreatedAt())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
