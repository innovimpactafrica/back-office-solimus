package com.example.solimus.services.auth;

import com.example.solimus.dtos.auth.*;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.User;
import com.example.solimus.entities.auth.ActivationCode;
import com.example.solimus.enums.CodeType;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ActivationCodeRepository;

import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.RoleRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;


/**
 * Service gérant la logique d'authentification et de création de compte selon le flux simplifié (V1) :
 * 1. Inscription initiale : Capture des infos de base et stockage en statut PENDING.
 * 2. Validation OTP : Vérification de l'email via un code à 4 ou 6 chiffres (ActivationCodeService).
 * 3. Finalisation : Définition du mot de passe et activation du compte.
 * 
 * Intègre également la gestion de la connexion (JWT), de la déconnexion (Blacklist) 
 * et de la récupération de mot de passe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;

    private final ActivationCodeService activationCodeService;
    private final ActivationCodeRepository activationCodeRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SubscriptionService subscriptionService;

    // ============================================================================
    // 👤 INSCRIPTION ET ACTIVATION
    // ============================================================================

    @Override
    @Transactional
    public void register(RegisterRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Cet email est déjà utilisé.");
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new PhoneAlreadyExistsException("Ce numéro de téléphone est déjà utilisé.");
        }

        // SÉCURITÉ : Seuls les rôles PRESTATAIRE et COPROPRIETAIRE sont autorisés à s'auto-inscrire via ce endpoint public.
        // Les Syndics sont créés par l'Admin.
        if (dto.getRole() != ERole.ROLE_PRESTATAIRE && dto.getRole() != ERole.ROLE_COPROPRIETAIRE) {
            throw new BadRequestException("Action non autorisée : Seuls les prestataires et copropriétaires peuvent s'auto-inscrire.");
        }

        Role role = roleRepository.findByName(dto.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Rôle non trouvé : " + dto.getRole()));

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);

        if (dto.getRole() == ERole.ROLE_PRESTATAIRE) {
            validateAndSetProviderInfo(user, dto);
        } else if (dto.getRole() == ERole.ROLE_COPROPRIETAIRE) {
            validateAndSetCoOwnerInfo(user, dto);
        }

        User savedUser = userRepository.save(user);

        // Initialisation de l'abonnement par défaut si rôle PRESTATAIRE
        if (dto.getRole() == ERole.ROLE_PRESTATAIRE) {
            subscriptionService.initialiserAbonnement(savedUser);
        }

        // Génération du code OTP mobile (4 chiffres) pour l'auto-inscription
        String code = activationCodeService.generateAndStoreCodeMobile(savedUser);

        // Envoi par email
        emailService.sendActivationCode(user.getEmail(), code, user.getFirstName());
        log.info("Inscription réussie. OTP envoyé à : {}", user.getEmail());
    }

    @Override
    @Transactional
    public void verifyCode(VerifyCodeRequestDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (!activationCodeService.verifyCode(user, dto.getCode())) {
            throw new BadRequestException("Code d'activation invalide ou expiré");
        }

        activationCodeService.deleteCodeByUser(user);
        log.info("Code OTP validé avec succès pour : {}", user.getEmail());
    }

    @Override
    @Transactional
    public void setPassword(SetPasswordRequestDTO dto) {
        // 1. Vérifier si les mots de passe correspondent
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas.");
        }

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        
        // En V1, activation automatique après définition du mot de passe
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("Mot de passe défini et compte activé pour : {}", user.getEmail());
    }

    // ============================================================================
    // 🔐 CONNEXION ET JWT
    // ============================================================================

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO dto) {
        // Recherche par email ou téléphone
        User user = userRepository.findByEmail(dto.getIdentifier())
                .or(() -> userRepository.findByPhone(dto.getIdentifier()))
                .orElseThrow(() -> new BadRequestException("Identifiant ou mot de passe incorrect"));

        // Vérification du mot de passe
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BadRequestException("Identifiant ou mot de passe incorrect");
        }

        // Vérification du statut du compte
        if (user.getStatus() == UserStatus.PENDING) {
            throw new BadRequestException("Votre compte n'est pas encore activé. Veuillez vérifier votre email.");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BadRequestException("Votre compte a été désactivé. Veuillez contacter l'administrateur.");
        }

        // Si c'est un administrateur, on déclenche un flux OTP (2FA)
        if (user.getRole().getName() == ERole.ROLE_ADMIN) {
            // Génération d'un code à 6 chiffres pour l'admin
            String otpCode = activationCodeService.generateAndStoreCode(user);
            emailService.sendActivationCode(user.getEmail(), otpCode, user.getFirstName());
            log.info("OTP de connexion généré pour l'administrateur : {}", user.getEmail());
            return new LoginResponseDTO(user.getEmail(), true);
        }
        
        // Pour les autres rôles : connexion directe avec génération du token JWT
        return generateLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponseDTO verifyAdminLoginOtp(VerifyCodeRequestDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur non trouvé"));

        if (user.getRole().getName() != ERole.ROLE_ADMIN) {
            throw new BadRequestException("Action réservée aux administrateurs");
        }

        // Vérification du code
        if (!activationCodeService.verifyCode(user, dto.getCode())) {
            throw new BadRequestException("Code OTP invalide ou expiré");
        }

        // Nettoyage du code utilisé
        activationCodeService.deleteCodeByUser(user);
        log.info("OTP validé pour l'admin : {}. Génération du token...", user.getEmail());

        return generateLoginResponse(user);
    }



    @Override
    @Transactional
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            tokenBlacklistService.addToBlackList(jwt);
            log.info("Token ajouté à la blacklist pour déconnexion.");
        }
    }

    // ============================================================================
    // 🔄 RÉCUPÉRATION DE MOT DE PASSE
    // ============================================================================

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequestDTO dto) {
        // 1. Trouver l'utilisateur par e-mail ou téléphone
        User user = userRepository.findByEmail(dto.getEmailOrPhone())
                .or(() -> userRepository.findByPhone(dto.getEmailOrPhone()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte n'est associé à cet identifiant (email ou téléphone)."));

        // 2. Bloquer si le compte est désactivé
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BadRequestException("Ce compte a été désactivé par un administrateur.");
        }

        // 3. Vérifier si le compte est actif (comme dans Coopachat)
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Ce compte n'est pas encore actif. Veuillez d'abord valider votre inscription.");
        }

        // 4. Générer le code/token en fonction du rôle (Mobile vs Web)
        String resetToken;
        ERole userRole = user.getRole().getName();
        if (userRole == ERole.ROLE_PRESTATAIRE || userRole == ERole.ROLE_COPROPRIETAIRE) {
            // Code à 4 chiffres pour le mobile
            resetToken = activationCodeService.generateAndStoreResetCodeMobile(user);
        } else {
            // Token UUID pour le web
            resetToken = activationCodeService.generateAndStoreResetToken(user);
        }
        
        // 5. Envoyer l'email avec le lien/code
        emailService.sendPasswordResetCode(user.getEmail(), resetToken, user.getFirstName());
        log.info("Processus de réinitialisation initié pour : {}", user.getEmail());
    }

    @Override
    @Transactional
    public ForgotPasswordVerifyResponseDTO verifyForgotPasswordCode(VerifyForgotPasswordCodeRequestDTO dto) {
        // 1. Trouver l'utilisateur par e-mail ou téléphone
        User user = userRepository.findByEmail(dto.getEmailOrPhone())
                .or(() -> userRepository.findByPhone(dto.getEmailOrPhone()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte n'est associé à cet identifiant."));

        // 2. Récupérer le code de type PASSWORD_RESET
        ActivationCode activationCode = activationCodeRepository.findByCodeAndUser(dto.getCode(), user)
                .filter(ac -> ac.getType() == CodeType.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException("Code de réinitialisation invalide."));

        // 3. Vérifier s'il est expiré ou déjà utilisé
        if (activationCode.isUsed()) {
            throw new BadRequestException("Ce code a déjà été utilisé.");
        }
        if (activationCode.isExpired()) {
            throw new BadRequestException("Ce code a expiré. Veuillez refaire une demande.");
        }

        // 4. Invalider le code à 4 chiffres (usage unique)
        activationCode.setUsed(true);
        activationCodeRepository.save(activationCode);

        // 5. Générer un token UUID pour l'étape suivante (reset-password)
        String finalResetToken = activationCodeService.generateAndStoreResetToken(user);

        return ForgotPasswordVerifyResponseDTO.builder()
                .token(finalResetToken)
                .build();
    }


    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO dto) {
        // 1. Vérifier que les mots de passe correspondent
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas.");
        }

        // 2. Récupérer le token depuis la base (doit être de type PASSWORD_RESET et non utilisé)
        ActivationCode activationCode = activationCodeRepository.findByCodeAndTypeAndUsedFalse(dto.getToken(), CodeType.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException("Token de réinitialisation invalide ou déjà utilisé."));

        // 3. Vérifier l'expiration
        if (activationCode.isExpired()) {
            throw new BadRequestException("Ce token a expiré. Veuillez refaire une demande.");
        }

        // 4. Récupérer l'utilisateur associé
        User user = activationCode.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("Utilisateur introuvable pour ce token.");
        }

        // 5. Mettre à jour le mot de passe
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        // 6. Marquer le token comme utilisé
        activationCode.setUsed(true);
        activationCodeRepository.save(activationCode);

        log.info("Mot de passe réinitialisé avec succès pour : {}", user.getEmail());
    }


    // ============================================================================
    // 🔑 ACTIVATION DE COMPTE UTILISATEUR (créé par ADMIN)
    // ============================================================================

    /**
     * Étape 1 : L'utilisateur clique sur le lien d'activation.
     * Valide le token UUID et retourne les infos du compte pour pré-remplir le formulaire.
     */
    @Override
    @Transactional(readOnly = true)
    public AccountActivationInfoDTO validateActivationToken(String token) {
        ActivationCode activationCode = activationCodeService
                .findValidAccountActivationToken(token)
                .orElseThrow(() -> new BadRequestException(
                        "Ce lien d'activation est invalide ou a expiré. " +
                        "Veuillez contacter votre administrateur pour obtenir un nouveau lien."
                ));

        User user = activationCode.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("Compte utilisateur introuvable pour ce token.");
        }

        log.info("Token d'activation validé pour l'utilisateur : {}", user.getEmail());

        return AccountActivationInfoDTO.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .token(token) // Retour du token pour l'étape suivante
                .build();
    }

    /**
     * Renvoie un nouveau lien d'activation à l'utilisateur.
     * Inclut un cooldown de 60 secondes .
     */
    @Override
    @Transactional
    public void resendUserActivationLink(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec cet email : " + email));

        // 1. Vérifier si le compte est déjà actif
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Ce compte est déjà actif.");
        }

        // 2. Déterminer si c'est un utilisateur mobile ou web
        ERole userRole = user.getRole().getName();
        boolean isMobile = (userRole == ERole.ROLE_PRESTATAIRE || userRole == ERole.ROLE_COPROPRIETAIRE);

        // 3. Déterminer le type de code (ACTIVATION ou ACCOUNT_ACTIVATION)
        CodeType type = CodeType.ACCOUNT_ACTIVATION;
        if (isMobile) {
            // Pour le mobile, on regarde s'il y a un code ACCOUNT_ACTIVATION existant, sinon on utilise ACTIVATION
            Optional<ActivationCode> existingCode = activationCodeRepository.findByUserAndType(user, CodeType.ACCOUNT_ACTIVATION);
            if (existingCode.isEmpty()) {
                type = CodeType.ACTIVATION;
            }
        }

        // 4. Vérifier le cooldown (délai entre deux envois)
        long cooldown = activationCodeService.getRemainingCooldownSecond(user, type);
        if (cooldown > 0) {
            String unit = isMobile ? "code" : "lien";
            throw new BadRequestException("Veuillez attendre " + cooldown + " secondes avant de demander un nouveau " + unit + ".");
        }

        // 5. Invalider l'ancien et renvoyer
        if (isMobile) {
            String newCode = activationCodeService.generateAndStoreCodeMobileWithType(user, type);
            emailService.sendActivationCode(user.getEmail(), newCode, user.getFirstName());
            log.info("Nouveau code d'activation (4 chiffres) renvoyé pour l'utilisateur mobile : {}", email);
        } else {
            String newToken = activationCodeService.generateAndStoreAccountActivationToken(user);
            emailService.sendUserActivationLink(user.getEmail(), newToken, user.getFirstName());
            log.info("Nouveau lien d'activation (UUID) renvoyé pour l'utilisateur : {}", email);
        }
    }

    /**
     * Renvoie un nouveau code OTP d'activation à un utilisateur mobile.
     * Cet endpoint est séparé du renvoi de lien pour clarifier l'usage côté mobile.
     */
    @Override
    @Transactional
    public void resendActivationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec cet email : " + email));

        // 1. Vérifier si le compte est déjà actif
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Ce compte est déjà actif.");
        }

        // 2. Ce endpoint est dédié aux comptes mobiles
        ERole userRole = user.getRole().getName();
        boolean isMobile = (userRole == ERole.ROLE_PRESTATAIRE || userRole == ERole.ROLE_COPROPRIETAIRE);
        if (!isMobile) {
            throw new BadRequestException("Ce compte utilise un lien d'activation, pas un code OTP.");
        }

        // 3. Déterminer le type de code à renouveler
        CodeType type = CodeType.ACCOUNT_ACTIVATION;
        Optional<ActivationCode> existingCode = activationCodeRepository.findByUserAndType(user, CodeType.ACCOUNT_ACTIVATION);
        if (existingCode.isEmpty()) {
            type = CodeType.ACTIVATION;
        }

        // 4. Vérifier le cooldown
        long cooldown = activationCodeService.getRemainingCooldownSecond(user, type);
        if (cooldown > 0) {
            throw new BadRequestException("Veuillez attendre " + cooldown + " secondes avant de demander un nouveau code.");
        }

        // 5. Invalider l'ancien code et renvoyer un nouveau code OTP
        String newCode = activationCodeService.generateAndStoreCodeMobileWithType(user, type);
        emailService.sendActivationCode(user.getEmail(), newCode, user.getFirstName());
        log.info("Nouveau code d'activation (4 chiffres) renvoyé pour l'utilisateur mobile : {}", email);
    }


    /**
     * Étape 2 : L'utilisateur définit son mot de passe.
     * Valide à nouveau le token, encode le mot de passe, active le compte et génère un JWT.
     */
    @Override
    @Transactional
    public String activateAccount(ActivateAccountRequestDTO dto) {

        // 1. Vérifier que les mots de passe correspondent
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas.");
        }

        // 2. Valider et récupérer le token d'activation (doit être ACCOUNT_ACTIVATION, non utilisé, non expiré)
        ActivationCode activationCode = activationCodeService
                .findValidAccountActivationToken(dto.getToken())
                .orElseThrow(() -> new BadRequestException(
                        "Ce lien d'activation est invalide ou a expiré. " +
                        "Veuillez contacter votre administrateur pour obtenir un nouveau lien."
                ));

        User user = activationCode.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("Compte utilisateur introuvable pour ce token.");
        }

        // 3. Définir le mot de passe et activer le compte
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // 4. Invalider le token (usage unique)
        activationCode.setUsed(true);
        activationCodeRepository.save(activationCode);

        log.info("Compte utilisateur activé avec succès pour : {}", user.getEmail());

        return "Votre compte a été activé avec succès. Vous pouvez maintenant vous connecter.";
    }

    // ============================================================================
    // 🛠️ MÉTHODES PRIVÉES
    // ============================================================================

    private void validateAndSetProviderInfo(User user, RegisterRequestDTO dto) {
        if (dto.getCompanyName() == null || dto.getCompanyName().isBlank()) {
            throw new BadRequestException("Le nom de l'entreprise est obligatoire pour un prestataire");
        }
        if (dto.getSpecialtyId() == null) {
            throw new BadRequestException("La spécialité est obligatoire pour un prestataire");
        }
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new BadRequestException("Les coordonnées GPS sont obligatoires pour localiser vos interventions");
        }

        user.setCompanyName(dto.getCompanyName());
        user.setSpecialty(specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée")));

        // On enregistre sa position de base pour le matching 30km
        user.setLatitude(dto.getLatitude());
        user.setLongitude(dto.getLongitude());
    }

    private void validateAndSetCoOwnerInfo(User user, RegisterRequestDTO dto) {
        // Rejeter les champs spécifiques aux prestataires
        if (dto.getSpecialtyId() != null) {
            throw new BadRequestException("Le champ specialtyId est réservé aux prestataires");
        }
        if (dto.getCompanyName() != null) {
            throw new BadRequestException("Le champ companyName est réservé aux prestataires");
        }
        if (dto.getLatitude() != null || dto.getLongitude() != null) {
            throw new BadRequestException("Les coordonnées GPS sont réservées aux prestataires");
        }

        if (dto.getResidenceId() == null) {
            throw new BadRequestException("La résidence est obligatoire pour un copropriétaire");
        }
        if (dto.getPropertyId() == null) {
            throw new BadRequestException("L'appartement est obligatoire pour un copropriétaire");
        }

        // Vérifier que la résidence existe
        residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence non trouvée"));

        // Vérifier que le bien existe et appartient à la résidence
        Property property = propertyRepository.findById(dto.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Appartement non trouvé"));

        if (!property.getResidence().getId().equals(dto.getResidenceId())) {
            throw new BadRequestException("L'appartement n'appartient pas à cette résidence");
        }

        // Vérifier que le bien n'a pas déjà un propriétaire
        if (property.getOwner() != null) {
            throw new BadRequestException(
                "Cet appartement a déjà un propriétaire. " +
                "Contactez votre syndic pour plus d'informations."
            );
        }

        // Lier l'utilisateur au bien (propriétaire)
        property.setOwner(user);
        propertyRepository.save(property);
    }

    /**
     * Centralise la création de la réponse de connexion avec JWT.
     */
    private LoginResponseDTO generateLoginResponse(User user) {
        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getRole().getName().name(),
                user.getId()
        );

        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .email(user.getEmail())
                .role(user.getRole().getName().name())
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus())
                .otpRequired(false)
                .build();
    }
}
