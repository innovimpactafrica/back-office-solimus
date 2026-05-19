package com.example.solimus.services.admin;

import com.example.solimus.dtos.admin.*;
import com.example.solimus.entities.EstimatedDelay;
import com.example.solimus.entities.Role;
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
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.entities.EstimatedDelay;
import com.example.solimus.services.auth.ActivationCodeService;
import com.example.solimus.services.auth.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SpecialtyRepository specialtyRepository;
    private final EstimatedDelayRepository estimatedDelayRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ActivationCodeService activationCodeService;
    private final EmailService emailService;

    // ============================================================================
    // 🛠 GESTION DES SPÉCIALITÉS
    // ============================================================================

    @Override
    public List<SpecialtyDTO> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(s -> new SpecialtyDTO(s.getId(), s.getName(), s.getDescription()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SpecialtyDTO createSpecialty(CreateSpecialtyDTO dto) {
        // Vérifier l'unicité du nom
        if (specialtyRepository.existsByName(dto.getName())) {
            throw new BadRequestException("La spécialité '" + dto.getName() + "' existe déjà.");
        }

        Specialty specialty = new Specialty();
        specialty.setName(dto.getName());
        specialty.setDescription(dto.getDescription());
        Specialty saved = specialtyRepository.save(specialty);
        return new SpecialtyDTO(saved.getId(), saved.getName(), saved.getDescription());
    }

    @Override
    @Transactional
    public SpecialtyDTO updateSpecialty(Long id, CreateSpecialtyDTO dto) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée"));
        
        specialty.setName(dto.getName());
        specialty.setDescription(dto.getDescription());
        
        Specialty updated = specialtyRepository.save(specialty);
        return new SpecialtyDTO(updated.getId(), updated.getName(), updated.getDescription());
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
    // ⏳ GESTION DES DÉLAIS
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
    // 👤 GESTION DES UTILISATEURS
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
                        u.getCompanyName(),
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
    // 💼 CRÉATION DE COMPTE UTILISATEUR (par ADMIN)
    // ============================================================================

    @Override
    @Transactional
    public CreateUserResponseDTO createUser(CreateUserRequestDTO dto) {

        // 1. Vérifications de duplication
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Un compte avec cet email existe déjà : " + dto.getEmail());
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new PhoneAlreadyExistsException("Un compte avec ce téléphone existe déjà : " + dto.getPhone());
        }

        // 2. Récupérer le rôle depuis le DTO
        Role role = roleRepository.findByName(dto.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Rôle " + dto.getRole() + " introuvable en base"));

        // 3. Créer l'utilisateur sans mot de passe, statut PENDING
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setPassword(null);          // Pas de mot de passe à la création
        user.setStatus(UserStatus.PENDING);

        User savedUser = userRepository.save(user);

        // 4. Générer un token UUID sécurisé (expire dans 60 minutes)
        String activationToken = activationCodeService.generateAndStoreAccountActivationToken(savedUser);

        // 5. Envoyer l'email d'activation
        emailService.sendUserActivationLink(
                savedUser.getEmail(),
                activationToken,
                savedUser.getFirstName()
        );

        // 6. Retourner la réponse avec confirmation
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
}
