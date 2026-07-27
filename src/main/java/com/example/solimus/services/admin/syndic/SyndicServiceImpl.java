package com.example.solimus.services.admin.syndic;

import com.example.solimus.dtos.admin.syndic.CreateSyndicDTO;
import com.example.solimus.dtos.admin.syndic.CreateSyndicResponseDTO;
import com.example.solimus.dtos.admin.syndic.SyndicPlanOptionDTO;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionDuration;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.RoleRepository;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicServiceImpl implements SyndicService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SyndicPlanRepository syndicPlanRepository;
    private final SyndicProfileRepository syndicProfileRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    @Override
    @Transactional
    public CreateSyndicResponseDTO createSyndic(CreateSyndicDTO dto) {

        // 1. Vérifie l'unicité de l'email et du téléphone
        if (userRepository.existsByEmail(dto.getEmail())) {
            // Un compte existe déjà avec cet email → on bloque avant de créer quoi que ce soit
            throw new EmailAlreadyExistsException("Un compte avec cet email existe déjà : " + dto.getEmail());
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            // Même vérification côté téléphone
            throw new PhoneAlreadyExistsException("Un compte avec ce téléphone existe déjà : " + dto.getPhone());
        }

        // 2. Récupère la formule d'abonnement choisie
        SyndicPlan plan = syndicPlanRepository.findById(dto.getSyndicPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Formule d'abonnement introuvable"));

        // 3. Détermine le montant selon la durée choisie (annuel ou mensuel)
        BigDecimal amountPaid = dto.getDuration() == SubscriptionDuration.YEARLY
                ? plan.getYearlyPrice()
                : plan.getMonthlyPrice();

        // Si l'admin n'a jamais configuré ce tarif pour la formule, on refuse plutôt que de facturer 0/null
        if (amountPaid == null) {
            throw new BadRequestException(
                    dto.getDuration() == SubscriptionDuration.YEARLY
                            ? "Cette formule ne propose pas de tarif annuel"
                            : "Cette formule ne propose pas de tarif mensuel");
        }

        // 4. Récupère le rôle SYNDIC
        Role syndicRole = roleRepository.findByName(ERole.ROLE_SYNDIC)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle SYNDIC introuvable en base"));

        // 5. Crée le compte utilisateur — PENDING tant que le paiement n'est pas confirmé.
        // Le mot de passe reste null : tant que l'abonnement n'est pas payé, le syndic n'a reçu
        // aucun identifiant et n'a donc aucune raison de tenter de se connecter. Le mot de passe
        // réel (temporaire) n'est généré et posé qu'au moment du callback TouchPay confirmé.
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setCity(dto.getCity());
        user.setCountry(dto.getCountry().getLabel());
        user.setRole(syndicRole);
        user.setPassword(null);
        user.setStatus(UserStatus.PENDING);

        User savedUser = userRepository.save(user);

        // 6. Crée le profil société lié à ce syndic
        SyndicProfile profile = new SyndicProfile();
        profile.setUser(savedUser);
        profile.setCompanyName(dto.getCompanyName());
        profile.setAddress(dto.getAddress());
        // Coordonnées GPS optionnelles, remplies via l'autocomplétion d'adresse côté front
        profile.setLatitude(dto.getLatitude() != null ? BigDecimal.valueOf(dto.getLatitude()) : null);
        profile.setLongitude(dto.getLongitude() != null ? BigDecimal.valueOf(dto.getLongitude()) : null);
        syndicProfileRepository.save(profile);

        // 7. Crée l'abonnement en PENDING — il ne devient ACTIVE qu'à la confirmation réelle du paiement
        // (callback TouchPay), jamais directement ici
        LocalDateTime startDate = dto.getStartDate() != null
                ? dto.getStartDate().atStartOfDay()
                : LocalDateTime.now();
        LocalDateTime endDate = startDate.plusMonths(dto.getDuration().getMonths());

        // Référence unique préfixée SYN- pour que le bridge et le callback la reconnaissent
        String transactionRef = generateReference("SYN");

        // L'admin connecté est celui qui va réellement effectuer le paiement TouchPay
        // (le syndic n'existe pas encore fonctionnellement tant que le paiement n'est pas confirmé)
        User currentAdmin = getCurrentUser();

        SyndicSubscription subscription = new SyndicSubscription();
        subscription.setSyndic(savedUser);
        subscription.setInitiatedBy(currentAdmin);
        subscription.setSyndicPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentStatus(PaymentStatus.PENDING);
        subscription.setAmountPaid(amountPaid);
        subscription.setMethod(dto.getMethod());
        subscription.setDuration(dto.getDuration());
        subscription.setTransactionRef(transactionRef);
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        syndicSubscriptionRepository.save(subscription);

        // 8. Construit l'URL du pont de paiement TouchPay pour cette référence
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        log.info("Création syndic initiée (en attente de paiement) : {} ({}) — ref: {}",
                savedUser.getEmail(), dto.getCompanyName(), transactionRef);

        return CreateSyndicResponseDTO.builder()
                .userId(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .companyName(dto.getCompanyName())
                .planName(plan.getName())
                .amountPaid(amountPaid)
                .startDate(startDate)
                .endDate(endDate)
                .transactionReference(transactionRef)
                .paymentUrl(bridgeUrl)
                .message("Compte syndic en attente de paiement. Veuillez compléter le paiement via TouchPay pour l'activer.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyndicPlanOptionDTO> listAvailablePlans() {

        // Seules les formules actives peuvent être proposées pour un nouveau syndic
        return syndicPlanRepository.findByActiveTrue().stream()
                .map(plan -> SyndicPlanOptionDTO.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .build())
                .toList();
    }

    // Génère une référence courte et unique, préfixée selon le type de paiement (SYN-, SUB-, CPY-...)
    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    // Récupère l'admin actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}