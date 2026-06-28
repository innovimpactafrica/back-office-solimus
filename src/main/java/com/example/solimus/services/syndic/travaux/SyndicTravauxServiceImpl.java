package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.travaux.CreateInterventionRequestDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicTravauxServiceImpl implements SyndicTravauxService {

    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final InterventionRequestRepository interventionRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GeolocationService geolocationService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;

    @Value("${solimus.geolocation.search-radius-km:30.0}")
    private double searchRadiusKm;//Rayon de recherche des prestataires
    @Value("${provider.gps.freshness-minutes:60}")
    private int gpsFreshnessMinutes;// Durée de validité d'une localisation

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SyndicResidenceDTO> getMesResidences() {
        // Récupérer le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupérer toutes les résidences qui appartiennent à ce syndic
        return residenceRepository.findAllBySyndicId(currentSyndic.getId()).stream()
                // Pour chaque résidence, on ne retourne que l'id et le nom
                .map(r -> SyndicResidenceDTO.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .build())
                .toList();
    }

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PropertyDTO> getPropertiesByResidence(Long residenceId) {
        // Récupérer le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Récupérer tous les lots de cette résidence
        return propertyRepository.findByResidenceId(residenceId).stream()
                .map(this::mapToPropertyDTO)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId) {
        // Récupérer le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Récupérer tous les biens communs de cette résidence
        return commonFacilityRepository.findByResidenceId(residenceId).stream()
                .map(cf -> CommonFacilityDTO.builder()
                        .id(cf.getId())
                        .label(cf.getFacilityType().getName())
                        .build())
                .toList();
    }

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyDTO> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(specialty -> SpecialtyDTO.builder()
                        .id(specialty.getId())
                        .name(specialty.getName())
                        .description(specialty.getDescription())
                        .icon(specialty.getIcon())
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================

    /**
     * Crée une demande d'intervention et notifie les prestataires.
     */
    @Override
    @Transactional
    public void createInterventionRequest(CreateInterventionRequestDTO dto) {
        User currentSyndic = getCurrentUser(); //Récupérer le syndic actuel

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à créer une intervention pour cette résidence");
        }

        // Récupérer la spécialité
        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        // Créer la demande d'intervention
        InterventionRequest request = new InterventionRequest();
        request.setReference(genererReference()); //Générer et assigner une référence unique (ex: TRV-001) à la demande
        request.setTitle(dto.getTitle()); // Définir le titre
        request.setDescription(dto.getDescription()); // Définir la description
        request.addStatusHistory(InterventionStatus.PENDING, currentSyndic); // Ajouter l'historique de statut
        request.setInitiatedBy(InitiatedBy.SYNDIC); // Initié par le syndic
        request.setSyndic(currentSyndic); // Définir le syndic
        request.setResidence(residence); // Définir la résidence
        request.setSpecialty(specialty); // Définir la spécialité
        request.setPhotoUrls(dto.getPhotoUrls()); // Définir les URLs des photos
        request.setUrgencyLevel(dto.getUrgencyLevel()); // Définir le niveau d'urgence
        request.setLocationType(IncidentLocationType.PARTIE_COMMUNE); // Pour le syndic, c'est toujours une partie commune
        request.setManagementMode(InterventionManagementMode.SYNDIC); // Mode de gestion : géré par le syndic

        // Pour le syndic, seul les parties communes sont gérées
        if (dto.getPropertyId() != null) {
            throw new BadRequestException("Pour une demande de travaux du syndic, aucun bien ne doit être spécifié");
        }
        if (dto.getCommonFacilityId() == null) {
            throw new BadRequestException("Pour une demande de travaux du syndic, l'équipement commun concerné doit être spécifié");
        }

        // Récupérer et vérifier l'équipement commun
        CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));

        // Vérifier que l'équipement appartient à la résidence
        if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(dto.getResidenceId())) {
            throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
        }

        request.setProperty(null);
        request.setCommonFacility(commonFacility);

        // Diffusion automatique aux prestataires proches
        if (residence.getLatitude() == null || residence.getLongitude() == null) {
            throw new BadRequestException("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
        }

        // Récupérer les prestataires proches
        // Étape 1 — on récupère juste les prestataires actifs pour cette spécialité pas de calcul d'abord
        List<ProviderProfile> candidates = providerProfileRepository
                .findActiveProvidersBySpecialty(specialty.getId());

        // Étape 2 — on ne garde que les prestataires dont l'abonnement est actuellement actif (status ACTIVE ET date de fin non dépassée)
        List<ProviderProfile> abonnesActifs = candidates.stream()
                .filter(profile -> subscriptionRepository
                        // pour chaque prestataire, on va chercher son dernier abonnement
                        .findFirstByProviderIdOrderByEndDateDesc(profile.getUser().getId())
                        // s'il en a un, on vérifie qu'il est encore valide en ce moment
                        .map(Subscription::isCurrentlyActive)
                        // s'il n'a jamais eu d'abonnement, on le considère comme non actif
                        .orElse(false))
                .toList();

        // Étape 3 — on calcule la distance entre la résidence et chaque prestataire
        // abonné actif, et on ne garde que ceux dans le rayon autorisé (30km)
        List<ProviderProfile> candidatsProches = new ArrayList<>();

        // On calcule d'abord le seuil de fraîcheur GPS une seule fois,
        // pour ne pas le recalculer à chaque tour de boucle
        LocalDateTime seuilFraicheur = LocalDateTime.now().minusMinutes(gpsFreshnessMinutes);

        for (ProviderProfile profile : abonnesActifs) {
            // On vérifie si ce prestataire a une position GPS récente et valide
            boolean gpsValide = profile.getGpsLatitude() != null
                    && profile.getGpsLongitude() != null
                    && profile.getGpsUpdatedAt() != null
                    && profile.getGpsUpdatedAt().isAfter(seuilFraicheur);

            // Si le GPS est valide, on l'utilise ; sinon on retombe sur la zone de référence saisie à l'inscription
            double providerLat = gpsValide
                    ? profile.getGpsLatitude().doubleValue()
                    : profile.getLatitude().doubleValue();

            double providerLon = gpsValide
                    ? profile.getGpsLongitude().doubleValue()
                    : profile.getLongitude().doubleValue();

            // On calcule la distance réelle entre la résidence et ce prestataire
            double distance = geolocationService.calculateDistance(
                    residence.getLatitude().doubleValue(),
                    residence.getLongitude().doubleValue(),
                    providerLat,
                    providerLon
            );

            // On ne garde que ceux qui sont à 30km ou moins (searchRadiusKm)
            if (distance <= searchRadiusKm) {
                // On stocke le prestataire
                candidatsProches.add(profile);
            }
        }

        // Étape 4 — on notifie chaque prestataire trouvé, et on les enregistre dans la liste des prestataires notifiés pour cette demande
        for (ProviderProfile profil : candidatsProches) {

            User user = profil.getUser();

            // on envoie la notification push Firebase et l'email si l'utilisateur a activé les notifications
            if (user.isNotificationsEnabled()) {
                notificationService.sendPush(
                        user.getId(), // id de l'utilisateur cible
                        "Nouvelle demande de travaux", // titre visible sur le téléphone
                        "Une nouvelle demande correspond à votre spécialité : " + request.getTitle() // corps du message
                );

                // On envoie l'email de notification
                emailService.sendInterventionNotification(
                        user.getEmail(),
                        user.getFirstName(),
                        request.getTitle(),
                        residence.getName()
                );
            }

            // On garde une trace : ce prestataire fait partie de ceux notifiés
            // pour cette demande précise — utile  pour savoir qui a le droit de soumettre un devis dessus
            request.getNotifiedProviders().add(user);
        }

        //On sauvegarde la demande
        interventionRepository.save(request);
    }

    // =========================================================================
    // CRÉATION D'AVIS
    // =========================================================================

    @Override
    @Transactional
    public void createReview(Long interventionId, CreateReviewDTO dto) {
        User currentSyndic = getCurrentUser();

        // 1. Récupérer l'intervention
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // 2. Vérifier que l'intervention appartient au syndic
        if (request.getSyndic() == null || !request.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à laisser un avis sur cette intervention");
        }

        // 3. Vérifier que l'intervention est terminée
        if (request.getStatus() != InterventionStatus.FINISHED && request.getStatus() != InterventionStatus.FINAL_VALIDATION) {
            throw new BadRequestException("L'intervention doit être terminée pour laisser un avis");
        }

        // 4. Vérifier qu'un prestataire a été sélectionné
        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire sélectionné pour cette intervention");
        }

        // 5. Vérifier qu'un avis n'existe pas déjà
        if (reviewRepository.existsByInterventionRequestId(interventionId)) {
            throw new BadRequestException("Un avis a déjà été laissé pour cette intervention");
        }

        // 6. Créer l'avis
        Review review = new Review();
        review.setInterventionRequest(request);
        review.setReviewer(currentSyndic);
        review.setProvider(request.getSelectedProvider());
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        reviewRepository.save(review);
    }

    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    /**
     * Génère une référence unique de type TRV-001 etc
     * On compte le nombre total de demandes en base, on ajoute 1, puis on formate.
     */
    private String genererReference() {
        // On compte le nombre total de demandes déjà existantes en base
        long totalExistant = interventionRepository.count();
        // On ajoute 1 pour obtenir le numéro de la prochaine demande
        long prochainNumero = totalExistant + 1;
        // On formate en "TRV-" suivi d'au minimum 3 chiffres (ex: TRV-001, TRV-010, TRV-1000)
        return String.format("TRV-%03d", prochainNumero);
    }

    /**
     * Récupère l'utilisateur actuellement authentifié via le SecurityContext.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    /**
     * Mappe une entité Property vers un PropertyDTO.
     */
    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .superficie(property.getSuperficie())
                .typeName(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                .residenceId(property.getResidence().getId())
                .residenceName(property.getResidence().getName())
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }
}





