package com.example.solimus.services.tenant.signalement;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.SignalementCardDTO;
import com.example.solimus.dtos.owner.signalement.SignalementDetailDTO;
import com.example.solimus.dtos.owner.signalement.SignalementHistoryItemDTO;
import com.example.solimus.dtos.tenant.TenantPropertyInfoDTO;
import com.example.solimus.entities.CommonFacility;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.Signalement;
import com.example.solimus.entities.User;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CommonFacilityRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.SignalementRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantSignalementServiceImpl implements TenantSignalementService {

    private final SignalementRepository signalementRepository;
    private final PropertyRepository propertyRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // =========================================================================
    // CRÉER UN SIGNALEMENT
    // =========================================================================

    @Override
    @Transactional
    public void createSignalement(CreateSignalementDTO dto) {

        // Récupère le locataire connecté et son bien loué (un seul bien par locataire)
        User currentTenant = getCurrentUser();
        Property property = getMyProperty(currentTenant);
        Residence residence = property.getResidence();

        // Crée le nouveau signalement et remplit ses informations générales
        Signalement signalement = new Signalement();
        signalement.setReference(genererReference());
        signalement.setTitle(dto.getTitle());
        signalement.setDescription(dto.getDescription());
        signalement.setResidence(residence);
        signalement.setOwner(property.getOwner());
        signalement.setTenant(currentTenant);
        signalement.setUrgencyLevel(dto.getUrgencyLevel());
        signalement.setLocationType(dto.getLocationType());
        signalement.setPhotoUrls(dto.getPhotoUrls() != null ? dto.getPhotoUrls() : new ArrayList<>());

        // Associe l'entité correspondante selon le type de localisation choisi
        if (dto.getLocationType() == IncidentLocationType.APPARTEMENT) {
            if (dto.getCommonFacilityId() != null) {
                throw new BadRequestException("commonFacilityId ne doit pas être fourni lorsque locationType est APPARTEMENT");
            }
            signalement.setProperty(property);
        } else if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            if (dto.getCommonFacilityId() == null) {
                throw new BadRequestException("commonFacilityId est obligatoire lorsque locationType est PARTIE_COMMUNE");
            }
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));

            // Vérifie que l'équipement appartient bien à la résidence du locataire
            if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(residence.getId())) {
                throw new BadRequestException("Cet équipement commun n'appartient pas à votre résidence");
            }
            signalement.setCommonFacility(commonFacility);
        }

        // Trace l'événement initial dans l'historique, statut PENDING dès la création
        signalement.addStatusHistory(SignalementStatus.PENDING, currentTenant, "Signalement envoyé");

        // Sauvegarde le signalement en base
        signalementRepository.save(signalement);

        // Notifie toujours le propriétaire (le bien reste le sien, même s'il ne l'occupe pas)
        if (property.getOwner().isNotificationsEnabled()) {
            notificationService.sendPush(
                    property.getOwner().getId(),
                    "Signalement de votre locataire",
                    currentTenant.getFirstName() + " a signalé : " + signalement.getTitle()
            );
        }

        // Alerte le syndic si le signalement est urgent (respecte sa préférence "Incidents urgents")
        if (signalement.getUrgencyLevel() == UrgencyLevel.URGENT && residence.getSyndic() != null) {
            notificationService.sendUrgentIncidentNotification(
                    residence.getSyndic().getId(),
                    "Incident urgent signalé",
                    currentTenant.getFirstName() + " " + currentTenant.getLastName() +
                            " a signalé un problème urgent : " + signalement.getTitle()
            );
        }
    }

    // =========================================================================
    // LISTER MES SIGNALEMENTS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<SignalementCardDTO> getMySignalements(String search, SignalementStatus status, int page, int size) {

        User currentTenant = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Signalement> signalementPage = signalementRepository.searchMyTenantSignalements(
                currentTenant.getId(), search, status, pageable);

        return signalementPage.map(this::buildSignalementCard);
    }

    // =========================================================================
    // DÉTAIL D'UN SIGNALEMENT
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SignalementDetailDTO getSignalementDetail(Long id) {

        User currentTenant = getCurrentUser();

        Signalement signalement = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        // Vérifie que ce signalement a bien été créé par le locataire connecté
        if (signalement.getTenant() == null || !signalement.getTenant().getId().equals(currentTenant.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce signalement");
        }

        return buildSignalementDetail(signalement);
    }

    // =========================================================================
    // MON BIEN LOUÉ
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public TenantPropertyInfoDTO getMyProperty() {
        User currentTenant = getCurrentUser();
        Property property = getMyProperty(currentTenant);

        return TenantPropertyInfoDTO.builder()
                .propertyReference(property.getReference())
                .residenceName(property.getResidence().getName())
                .build();
    }

    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    // Récupère l'unique bien loué par ce locataire
    private Property getMyProperty(User currentTenant) {
        return propertyRepository.findByTenantId(currentTenant.getId())
                .orElseThrow(() -> new ForbiddenException("Aucun bien n'est assigné à ce compte locataire"));
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

    // Construit une carte de signalement pour la liste
    private SignalementCardDTO buildSignalementCard(Signalement signalement) {
        List<String> photoUrls = signalement.getPhotoUrls() != null
                ? signalement.getPhotoUrls()
                : new ArrayList<>();

        return SignalementCardDTO.builder()
                .id(signalement.getId())
                .title(signalement.getTitle())
                .positionLabel(buildPositionLabel(signalement))
                .createdAt(signalement.getCreatedAt())
                .urgencyLevel(signalement.getUrgencyLevel().name())
                .status(signalement.getStatus().getLabel())
                .photoUrls(photoUrls)
                .fromTenant(true)
                .tenantName(signalement.getTenant().getFirstName() + " " + signalement.getTenant().getLastName())
                .build();
    }

    // Construit le détail complet d'un signalement
    private SignalementDetailDTO buildSignalementDetail(Signalement signalement) {

        List<SignalementHistoryItemDTO> historyDtos = signalement.getHistory().stream()
                .map(h -> SignalementHistoryItemDTO.builder()
                        .status(h.getStatus().getLabel())
                        .label(h.getNote())
                        .changedByName(h.getChangedBy() != null
                                ? h.getChangedBy().getFirstName() + " " + h.getChangedBy().getLastName() : null)
                        .date(h.getCreatedAt())
                        .build())
                .toList();

        List<String> photoUrls = signalement.getPhotoUrls() != null
                ? signalement.getPhotoUrls()
                : new ArrayList<>();

        return SignalementDetailDTO.builder()
                .id(signalement.getId())
                .reference(signalement.getReference())
                .title(signalement.getTitle())
                .residenceName(signalement.getResidence().getName())
                .positionLabel(buildPositionLabel(signalement))
                .createdAt(signalement.getCreatedAt())
                .urgencyLevel(signalement.getUrgencyLevel().name())
                .status(signalement.getStatus().getLabel())
                .description(signalement.getDescription())
                .photoUrls(photoUrls)
                .declaredByName(signalement.getOwner().getFirstName() + " " + signalement.getOwner().getLastName())
                .closingNote(signalement.getClosingNote())
                .history(historyDtos)
                .fromTenant(true)
                .tenantName(signalement.getTenant().getFirstName() + " " + signalement.getTenant().getLastName())
                .build();
    }

    // Génère une référence unique de type SIG-2026-001
    private String genererReference() {
        long totalExistant = signalementRepository.count();
        long prochainNumero = totalExistant + 1;
        return String.format("SIG-%d-%03d", LocalDate.now().getYear(), prochainNumero);
    }
}
