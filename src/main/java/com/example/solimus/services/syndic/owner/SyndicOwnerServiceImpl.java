package com.example.solimus.services.syndic.owner;

import com.example.solimus.dtos.syndic.owner.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.PropertyStatus;
import com.example.solimus.enums.UserStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

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


    //----------------------------------------------------------------------
    // Autocomplete — recherche un copropriétaire par nom, email ou téléphone
    //-----------------------------------------------------------------------
    @Override
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
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerListDTO> getCoOwners(String search, Long residenceId, Pageable pageable) {

        //Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        //Retourner la liste des copropriétaires liés à ce syndic via SyndicCoOwnerRelation
        //Uniquement ceux qui ont au moins un bien (car pas de lots = pas propriétaire)
        return syndicCoOwnerRelationRepository
                .findCoOwnersWithPropertiesBySyndicId(currentSyndic.getId(), pageable)
                .map(relation -> mapToCoOwnerListDTO(relation.getCoOwner()));
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

    private CoOwnerListDTO mapToCoOwnerListDTO(User user) {

        // Récupérer les propriétés du copropriétaire pour avoir le nombre de biens
        List<Property> properties = propertyRepository.findAllByOwnerId(user.getId());

        // Construire le DTO
        return CoOwnerListDTO.builder()
                .id(user.getId())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .photoUrl(user.getProfilePhotoUrl())
                .email(user.getEmail())
                .phone(user.getPhone())
                .propertyCount(properties.size())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
