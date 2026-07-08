package com.example.solimus.services.owner.signalement;

import com.example.solimus.dtos.owner.signalement.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
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
public class OwnerSignalementServiceImpl implements OwnerSignalementService {

    private final SignalementRepository signalementRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // CRÉER UN SIGNALEMENT
    // =========================================================================

    @Override
    @Transactional
    public void createSignalement(CreateSignalementDTO dto) {

        // Récupère le copropriétaire actuellement connecté
        User currentOwner = getCurrentUser();

        // Récupère la résidence, erreur si introuvable
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Crée le nouveau signalement et remplit ses informations générales
        Signalement signalement = new Signalement();
        signalement.setReference(genererReference());
        signalement.setTitle(dto.getTitle());
        signalement.setDescription(dto.getDescription());
        signalement.setResidence(residence);
        signalement.setOwner(currentOwner);
        signalement.setUrgencyLevel(dto.getUrgencyLevel());
        signalement.setLocationType(dto.getLocationType());
        signalement.setPhotoUrls(dto.getPhotoUrls() != null ? dto.getPhotoUrls() : new ArrayList<>());

        // Vérifie qu'un seul des deux (appartement OU partie commune) est renseigné
        if (dto.getPropertyId() != null && dto.getCommonFacilityId() != null) {
            throw new BadRequestException("Vous ne pouvez spécifier qu'un seul : un appartement OU un équipement commun");
        }
        if (dto.getPropertyId() == null && dto.getCommonFacilityId() == null) {
            throw new BadRequestException("Vous devez spécifier un appartement OU un équipement commun");
        }

        // Associe l'entité correspondante selon le type de localisation choisi
        if (dto.getLocationType() == IncidentLocationType.APPARTEMENT) {
            Property property = propertyRepository.findById(dto.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appartement introuvable"));
            signalement.setProperty(property);
        } else {
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));
            signalement.setCommonFacility(commonFacility);
        }

        // Trace l'événement initial dans l'historique, statut PENDING dès la création
        signalement.addStatusHistory(SignalementStatus.PENDING, currentOwner, "Signalement envoyé");

        // Sauvegarde le signalement en base
        signalementRepository.save(signalement);
    }

    // =========================================================================
    // LISTER MES SIGNALEMENTS
    // =========================================================================

    @Override
    public Page<SignalementCardDTO> getMySignalements(String search, Long residenceId, SignalementStatus status, int page, int size) {

        // Récupère le copropriétaire actuellement connecté
        User currentOwner = getCurrentUser();

        // Construit la pagination, triée du signalement le plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupère les signalements du copropriétaire, en appliquant les filtres optionnels
        // (recherche par titre, résidence, statut) — chaque filtre est ignoré si non fourni
        Page<Signalement> signalementPage = signalementRepository.searchMySignalements(
                currentOwner.getId(), search, residenceId, status, pageable);

        // Transforme chaque signalement de la page en carte pour l'affichage
        return signalementPage.map(this::buildSignalementCard);
    }

    // =========================================================================
    // DÉTAIL D'UN SIGNALEMENT
    // =========================================================================

    @Override
    public SignalementDetailDTO getSignalementDetail(Long id) {

        // Récupère le copropriétaire actuellement connecté
        User currentOwner = getCurrentUser();

        // Récupère le signalement, erreur si introuvable
        Signalement signalement = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));

        // Vérifie que ce signalement appartient bien au copropriétaire connecté
        if (!signalement.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce signalement");
        }

        // Construit et retourne le détail complet
        return buildSignalementDetail(signalement);
    }
    // =========================================================================
   // UTILITAIRES ET MAPPERS
  // =========================================================================

    // Récupère l'utilisateur actuellement authentifié via le SecurityContext
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    // Construit le libellé de position affiché (appartement ou équipement commun)
    private String buildPositionLabel(Signalement signalement) {
        if (signalement.getLocationType() == IncidentLocationType.APPARTEMENT) {
            // Récupère la référence de l'appartement si renseignée
            return signalement.getProperty() != null ? "Appartement " + signalement.getProperty().getReference() : "";
        } else {
            // Récupère le nom du type d'équipement commun si renseigné
            return signalement.getCommonFacility() != null && signalement.getCommonFacility().getFacilityType() != null
                    ? signalement.getCommonFacility().getFacilityType().getName() : "";
        }
    }

    // Construit une carte de signalement pour la liste
    private SignalementCardDTO buildSignalementCard(Signalement signalement) {
        return SignalementCardDTO.builder()
                .id(signalement.getId())
                .title(signalement.getTitle())
                .positionLabel(buildPositionLabel(signalement))
                .createdAt(signalement.getCreatedAt())
                .urgencyLevel(signalement.getUrgencyLevel().name())
                .status(signalement.getStatus().getLabel())
                .build();
    }

    // Construit le détail complet d'un signalement (réutilisable owner + syndic)
    private SignalementDetailDTO buildSignalementDetail(Signalement signalement) {

        // Transforme chaque entrée de l'historique en DTO
        List<SignalementHistoryItemDTO> historyDtos = signalement.getHistory().stream()
                .map(h -> SignalementHistoryItemDTO.builder()
                        .status(h.getStatus().getLabel())
                        .label(h.getNote())
                        .changedByName(h.getChangedBy() != null
                                ? h.getChangedBy().getFirstName() + " " + h.getChangedBy().getLastName() : null)
                        .date(h.getCreatedAt())
                        .build())
                .toList();

        // Construit le DTO de détail complet
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
                .photoUrls(signalement.getPhotoUrls())
                .declaredByName(signalement.getOwner().getFirstName() + " " + signalement.getOwner().getLastName())
                .closingNote(signalement.getClosingNote())
                .history(historyDtos)
                .build();
    }

    // Génère une référence unique de type SIG-2026-001
    private String genererReference() {
        // Compte le nombre total de signalements déjà existants en base
        long totalExistant = signalementRepository.count();
        // Ajoute 1 pour obtenir le numéro du prochain signalement
        long prochainNumero = totalExistant + 1;
        // Formate avec l'année en cours + le numéro sur 3 chiffres minimum
        return String.format("SIG-%d-%03d", LocalDate.now().getYear(), prochainNumero);
    }
}



