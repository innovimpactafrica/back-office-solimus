package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionSummaryDTO;
import com.example.solimus.dtos.owner.travaux.OwnerTimelineStepDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.entities.CommonFacility;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.Specialty;
import com.example.solimus.entities.User;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CommonFacilityRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.UserRepository;
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
public class OwnerTravauxManualServiceImpl implements OwnerTravauxManualService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final ResidenceRepository residenceRepository;
    private final SpecialtyRepository specialtyRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final StatusRecalculationService statusRecalculationService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // =========================================================================
    // Recupére les résidences du copropriétaire connecté
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ResidenceDTO> getMyResidences() {
        User currentOwner = getCurrentUser();

        return propertyRepository.findAllByOwnerId(currentOwner.getId()).stream()
                .map(Property::getResidence)
                .filter(residence -> residence != null)
                .distinct()
                .map(this::mapToResidenceDTO)
                .toList();
    }

    // =========================================================================
    // Lister les parties communes d'une résidence où le owner a au moins un bien
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId) {
        User currentOwner = getCurrentUser();

        if (!propertyRepository.existsByOwnerIdAndResidenceId(currentOwner.getId(), residenceId)) {
            throw new ForbiddenException("Vous n'avez pas de bien dans cette résidence");
        }

        return commonFacilityRepository.findByResidenceId(residenceId).stream()
                .map(cf -> CommonFacilityDTO.builder()
                        .id(cf.getId())
                        .label(cf.getFacilityType().getName())
                        .build())
                .toList();
    }

    // =========================================================================
    // Lister mes biens dans une résidence donnée
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PropertyDTO> getMyPropertiesByResidence(Long residenceId) {
        User currentOwner = getCurrentUser();

        return propertyRepository.findByOwnerIdAndResidenceId(currentOwner.getId(), residenceId)
                .stream()
                .map(this::mapToPropertyDTO)
                .toList();
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION — toujours gérée manuellement par le syndic
    // =========================================================================

    @Override
    @Transactional
    public void createIntervention(CreateOwnerInterventionRequestDTO dto) {

        User currentOwner = getCurrentUser();

        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (!propertyRepository.existsByOwnerIdAndResidenceId(currentOwner.getId(), dto.getResidenceId())) {
            throw new ForbiddenException("Vous n'avez pas de bien dans cette résidence");
        }

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        InterventionRequest request = new InterventionRequest();
        request.setReference(genererReference());
        request.setTitle(dto.getTitle());
        request.setDescription(dto.getDescription());
        request.addStatusHistory(InterventionStatus.PENDING, currentOwner);
        request.setInitiatedBy(InitiatedBy.OWNER);
        request.setOwner(currentOwner);
        request.setResidence(residence);
        request.setSpecialty(specialty);
        request.setPhotoUrls(dto.getPhotoUrls());
        request.setUrgencyLevel(dto.getUrgencyLevel());
        request.setLocationType(dto.getLocationType());
        // Circuit prestataire désactivé : toute demande de travaux copropriétaire est gérée manuellement par le syndic
        request.setManagementMode(InterventionManagementMode.SYNDIC);

        if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            if (dto.getPropertyId() != null) {
                throw new BadRequestException("Pour une demande de type PARTIE_COMMUNE, aucun bien ne doit être spécifié");
            }
            if (dto.getCommonFacilityId() == null) {
                throw new BadRequestException("Pour une demande de type PARTIE_COMMUNE, l'équipement commun concerné doit être spécifié");
            }

            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));

            if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(dto.getResidenceId())) {
                throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
            }
            request.setProperty(null);
            request.setCommonFacility(commonFacility);
        } else if (dto.getLocationType() == IncidentLocationType.APPARTEMENT) {
            if (dto.getPropertyId() == null) {
                throw new BadRequestException("Pour une demande de type APPARTEMENT, un bien doit être spécifié");
            }
            if (dto.getCommonFacilityId() != null) {
                throw new BadRequestException("Pour une demande de type APPARTEMENT, aucun équipement commun ne doit être spécifié");
            }

            Property property = propertyRepository.findById(dto.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

            if (!propertyRepository.existsByIdAndOwnerIdAndResidenceId(dto.getPropertyId(), currentOwner.getId(), dto.getResidenceId())) {
                throw new ForbiddenException("Ce bien ne vous appartient pas ou n'appartient pas à cette résidence");
            }
            request.setProperty(property);
            request.setCommonFacility(null);
        }

        interventionRequestRepository.save(request);

        // Une intervention URGENT active peut faire passer la résidence en CRITIQUE
        statusRecalculationService.recalculateResidenceHealthStatus(residence);

        // Notifie uniquement le syndic — jamais les prestataires, peu importe locationType
        notifySyndicManualFlow(request, residence, currentOwner);
    }

    // =========================================================================
    // LISTER MES DEMANDES DE TRAVAUX (recherche + filtres + pagination)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, Long residenceId, int page, int size) {

        User currentOwner = getCurrentUser();

        long totalIncidents = interventionRequestRepository.countByOwner(currentOwner, residenceId);
        long enCoursCount = interventionRequestRepository.countByOwnerAndStatus(currentOwner, InterventionStatus.STARTED, residenceId);

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<InterventionRequest> interventions = interventionRequestRepository
                .findByOwnerWithFiltersAndResidence(currentOwner, normalizedSearch, status, residenceId, pageable);

        Page<OwnerInterventionSummaryDTO> interventionsPage = interventions.map(this::mapToSummaryDTO);

        return OwnerInterventionDTO.builder()
                .totalIncidents(totalIncidents)
                .enCoursCount(enCoursCount)
                .interventions(interventionsPage)
                .build();
    }

    // =========================================================================
    // DÉTAIL D'INTERVENTION
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionDetailDTO getInterventionDetail(Long interventionId) {

        User currentOwner = getCurrentUser();

        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (request.getOwner() == null || !request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à voir cette intervention");
        }

        return mapToDetailDTO(request);
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private String genererReference() {
        long totalExistant = interventionRequestRepository.count();
        long prochainNumero = totalExistant + 1;
        return String.format("TRV-%03d", prochainNumero);
    }

    /**
     * Notifie le syndic de la résidence d'une demande de travaux créée par un copropriétaire.
     * Push uniquement si urgent (respecte sa préférence "Incidents urgents"), email systématique.
     * Chemin de notification unique, quel que soit locationType (contrairement à l'ancien flux
     * qui distinguait appartement/partie commune pour le matching prestataire).
     */
    private void notifySyndicManualFlow(InterventionRequest request, Residence residence, User owner) {
        if (residence.getSyndic() == null) {
            return;
        }

        User syndic = residence.getSyndic();
        String ownerName = owner.getFirstName() + " " + owner.getLastName();

        if (request.getUrgencyLevel() == UrgencyLevel.URGENT) {
            notificationService.sendUrgentIncidentNotification(
                    syndic.getId(),
                    "Incident urgent signalé",
                    ownerName + " a signalé un problème urgent : " + request.getTitle()
            );
        }

        emailService.sendSyndicInterventionNotification(
                syndic.getEmail(),
                syndic.getFirstName(),
                request.getTitle(),
                residence.getName(),
                ownerName
        );
    }

    private ResidenceDTO mapToResidenceDTO(Residence residence) {
        return ResidenceDTO.builder()
                .id(residence.getId())
                .name(residence.getName())
                .fullAddress(residence.getFullAddress())
                .latitude(residence.getLatitude())
                .longitude(residence.getLongitude())
                .lotsCount(residence.getLotsCount())
                .syndicId(residence.getSyndic() != null ? residence.getSyndic().getId() : null)
                .syndicName(residence.getSyndic() != null ? residence.getSyndic().getFirstName() + " " + residence.getSyndic().getLastName() : null)
                .createdAt(residence.getCreatedAt())
                .build();
    }

    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .area(property.getArea())
                .typeName(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                .residenceId(property.getResidence() != null ? property.getResidence().getId() : null)
                .residenceName(property.getResidence() != null ? property.getResidence().getName() : null)
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }

    private OwnerInterventionSummaryDTO mapToSummaryDTO(InterventionRequest intervention) {
        return OwnerInterventionSummaryDTO.builder()
                .id(intervention.getId())
                .title(intervention.getTitle())
                .residenceName(intervention.getResidence() != null ? intervention.getResidence().getName() : null)
                .propertyReference(intervention.getProperty() != null ? intervention.getProperty().getReference() : null)
                .commonFacilityName(intervention.getCommonFacility() != null ? intervention.getCommonFacility().getFacilityType().getName() : null)
                .specialtyName(intervention.getSpecialty() != null ? intervention.getSpecialty().getName() : null)
                .specialtyIcon(intervention.getSpecialty() != null ? intervention.getSpecialty().getIcon() : null)
                .statusLabel(intervention.getStatus() != null ? intervention.getStatus().getLabel() : null)
                .status(intervention.getStatus())
                .urgencyLabel(intervention.getUrgencyLevel() != null ? intervention.getUrgencyLevel().getLabel() : null)
                .urgencyLevel(intervention.getUrgencyLevel())
                .createdAt(intervention.getCreatedAt())
                .fromTenant(intervention.getTenant() != null)
                .tenantName(intervention.getTenant() != null
                        ? intervention.getTenant().getFirstName() + " " + intervention.getTenant().getLastName() : null)
                .build();
    }

    private OwnerInterventionDetailDTO mapToDetailDTO(InterventionRequest request) {

        boolean isCommonArea = request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE;

        String propertyReference = null;
        if (!isCommonArea && request.getProperty() != null) {
            propertyReference = request.getProperty().getReference();
        }

        String commonFacilityName = null;
        if (isCommonArea && request.getCommonFacility() != null) {
            commonFacilityName = request.getCommonFacility().getFacilityType().getName();
        }

        List<String> photoUrls = request.getPhotoUrls() != null
                ? request.getPhotoUrls()
                : new ArrayList<>();

        return OwnerInterventionDetailDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : null)
                .propertyReference(propertyReference)
                .commonFacilityName(commonFacilityName)
                .createdAt(request.getCreatedAt())
                .statusLabel(request.getStatus() != null ? request.getStatus().getLabel() : null)
                .status(request.getStatus())
                .urgencyLabel(request.getUrgencyLevel() != null ? request.getUrgencyLevel().getLabel() : null)
                .urgencyLevel(request.getUrgencyLevel())
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .specialtyIcon(request.getSpecialty() != null ? request.getSpecialty().getIcon() : null)
                .photoUrls(photoUrls)
                .selectedProvider(null) // circuit prestataire désactivé pour le flux manuel
                .timeline(buildTimeline(request))
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .fromTenant(request.getTenant() != null)
                .tenantName(request.getTenant() != null
                        ? request.getTenant().getFirstName() + " " + request.getTenant().getLastName() : null)
                .build();
    }

    // Timeline simplifiée du flux manuel (pas de devis/prestataire) :
    // Incident envoyé → Pris en charge par le syndic → Travail terminé → Validation finale
    private List<OwnerTimelineStepDTO> buildTimeline(InterventionRequest request) {
        List<OwnerTimelineStepDTO> timeline = new ArrayList<>();

        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Incident envoyé")
                .date(request.getCreatedAt())
                .completed(true)
                .build());

        boolean started = request.getStartedAt() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Pris en charge par le syndic")
                .date(request.getStartedAt())
                .completed(started)
                .build());

        boolean finished = request.getFinishedAt() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Travail terminé")
                .date(request.getFinishedAt())
                .completed(finished)
                .build());

        boolean validated = request.getStatus() == InterventionStatus.FINAL_VALIDATION;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Validation finale")
                .date(validated ? request.getValidatedAt() : null)
                .completed(validated)
                .build());

        return timeline;
    }
}
