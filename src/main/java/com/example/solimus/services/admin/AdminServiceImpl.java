package com.example.solimus.services.admin;

import com.example.solimus.dtos.admin.*;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanDTO;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanRequestDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;

import com.example.solimus.repositories.*;
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

    private final EstimatedDelayRepository estimatedDelayRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ActivationCodeService activationCodeService;
    private final EmailService emailService;
    private final SecurityFeatureRepository securityFeatureRepository;
    private final ProviderPlanRepository providerPlanRepository;

    // ============================================================================
    // PARTIE — GESTION ABONNEMENT PRESTATAIRE
    // ============================================================================
    //
    // Save transparent : crée la formule si elle n'existe pas encore,
    // sinon met à jour la ligne existante avec les nouvelles valeurs
    // envoyées par l'admin depuis le formulaire.
    //
    // ============================================================================

    @Override
    @Transactional
    public ProviderPlanDTO saveProviderPlan(ProviderPlanRequestDTO dto) {

        // On récupère la ligne existante si elle existe, sinon on en crée une nouvelle
        ProviderPlan plan = providerPlanRepository.findFirstByOrderByIdAsc()
                .orElse(new ProviderPlan());


        // On ne met à jour que les champs réellement envoyés par l'admin.
        if (dto.getName() != null) {
            plan.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            plan.setDescription(dto.getDescription());
        }
        if (dto.getMonthlyPrice() != null) {
            plan.setMonthlyPrice(dto.getMonthlyPrice());
        }
        if (dto.getYearlyPrice() != null) {
            plan.setYearlyPrice(dto.getYearlyPrice());
        }

        ProviderPlan saved = providerPlanRepository.save(plan);

        return toDTO(saved);
    }


    // ============================================================================
    // PARTIE — GESTION DES DÉLAIS ESTIMÉS
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
    // PARTIE — AJOUT , CONSULTATION ET STATUT DES UTILISATEURS
    // ============================================================================
    //
    // Regroupe la liste paginée des utilisateurs et la modification de leur statut.
    // Les filtres permettent de rechercher par texte, rôle et statut.
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
    // Méthodes Utilitaires
    // ============================================================================
    /**
     * Conversion entité → DTO.
     */
    private ProviderPlanDTO toDTO(ProviderPlan plan) {
        return ProviderPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .monthlyPrice(plan.getMonthlyPrice())
                .yearlyPrice(plan.getYearlyPrice())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }




}
