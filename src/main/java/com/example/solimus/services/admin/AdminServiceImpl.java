package com.example.solimus.services.admin;

import com.example.solimus.dtos.admin.*;
import com.example.solimus.dtos.residence.CreateSecurityFeatureDTO;
import com.example.solimus.dtos.residence.SecurityFeatureDTO;
import com.example.solimus.entities.EstimatedDelay;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.SecurityFeature;
import com.example.solimus.entities.Specialty;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;

import com.example.solimus.repositories.EstimatedDelayRepository;
import com.example.solimus.repositories.RoleRepository;
import com.example.solimus.repositories.SecurityFeatureRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.ActivationCodeService;
import com.example.solimus.services.auth.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    // ============================================================================
    // DÉPENDANCES
    // ============================================================================

    private final SpecialtyRepository specialtyRepository;
    private final EstimatedDelayRepository estimatedDelayRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ActivationCodeService activationCodeService;
    private final EmailService emailService;
    private final SecurityFeatureRepository securityFeatureRepository;

    // ============================================================================
    // PARTIE 1 — GESTION DES SPÉCIALITÉS
    // ============================================================================
    //
    // Permet à l'administrateur de gérer les métiers disponibles pour les
    // prestataires : plomberie, électricité, nettoyage, maintenance, etc.
    //
    // ============================================================================

    @Override
    public List<SpecialtyDTO> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(s -> new SpecialtyDTO(s.getId(), s.getName(), s.getDescription(), s.getIcon()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SpecialtyDTO createSpecialty(CreateSpecialtyDTO dto) {
        // Empêche la création de deux spécialités portant le même nom
        if (specialtyRepository.existsByName(dto.getName())) {
            throw new BadRequestException("La spécialité '" + dto.getName() + "' existe déjà.");
        }

        Specialty specialty = new Specialty();
        specialty.setName(dto.getName());
        specialty.setDescription(dto.getDescription());
        specialty.setIcon(dto.getIcon());
        Specialty saved = specialtyRepository.save(specialty);
        return new SpecialtyDTO(saved.getId(), saved.getName(), saved.getDescription(), saved.getIcon());
    }

    @Override
    @Transactional
    public SpecialtyDTO updateSpecialty(Long id, CreateSpecialtyDTO dto) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée"));

        // Empêche de renommer avec un nom déjà utilisé par une autre spécialité
        if (specialtyRepository.existsByNameAndIdNot(dto.getName(), id)) {
            throw new BadRequestException("La spécialité '" + dto.getName() + "' existe déjà.");
        }

        specialty.setName(dto.getName());
        specialty.setDescription(dto.getDescription());
        specialty.setIcon(dto.getIcon());
        
        Specialty updated = specialtyRepository.save(specialty);
        return new SpecialtyDTO(updated.getId(), updated.getName(), updated.getDescription(), updated.getIcon());
    }

    @Override
    @Transactional
    public void deleteSpecialty(Long id) {
        if (!specialtyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Spécialité non trouvée");
        }
        specialtyRepository.deleteById(id);
    }


    // ============================================================================
    // PARTIE 2 — GESTION DES DÉLAIS ESTIMÉS
    // ============================================================================
    //
    // Les délais estimés permettent de qualifier le temps prévu pour une
    // intervention : urgence, 24h, 48h, une semaine, etc.
    //
    // ============================================================================

    @Override
    public List<EstimatedDelayDTO> getAllEstimatedDelays() {
        return estimatedDelayRepository.findAll().stream()
                .map(d -> new EstimatedDelayDTO(d.getId(), d.getLabel(), d.getDays()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EstimatedDelayDTO addEstimatedDelay(String label, Integer days) {
        EstimatedDelay delay = EstimatedDelay.builder()
                .label(label)
                .days(days)
                .build();
        EstimatedDelay saved = estimatedDelayRepository.save(delay);
        return new EstimatedDelayDTO(saved.getId(), saved.getLabel(), saved.getDays());
    }

    // ============================================================================
    // PARTIE 3 — CONSULTATION ET STATUT DES UTILISATEURS
    // ============================================================================
    //
    // Regroupe la liste paginée des utilisateurs et la modification de leur statut.
    // Les filtres permettent de rechercher par texte, rôle et statut.
    //
    // ============================================================================

    @Override
    public UserListResponseDTO getUsers(int page, int size, String search, ERole role, UserStatus status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage = userRepository.findAllWithFilters(search, role, status, pageable);

        List<UserListItemDTO> userDTOs = userPage.getContent().stream()
                .map(u -> new UserListItemDTO(
                        u.getId(),
                        u.getFirstName(),
                        u.getLastName(),
                        u.getEmail(),
                        u.getPhone(),
                        u.getRole().getName(),
                        u.getStatus(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new UserListResponseDTO(
                userDTOs,
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.getNumber()
        );
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        user.setStatus(status);
        userRepository.save(user);
    }

    // ============================================================================
    // PARTIE 4 — CRÉATION DE COMPTE UTILISATEUR PAR L'ADMIN
    // ============================================================================
    //
    // Flux de création :
    // 1. L'admin crée un utilisateur sans mot de passe
    // 2. Le compte est enregistré avec le statut PENDING
    // 3. Un token UUID d'activation est généré
    // 4. L'utilisateur reçoit un lien email pour définir son mot de passe
    // 5. Une fois activé, le compte devient utilisable dans l'application
    //
    // ============================================================================

    @Override
    @Transactional
    public CreateUserResponseDTO createUser(CreateUserRequestDTO dto) {

        // ---------------------------------------------------------------------
        // 1. Vérification de l'unicité de l'email et du téléphone
        // ---------------------------------------------------------------------
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Un compte avec cet email existe déjà : " + dto.getEmail());
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new PhoneAlreadyExistsException("Un compte avec ce téléphone existe déjà : " + dto.getPhone());
        }

        // ---------------------------------------------------------------------
        // 2. Récupération du rôle demandé depuis la base
        // ---------------------------------------------------------------------
        Role role = roleRepository.findByName(dto.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Rôle " + dto.getRole() + " introuvable en base"));

        // ---------------------------------------------------------------------
        // 3. Création du compte sans mot de passe
        // ---------------------------------------------------------------------
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setPassword(null);          // Pas de mot de passe à la création
        user.setStatus(UserStatus.PENDING);

        User savedUser = userRepository.save(user);

        // ---------------------------------------------------------------------
        // 4. Génération du token UUID d'activation
        // ---------------------------------------------------------------------
        String activationToken = activationCodeService.generateAndStoreAccountActivationToken(savedUser);

        // ---------------------------------------------------------------------
        // 5. Envoi du lien d'activation par email
        // ---------------------------------------------------------------------
        emailService.sendUserActivationLink(
                savedUser.getEmail(),
                activationToken,
                savedUser.getFirstName()
        );

        // ---------------------------------------------------------------------
        // 6. Réponse retournée à l'admin après création
        // ---------------------------------------------------------------------
        return CreateUserResponseDTO.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .role(dto.getRole())
                .status(savedUser.getStatus())
                .createdAt(savedUser.getCreatedAt())
                .message("Compte " + dto.getRole().getLabel() + " créé avec succès. Un email d'activation a été envoyé à " + savedUser.getEmail() + ".")
                .build();
    }

    // ============================================================================
    // PARTIE 5 - GESTION DES OPTIONS DE SÉCURITÉ
    // ============================================================================

    @Override
    @Transactional
    public void createSecurityFeature(CreateSecurityFeatureDTO dto) {

        // Vérifier si le label existe déjà
        if (securityFeatureRepository.existsByLabel(dto.getLabel())) {
            throw new BadRequestException("Une option de sécurité avec ce label existe déjà");
        }

        SecurityFeature feature = new SecurityFeature();
        feature.setLabel(dto.getLabel());
        feature.setDescription(dto.getDescription());
        feature.setActive(true);

        securityFeatureRepository.save(feature);

        log.info("Option de sécurité '{}' créée par l'admin", dto.getLabel());
    }

    @Override
    public List<SecurityFeatureDTO> getSecurityFeatures() {
        return securityFeatureRepository.findAll()
            .stream()
            .map(this::mapToSecurityFeatureDTO)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deactivateSecurityFeature(Long id) {

        //Vérifier que l'option existe
        SecurityFeature feature = securityFeatureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Option de sécurité introuvable"));

        //si oui, on la désactive
        feature.setActive(false);
        securityFeatureRepository.save(feature);

        log.info("Option de sécurité '{}' désactivée par l'admin", feature.getLabel());
    }

    @Override
    @Transactional
    public void activateSecurityFeature(Long id) {

        //Vérifier que l'option existe
        SecurityFeature feature = securityFeatureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Option de sécurité introuvable"));

        //si oui , on l'active
        feature.setActive(true);
        securityFeatureRepository.save(feature);

        log.info("Option de sécurité '{}' activée par l'admin", feature.getLabel());
    }

    private SecurityFeatureDTO mapToSecurityFeatureDTO(SecurityFeature feature) {
        return SecurityFeatureDTO.builder()
            .id(feature.getId())
            .label(feature.getLabel())
            .description(feature.getDescription())
            .active(feature.isActive())
            .build();
    }

}
