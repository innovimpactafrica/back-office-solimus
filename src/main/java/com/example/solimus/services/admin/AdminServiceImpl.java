package com.example.solimus.services.admin;

import com.example.solimus.dtos.admin.*;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanDTO;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanRequestDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;

import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.ActivationCodeService;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final MinioService minioService;
    private final NotificationService notificationService;

    // ============================================================================
    // PARTIE — GESTION DES RETRAITS WALLET
    // ============================================================================

    /**
     * Valide une demande de retrait wallet après paiement réel du demandeur.
     */
    @Override
    @Transactional
    public WithdrawalRequestDTO approveWalletWithdrawal(Long withdrawalId, MultipartFile receipt, String comment) {

        // 1. Récupérer l'admin connecté pour l'enregistrer comme personne ayant traité la demande
        User currentAdmin = getCurrentAdmin();

        // 2. Récupérer la demande de retrait par son id
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        // 3. Vérifier que la demande est encore en attente
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new BadRequestException("Cette demande de retrait a déjà été traitée");
        }

        // 4. Récupérer le wallet du prestataire concerné par la demande
        ProviderWallet wallet = walletRepository.findByProviderId(withdrawal.getProvider().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet prestataire introuvable"));

        // 5. Vérifier que le montant demandé est bien disponible dans le solde en attente
        if (wallet.getPendingBalance().compareTo(withdrawal.getAmount()) < 0) {
            throw new BadRequestException("Solde en attente insuffisant pour valider ce retrait");
        }

        // 6. Vérifier que le reçu de paiement est bien fourni par l'admin
        if (receipt == null || receipt.isEmpty()) {
            throw new BadRequestException("Le reçu de paiement est obligatoire");
        }

        // 7. Uploader le reçu de paiement dans MinIO
        String receiptUrl = minioService.uploadFile(receipt, "wallet-withdrawals");

        // 8. Déduire le montant du solde en attente uniquement
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(withdrawal.getAmount()));

        // 9. Ne pas toucher au solde disponible car il a déjà été diminué lors de la demande
        walletRepository.save(wallet);

        // 10. Marquer la demande comme payée/validée
        withdrawal.setStatus(WithdrawalStatus.COMPLETED);

        // 11. Enregistrer la date de traitement
        withdrawal.setProcessedAt(LocalDateTime.now());

        // 12. Enregistrer le reçu uploadé
        withdrawal.setReceiptUrl(receiptUrl);

        // 13. Enregistrer le commentaire admin
        withdrawal.setAdminComment(comment);

        // 14. Enregistrer l'admin qui a traité la demande
        withdrawal.setProcessedBy(currentAdmin);

        // 15. Sauvegarder la demande validée
        WithdrawalRequest savedWithdrawal = withdrawalRequestRepository.save(withdrawal);

        // 16. Notifier le prestataire par push et email si les notifications sont activées
        notifyWithdrawalApproved(savedWithdrawal);

        // 17. Retourner la réponse au back-office
        return mapToWithdrawalDTO(savedWithdrawal);
    }

    @Override
    @Transactional
    public WithdrawalRequestDTO rejectWalletWithdrawal(Long withdrawalId, String rejectionReason) {

        // 1. Récupérer l'admin connecté pour l'enregistrer comme personne ayant traité la demande
        User currentAdmin = getCurrentAdmin();

        // 2. Récupérer la demande de retrait par son id
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        // 3. Vérifier que la demande est encore en attente
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new BadRequestException("Cette demande de retrait a déjà été traitée");
        }

        // 4. Récupérer le wallet du prestataire concerné par la demande
        ProviderWallet wallet = walletRepository.findByProviderId(withdrawal.getProvider().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet prestataire introuvable"));

        // 5. Vérifier que le montant demandé est bien disponible dans le solde en attente (avant de remettre dans le solde disponible )
        if (wallet.getPendingBalance().compareTo(withdrawal.getAmount()) < 0) {
            throw new BadRequestException("Montant non trouvé dans le solde en attente");
        }

        // 6. Déduire le montant du solde en attente
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(withdrawal.getAmount()));

        // 7. Rembourser le montant dans le solde disponible
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(withdrawal.getAmount()));

        // 8. Sauvegarder le wallet mis à jour
        walletRepository.save(wallet);

        // 9. Marquer la demande comme refusée
        withdrawal.setStatus(WithdrawalStatus.REJECTED);

        // 10. Enregistrer la date de traitement
        withdrawal.setProcessedAt(LocalDateTime.now());

        // 11. Enregistrer le motif de refus
        withdrawal.setMotifRefus(rejectionReason);

        // 12. Enregistrer l'admin qui a traité la demande
        withdrawal.setProcessedBy(currentAdmin);

        // 13. Sauvegarder la demande refusée
        WithdrawalRequest savedWithdrawal = withdrawalRequestRepository.save(withdrawal);

        // 14. Notifier le prestataire par push et email si les notifications sont activées
        notifyWithdrawalRejected(savedWithdrawal, rejectionReason);

        // 15. Retourner la réponse au back-office
        return mapToWithdrawalDTO(savedWithdrawal);
    }

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

    @Override
    @Transactional
    public void deleteEstimatedDelay(Long id) {
        EstimatedDelay delay = estimatedDelayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Délai estimé introuvable"));
        estimatedDelayRepository.delete(delay);
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
     * Récupère l'admin connecté depuis le contexte de sécurité.
     */
    private User getCurrentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Admin introuvable"));
    }

    /**
     * Notifie le prestataire après validation de son retrait.
     */
    private void notifyWithdrawalApproved(WithdrawalRequest withdrawal) {
        User provider = withdrawal.getProvider();

        if (provider.isNotificationsEnabled()) {
            notificationService.sendPush(
                    provider.getId(),
                    "Retrait validé",
                    "Votre retrait de " + withdrawal.getAmount() + " FCFA a été validé et payé."
            );

            String subject = "Votre retrait a été validé";
            String body = "Bonjour " + provider.getFirstName() + ",\n\n" +
                    "Votre demande de retrait a été validée et payée.\n" +
                    "Référence : " + withdrawal.getReference() + "\n" +
                    "Montant : " + withdrawal.getAmount() + " FCFA\n\n" +
                    "Cordialement,\nL'équipe Solimus";
            emailService.sendEmail(provider.getEmail(), subject, body);
        }
    }

    /**
     * Notifie le prestataire après refus de son retrait.
     */
    private void notifyWithdrawalRejected(WithdrawalRequest withdrawal, String rejectionReason) {
        User provider = withdrawal.getProvider();

        if (provider.isNotificationsEnabled()) {
            notificationService.sendPush(
                    provider.getId(),
                    "Retrait refusé",
                    "Votre demande de retrait de " + withdrawal.getAmount() + " FCFA a été refusée."
            );

            String subject = "Votre retrait a été refusé";
            String body = "Bonjour " + provider.getFirstName() + ",\n\n" +
                    "Votre demande de retrait a été refusée.\n" +
                    "Référence : " + withdrawal.getReference() + "\n" +
                    "Montant : " + withdrawal.getAmount() + " FCFA\n" +
                    "Motif : " + rejectionReason + "\n\n" +
                    "Le montant a été remboursé dans votre solde disponible.\n\n" +
                    "Cordialement,\nL'équipe Solimus";
            emailService.sendEmail(provider.getEmail(), subject, body);
        }
    }

    /**
     * Mappe une demande de retrait vers le DTO retourné au front.
     */
    private WithdrawalRequestDTO mapToWithdrawalDTO(WithdrawalRequest withdrawal) {
        return WithdrawalRequestDTO.builder()
                .id(withdrawal.getId())
                .reference(withdrawal.getReference())
                .amount(withdrawal.getAmount())
                .method(withdrawal.getMethod())
                .phoneNumber(withdrawal.getPhoneNumber())
                .status(withdrawal.getStatus())
                .createdAt(withdrawal.getCreatedAt())
                .build();
    }

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
