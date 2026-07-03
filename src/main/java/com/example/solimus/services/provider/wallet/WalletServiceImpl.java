package com.example.solimus.services.provider.wallet;

import com.example.solimus.dtos.provider.wallet.RequestWithdrawalDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;
import com.example.solimus.dtos.provider.wallet.WalletTransactionDTO;
import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.entities.User;
import com.example.solimus.entities.Wallet;
import com.example.solimus.entities.WithdrawalRequest;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.TransactionType;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.repositories.WalletRepository;
import com.example.solimus.repositories.WithdrawalRequestRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // =========================================================================
    // Récupération Wallet
    // =========================================================================
    @Override
    @Transactional
    public WalletDTO getMyWallet(int page, int size) {

        // 1. Récupérer l'utilisateur connecté (prestataire)
        User currentProvider = getCurrentUser();

        // 2. Récupérer le wallet ou en créer un si inexistant
        Wallet wallet = walletRepository.findByProviderId(currentProvider.getId())
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .provider(currentProvider) // Prestataire propriétaire du wallet
                            .availableBalance(BigDecimal.ZERO) // Solde disponible initialisé à 0
                            .pendingBalance(BigDecimal.ZERO) // Solde en attente initialisé à 0
                            .totalThisMonth(BigDecimal.ZERO) // Total reçu ce mois initialisé à 0
                            .build();
                    return walletRepository.save(newWallet);
                });

        // 3. Récupérer les transactions avec pagination
        Page<WalletTransactionDTO> transactions = getTransactions(currentProvider.getId(), page, size);

        // 4. Construire et retourner le DTO
        return WalletDTO.builder()
                .availableBalance(wallet.getAvailableBalance()) // Solde disponible du prestataire
                .pendingBalance(wallet.getPendingBalance()) // Solde en attente de validation
                .totalThisMonth(wallet.getTotalThisMonth()) // Total reçu ce mois
                .transactions(transactions)
                .build();
    }

    // =========================================================================
    // Crédit Wallet
    // =========================================================================

    @Override
    @Transactional
    public void creditWallet(Long providerId, BigDecimal amount) {

        // Recherche du wallet du prestataire ou création automatique s'il n'existe pas
        Wallet wallet = walletRepository.findByProviderId(providerId)
                .orElseGet(() -> walletRepository.save(createWallet(providerId)));

        // Créditer le solde disponible
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));

        // Mettre à jour le total reçu ce mois (vérifier si on est dans le même mois que la dernière mise à jour)
        LocalDate today = LocalDate.now(); // Date actuelle
        LocalDate walletUpdatedAt = wallet.getUpdatedAt() != null // Récupérer la date de dernière mise à jour du wallet
                ? wallet.getUpdatedAt().toLocalDate()
                : null;

        // Vérifier si le wallet n'a jamais été mis à jour ou si on a changé de mois/année
        if (walletUpdatedAt == null ||
            walletUpdatedAt.getMonth() != today.getMonth() || // Mois différent
            walletUpdatedAt.getYear() != today.getYear()) { // Année différente
            // Nouveau mois : réinitialiser avec le montant actuel
            wallet.setTotalThisMonth(amount);
        } else {
            // Même mois : ajouter au cumul existant
            wallet.setTotalThisMonth(wallet.getTotalThisMonth().add(amount));
        }

        walletRepository.save(wallet);
    }

    // =========================================================================
    // Demande de versement (retrait)
    // =========================================================================

    @Override
    @Transactional
    public WithdrawalRequestDTO requestWithdrawal(RequestWithdrawalDTO dto) {
        User currentProvider = getCurrentUser();

        // Récupérer le wallet du prestataire
        Wallet wallet = walletRepository.findByProviderId(currentProvider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet introuvable"));

        // 1. Vérifier que le solde est suffisant
        if (dto.getAmount().compareTo(wallet.getAvailableBalance()) > 0) {
            throw new BadRequestException(
                    "Solde insuffisant. Disponible : " + wallet.getAvailableBalance() + " FCFA");
        }

        // 2. Créer la demande de versement (retrait)
        WithdrawalRequest retrait = WithdrawalRequest.builder()
                .reference(generateReference("WIT"))                     // Référence unique (ex: WIT-987654)
                .provider(currentProvider)                              // Prestataire effectuant la demande
                .amount(dto.getAmount())                                // Montant du retrait
                .method(dto.getMethod())                                 // Moyen de retrait (WAVE, ORANGE_MONEY)
                .phoneNumber(dto.getPhoneNumber())                       // Numéro de téléphone destinataire
                .status(WithdrawalStatus.PENDING)                        // Nouveau retrait toujours PENDING
                .build();

        withdrawalRequestRepository.save(retrait);

        // 3. Déduire immédiatement du solde disponible (Son wallet) et mettre le montant en attente
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(dto.getAmount()));
        wallet.setPendingBalance(wallet.getPendingBalance().add(dto.getAmount()));
        walletRepository.save(wallet);

        // 4. Notifier le prestataire (push + email) si notifications activées
        if (currentProvider.isNotificationsEnabled()) {
            // Notification push
            notificationService.sendPush(
                    currentProvider.getId(),
                    "Demande de retrait reçue",
                    "Votre demande de retrait de " + dto.getAmount() + " FCFA a été enregistrée et est en attente de traitement."
            );

            // Notification email
            String emailSubject = "Confirmation de votre demande de retrait";
            String emailBody = "Bonjour " + currentProvider.getFirstName() + ",\n\n" +
                    "Votre demande de retrait de " + dto.getAmount() + " FCFA a été enregistrée avec succès.\n" +
                    "Référence : " + retrait.getReference() + "\n" +
                    "Méthode : " + dto.getMethod() + "\n" +
                    "Numéro : " + dto.getPhoneNumber() + "\n\n" +
                    "Votre demande est en attente de validation par l'administrateur.\n\n" +
                    "Cordialement,\nL'équipe Solimus";
            emailService.sendEmail(currentProvider.getEmail(), emailSubject, emailBody);
        }

        //Retourner la réponse
        return mapToWithdrawalDTO(retrait);
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    /**
     * Crée un nouveau portefeuille pour un prestataire donné (sécurité).
     */
    private Wallet createWallet(Long providerId) {

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Prestataire introuvable"));
        return Wallet.builder()
                .provider(provider)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalThisMonth(BigDecimal.ZERO)
                .build();
    }

    /**
     * Génère une référence unique pour un retrait.
     */
    private String generateReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }

    /**
     * Mappe une WithdrawalRequest vers son DTO pour la réponse.
     */
    private WithdrawalRequestDTO mapToWithdrawalDTO(WithdrawalRequest retrait) {
        return WithdrawalRequestDTO.builder()
            .id(retrait.getId())
            .reference(retrait.getReference())
            .amount(retrait.getAmount())
            .method(retrait.getMethod())
            .phoneNumber(retrait.getPhoneNumber())
            .status(retrait.getStatus())
            .createdAt(retrait.getCreatedAt())
            .build();
    }

    /**
     * Fusionne les paiements et les retraits, les mappe en DTO et pagine le résultat.
     */
    private Page<WalletTransactionDTO> getTransactions(Long providerId, int page, int size) {

        // 1. Récupérer tous les paiements reçus
        List<PaymentProvider> paiements = paymentRepository.findAllByProviderIdOrderByCreatedAtDesc(providerId);

        // 2. Récupérer tous les retraits
        List<WithdrawalRequest> retraits = withdrawalRequestRepository.findAllByProviderIdOrderByCreatedAtDesc(providerId);

        // Liste fusionnée contenant paiements et retraits
        List<WalletTransactionDTO> transactions = new ArrayList<>();

        // 3. Ajouter les paiements (crédits)
        if (paiements != null) {

            // Parcourir chaque paiement pour le convertir en transaction
            paiements.forEach(p -> {

                // Récupérer le nom de la résidence (ou valeur par défaut)
                String residenceName = p.getInterventionRequest().getResidence() != null
                        ? p.getInterventionRequest().getResidence().getName()
                        : "Résidence";
                //Récupérer le nom de la spécialité ou valeur par défaut
                String specialtyName = p.getInterventionRequest().getSpecialty() != null
                        ? p.getInterventionRequest().getSpecialty().getName()
                        : "Intervention";

                // Créer et ajouter la transaction de paiement
                transactions.add(WalletTransactionDTO.builder()
                        .label(residenceName + " - " + specialtyName)
                        .amount(p.getAmount())
                        .type(TransactionType.ENTREE)
                        .status(p.getStatus() == PaymentStatus.COMPLETED ? "Reçu" : "En attente")
                        .date(p.getCreatedAt().toLocalDate())
                        .build());
            });
        }

        // 4. Ajouter les retraits (débits)
        if (retraits != null) {
            // Parcourir chaque retrait pour le convertir en transaction
            retraits.forEach(r -> {
                // Déterminer le label de la méthode de retrait
                String methodeLabel = r.getMethod() != null ? r.getMethod().name() : "N/A";
                String statutLabel = "En attente";
                if (r.getStatus() == WithdrawalStatus.COMPLETED) {
                    statutLabel = "Effectué";
                } else if (r.getStatus() == WithdrawalStatus.REJECTED) {
                    statutLabel = "Refusé";
                }

                // Créer et ajouter la transaction de retrait
                transactions.add(WalletTransactionDTO.builder()
                        .label("Retrait " + methodeLabel)
                        .amount(r.getAmount().negate())
                        .type(TransactionType.SORTIE)
                        .status(statutLabel)
                        .date(r.getCreatedAt().toLocalDate())
                        .build());
            });
        }

        // 5. Trier par date décroissante
        transactions.sort(Comparator.comparing(WalletTransactionDTO::getDate).reversed());

        // 6. Paginer la liste fusionnée
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), transactions.size());
        List<WalletTransactionDTO> pagedTransactions = transactions.subList(start, end);

        // pagedTransactions -> liste des transactions à afficher dans cette page
        // pageable -> infos pagination (numéro page, taille page, tri)
        // transactions.size() -> nombre total de transactions (pour calculer nombre total de pages)
        return new PageImpl<>(pagedTransactions, pageable, transactions.size());
    }

    /**
     * Récupère l'utilisateur (prestataire) actuellement authentifié via le contexte de sécurité Spring.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }
}
