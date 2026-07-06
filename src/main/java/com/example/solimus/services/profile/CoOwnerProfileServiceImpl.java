package com.example.solimus.services.profile;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementDetailDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementSummaryDTO;
import com.example.solimus.dtos.owner.travaux.OwnerTimelineStepDTO;
import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementEventType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerProfileServiceImpl implements CoOwnerProfileService {

    private final UserRepository userRepository;
    private final MinioService minioService;
    private final SignalementRepository signalementRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final CommonFacilityRepository commonFacilityRepository;

    @Override
    @Transactional(readOnly = true)
    public CoOwnerProfileDTO getProfile() {
        User currentUser = getCurrentUser();

        return CoOwnerProfileDTO.builder()
                .firstName(currentUser.getFirstName())
                .lastName(currentUser.getLastName())
                .phone(currentUser.getPhone())
                .email(currentUser.getEmail())
                .photoUrl(currentUser.getProfilePhotoUrl())
                .memberSince(currentUser.getCreatedAt() != null ? currentUser.getCreatedAt().toLocalDate() : null)
                .build();
    }

    @Override
    @Transactional
    public CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto, MultipartFile photo) {
        User currentUser = getCurrentUser();

        if (dto.getFirstName() != null) currentUser.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) currentUser.setLastName(dto.getLastName());
        if (dto.getPhone() != null) currentUser.setPhone(dto.getPhone());

        if (photo != null && !photo.isEmpty()) {
            try {
                String photoUrl = minioService.uploadFile(photo, "profiles");
                if (photoUrl != null) {
                    currentUser.setProfilePhotoUrl(photoUrl);
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de la photo de profil", e);
                throw new RuntimeException("Erreur lors de l'upload de la photo de profil");
            }
        }

        userRepository.save(currentUser);
        log.info("Profil mis à jour pour l'utilisateur {}", currentUser.getEmail());

        return getProfile();
    }

    @Override
    @Transactional
    public void createSignalement(CreateSignalementDTO dto, MultipartFile[] photos) {
        User currentOwner = getCurrentUser();

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le copropriétaire a au moins un bien dans cette résidence
        if (!propertyRepository.existsByOwnerIdAndResidenceId(currentOwner.getId(), dto.getResidenceId())) {
            throw new ForbiddenException("Vous n'avez pas de bien dans cette résidence");
        }

        // Validation locationType : un seul de propertyId ou commonFacilityId doit être rempli
        if (dto.getLocationType() == IncidentLocationType.APPARTEMENT) {
            if (dto.getPropertyId() == null) {
                throw new BadRequestException("propertyId est obligatoire pour APPARTEMENT");
            }
            if (dto.getCommonFacilityId() != null) {
                throw new BadRequestException("commonFacilityId ne doit pas être rempli pour APPARTEMENT");
            }
        } else if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            if (dto.getCommonFacilityId() == null) {
                throw new BadRequestException("commonFacilityId est obligatoire pour PARTIE_COMMUNE");
            }
            if (dto.getPropertyId() != null) {
                throw new BadRequestException("propertyId ne doit pas être rempli pour PARTIE_COMMUNE");
            }
        }

        // Upload des photos vers MinIO
        List<String> photoUrls = new ArrayList<>();
        if (photos != null && photos.length > 0) {
            for (MultipartFile photo : photos) {
                if (!photo.isEmpty()) {
                    try {
                        String url = minioService.uploadFile(photo, "signalements");
                        if (url != null) {
                            photoUrls.add(url);
                        }
                    } catch (Exception e) {
                        log.error("Erreur upload photo signalement", e);
                        throw new RuntimeException("Erreur lors de l'upload des photos");
                    }
                }
            }
        }

        // Créer le signalement
        Signalement signalement = new Signalement();
        signalement.setReference(generateReference());
        signalement.setTitle(dto.getTitle());
        signalement.setDescription(dto.getDescription());
        signalement.setStatus(SignalementStatus.PENDING);
        signalement.setUrgencyLevel(dto.getUrgencyLevel());
        signalement.setLocationType(dto.getLocationType());
        signalement.setResidence(residence);
        signalement.setDeclaredBy(currentOwner);

        // Set property ou commonFacility selon locationType
        if (dto.getLocationType() == IncidentLocationType.APPARTEMENT) {
            Property property = propertyRepository.findById(dto.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));
            signalement.setProperty(property);
        } else if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Partie commune introuvable"));
            signalement.setCommonFacility(commonFacility);
        }

        // Photo URLs
        if (!photoUrls.isEmpty()) {
            signalement.setPhotoUrls(photoUrls);
        }

        // Initialiser l'historique — statut PENDING
        signalement.addHistorique(SignalementEventType.CREATION, null, currentOwner);

        // Sauvegarder
        signalementRepository.save(signalement);
        log.info("Signalement créé par {} pour la résidence {}", currentOwner.getEmail(), residence.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerSignalementDTO getMySignalements(
            String search, SignalementStatus status, Long residenceId, int page, int size) {

        User currentOwner = getCurrentUser();

        long totalSignalements = signalementRepository.countByDeclaredBy(currentOwner, residenceId);
        long enAttenteCount = signalementRepository.countByDeclaredByAndStatus(
                currentOwner, SignalementStatus.PENDING, residenceId);

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Signalement> signalementsPage = signalementRepository
                .findByDeclaredByWithFiltersAndResidence(currentOwner, normalizedSearch, status, residenceId, pageable);

        Page<OwnerSignalementSummaryDTO> dtoPage = signalementsPage.map(this::mapToSummaryDTO);

        return OwnerSignalementDTO.builder()
                .totalSignalements(totalSignalements)
                .enAttenteCount(enAttenteCount)
                .signalements(dtoPage)
                .build();
    }

    private OwnerSignalementSummaryDTO mapToSummaryDTO(Signalement signalement) {
        return OwnerSignalementSummaryDTO.builder()
                .id(signalement.getId())
                .reference(signalement.getReference())
                .title(signalement.getTitle())
                .residenceName(signalement.getResidence() != null ? signalement.getResidence().getName() : null)
                .propertyReference(signalement.getProperty() != null ? signalement.getProperty().getReference() : null)
                .commonFacilityName(signalement.getCommonFacility() != null ? signalement.getCommonFacility().getFacilityType().getName() : null)
                .status(signalement.getStatus())
                .statusLabel(signalement.getStatus() != null ? signalement.getStatus().name() : null)
                .urgencyLevel(signalement.getUrgencyLevel())
                .urgencyLabel(signalement.getUrgencyLevel() != null ? signalement.getUrgencyLevel().name() : null)
                .locationType(signalement.getLocationType())
                .createdAt(signalement.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerSignalementDetailDTO getSignalementDetail(Long id) {
        User currentOwner = getCurrentUser();

        Signalement signalement = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        // Vérifier que le signalement appartient au copropriétaire
        if (!signalement.getDeclaredBy().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à voir ce signalement");
        }

        return mapToDetailDTO(signalement);
    }

    private OwnerSignalementDetailDTO mapToDetailDTO(Signalement signalement) {
        List<OwnerTimelineStepDTO> timeline = new ArrayList<>();
        if (signalement.getHistorique() != null) {
            for (SignalementHistorique h : signalement.getHistorique()) {
                OwnerTimelineStepDTO step = OwnerTimelineStepDTO.builder()
                        .type(h.getTypeEvenement().name())
                        .date(h.getCreatedAt())
                        //.author(h.getAuteur() != null ? h.getAuteur().getFirstName() + " " + h.getAuteur().getLastName() : null)
                        //.comment(h.getCommentaire())
                        .build();
                timeline.add(step);
            }
        }

        return OwnerSignalementDetailDTO.builder()
                .id(signalement.getId())
                .reference(signalement.getReference())
                .title(signalement.getTitle())
                .residenceName(signalement.getResidence() != null ? signalement.getResidence().getName() : null)
                .propertyReference(signalement.getProperty() != null ? signalement.getProperty().getReference() : null)
                .commonFacilityName(signalement.getCommonFacility() != null ? signalement.getCommonFacility().getFacilityType().getName() : null)
                .locationType(signalement.getLocationType())
                .status(signalement.getStatus())
                .statusLabel(signalement.getStatus() != null ? signalement.getStatus().getLabel() : null)
                .urgencyLevel(signalement.getUrgencyLevel())
                .urgencyLabel(signalement.getUrgencyLevel() != null ? signalement.getUrgencyLevel().name() : null)
                .description(signalement.getDescription())
                .photoUrls(signalement.getPhotoUrls())
                .createdAt(signalement.getCreatedAt())
                .timeline(timeline)
                .build();
    }

    private String generateReference() {
        int year = Year.now().getValue();
        long count = signalementRepository.count() + 1;
        return "SIG-" + year + "-" + String.format("%03d", count);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
