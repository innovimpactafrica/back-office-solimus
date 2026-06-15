package com.example.solimus.services.syndic;

import com.example.solimus.dtos.owner.CoOwnerPropertyAssignmentDTO;
import com.example.solimus.dtos.owner.CreateCoOwnerDTO;
import com.example.solimus.dtos.owner.PropertySummaryDTO;
import com.example.solimus.dtos.owner.ResidenceSummaryDTO;
import com.example.solimus.entities.CoOwnerProfile;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.PropertyStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.ActivationCodeService;
import com.example.solimus.services.auth.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ActivationCodeService activationCodeService;
    private final EmailService emailService;

    //-------------------------------------------
    // Ajouter un copropriétaire
    //-----------------------------------------
    @Override
    @Transactional
    public void addCoOwner(CreateCoOwnerDTO dto) {

        // Vérifications préliminaires
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BadRequestException("Email déjà utilisé.");
        }

        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new BadRequestException("Numéro de téléphone déjà utilisé.");
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

        User saved = userRepository.save(user);

        // Création du CoOwnerProfile avec les infos complémentaires
        CoOwnerProfile profile = new CoOwnerProfile();
        profile.setUser(saved);
        profile.setTitle(dto.getTitle());
        profile.setBirthDate(dto.getBirthDate());
        profile.setNationality(dto.getNationality());
        profile.setSecondaryPhone(dto.getSecondaryPhone());
        profile.setAddress(dto.getAddress());
        profile.setPhotoUrl(dto.getPhotoUrl());
        coOwnerProfileRepository.save(profile);

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

    //------------------------------------------
    //Lister les biens vacants d'une résidence 
    //------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public List<PropertySummaryDTO> getAvailableProperties(Long residenceId) {
        // Retourne uniquement les biens VACANT de cette résidence
        return propertyRepository
                .findByResidenceIdAndStatus(residenceId, PropertyStatus.VACANT)
                .stream()
                .map(this::mapToPropertySummaryDTO)
                .collect(Collectors.toList());
    }

    //-------------------------------------------------------
    //Lister les résidences qui ont au moins un bien Vaccant
    //-------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public List<ResidenceSummaryDTO> getResidencesWithVacantProperties() {
        return residenceRepository
                .findResidencesWithVacantProperties()
                .stream()
                .map(this::mapToResidenceSummaryDTO)
                .collect(Collectors.toList());
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
}
