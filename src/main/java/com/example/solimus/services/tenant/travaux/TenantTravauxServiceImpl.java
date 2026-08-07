package com.example.solimus.services.tenant.travaux;

import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionSummaryDTO;
import com.example.solimus.dtos.owner.travaux.OwnerTimelineStepDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.tenant.TenantPropertyInfoDTO;
import com.example.solimus.dtos.tenant.travaux.CreateTenantInterventionRequestDTO;
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
public class TenantTravauxServiceImpl implements TenantTravauxService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final SpecialtyRepository specialtyRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final StatusRecalculationService statusRecalculationService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // =========================================================================
    // MON BIEN LOUÉ
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public TenantPropertyInfoDTO getMyProperty() {
        User currentTenant = getCurrentUser();
        Property property = getMyRentedProperty(currentTenant);

        return TenantPropertyInfoDTO.builder()
                .propertyReference(property.getReference())
                .residenceName(property.getResidence().getName())
                .build();
    }

    // =========================================================================
    // LISTER LES PARTIES COMMUNES DE MA RÉSIDENCE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilityDTO> getCommonFacilities() {
        User currentTenant = getCurrentUser();
        Property property = getMyRentedProperty(currentTenant);

        return commonFacilityRepository.findByResidenceId(property.getResidence().getId()).stream()
                .map(cf -> CommonFacilityDTO.builder()
                        .id(cf.getId())
                        .label(cf.getFacilityType().getName())
                        .build())
                .toList();
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION — toujours gérée manuellement par le syndic
    // =========================================================================

    @Override
    @Transactional
    public void createIntervention(CreateTenantInterventionRequestDTO dto) {

        User currentTenant = getCurrentUser();
        Property property = getMyRentedProperty(currentTenant);
        Residence residence = property.getResidence();

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        InterventionRequest request = new InterventionRequest();
        request.setReference(genererReference());
        request.setTitle(dto.getTitle());
        request.setDescription(dto.getDescription());
        request.addStatusHistory(InterventionStatus.PENDING, currentTenant);
        request.setInitiatedBy(InitiatedBy.TENANT);
        request.setTenant(currentTenant);
        request.setOwner(property.getOwner());
        request.setResidence(residence);
        request.setSpecialty(specialty);
        request.setPhotoUrls(dto.getPhotoUrls() != null ? dto.getPhotoUrls() : new ArrayList<>());
        request.setUrgencyLevel(dto.getUrgencyLevel());
        request.setLocationType(dto.getLocationType());
        // Circuit prestataire désactivé : toute demande de travaux locataire est gérée manuellement par le syndic
        request.setManagementMode(InterventionManagementMode.SYNDIC);

        if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            if (dto.getCommonFacilityId() == null) {
                throw new BadRequestException("commonFacilityId est obligatoire lorsque locationType est PARTIE_COMMUNE");
            }
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));

            if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(residence.getId())) {
                throw new BadRequestException("Cet équipement commun n'appartient pas à votre résidence");
            }
            request.setCommonFacility(commonFacility);
        } else {
            if (dto.getCommonFacilityId() != null) {
                throw new BadRequestException("commonFacilityId ne doit pas être fourni lorsque locationType est APPARTEMENT");
            }
            request.setProperty(property);
        }

        interventionRequestRepository.save(request);

        // Une intervention URGENT active peut faire passer la résidence en CRITIQUE
        statusRecalculationService.recalculateResidenceHealthStatus(residence);

        // Notifie toujours le propriétaire
        if (property.getOwner().isNotificationsEnabled()) {
            notificationService.sendPush(property.getOwner().getId(), "Demande de travaux de votre locataire",
                    currentTenant.getFirstName() + " a fait une demande : " + request.getTitle());
        }

        // Notifie le syndic (push urgent + email systématique)
        notifySyndicManualFlow(request, residence, currentTenant);
    }

    // =========================================================================
    // LISTER MES DEMANDES DE TRAVAUX
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, int page, int size) {

        User currentTenant = getCurrentUser();

        long totalIncidents = interventionRequestRepository.countByTenant(currentTenant);
        long enCoursCount = interventionRequestRepository.countByTenantAndStatus(currentTenant, InterventionStatus.STARTED);

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<InterventionRequest> interventions = interventionRequestRepository
                .findByTenantWithFilters(currentTenant, normalizedSearch, status, pageable);

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

        User currentTenant = getCurrentUser();

        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (request.getTenant() == null || !request.getTenant().getId().equals(currentTenant.getId())) {
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

    // Récupère l'unique bien loué par ce locataire
    private Property getMyRentedProperty(User currentTenant) {
        return propertyRepository.findByTenantId(currentTenant.getId())
                .orElseThrow(() -> new ForbiddenException("Aucun bien n'est assigné à ce compte locataire"));
    }

    /**
     * Génère une référence unique pour la demande d'intervention. Format : TRV-XXX
     */
    private String genererReference() {
        long totalExistant = interventionRequestRepository.count();
        long prochainNumero = totalExistant + 1;
        return String.format("TRV-%03d", prochainNumero);
    }

    /**
     * Notifie le syndic de la résidence d'une demande de travaux créée par un locataire.
     * Push uniquement si urgent (respecte sa préférence "Incidents urgents"), email systématique.
     */
    private void notifySyndicManualFlow(InterventionRequest request, Residence residence, User tenant) {
        if (residence.getSyndic() == null) {
            return;
        }

        User syndic = residence.getSyndic();
        String tenantName = tenant.getFirstName() + " " + tenant.getLastName();

        if (request.getUrgencyLevel() == UrgencyLevel.URGENT) {
            notificationService.sendUrgentIncidentNotification(
                    syndic.getId(),
                    "Incident urgent signalé",
                    tenantName + " a signalé un problème urgent : " + request.getTitle()
            );
        }

        emailService.sendSyndicInterventionNotification(
                syndic.getEmail(),
                syndic.getFirstName(),
                request.getTitle(),
                residence.getName(),
                tenantName
        );
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
                .selectedProvider(null) // circuit prestataire désactivé pour le flux locataire
                .timeline(buildTimeline(request))
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
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
