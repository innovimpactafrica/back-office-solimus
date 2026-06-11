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
import com.example.solimus.repositories.*;
import com.example.solimus.services.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// =============================================================================
//
//  AUTH SERVICE — Gestion complète de l'authentification SOLIMUS
//
//  Ce service gère tout ce qui concerne les comptes utilisateurs :
//
//  1. INSCRIPTION    → Un prestataire ou copropriétaire crée son compte
//  2. VÉRIFICATION   → Il reçoit un code OTP par email et le valide
//  3. MOT DE PASSE   → Il définit son mot de passe et son compte est activé
//  4. CONNEXION      → Il se connecte et reçoit un token JWT
//  5. DÉCONNEXION    → Son token est mis en blacklist
//  6. MDP OUBLIÉ     → Il reçoit un code pour réinitialiser son mot de passe
//  7. ACTIVATION     → Les comptes créés par l'admin sont activés via un lien
//
// =============================================================================
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    // -------------------------------------------------------------------------
    // Dépendances injectées automatiquement par Spring
    // -------------------------------------------------------------------------
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


    // =========================================================================
    // PARTIE 1 — INSCRIPTION ET ACTIVATION DU COMPTE
    // =========================================================================
    //
    //  Flux complet pour un nouvel utilisateur :
    //
    //  register() ──► verifyCode() ──► setPassword()
    //
    //  Étape 1 : register()     → on crée le compte en statut PENDING
    //                             et on envoie un code OTP par email
    //  Étape 2 : verifyCode()   → l'utilisateur entre le code reçu
    //  Étape 3 : setPassword()  → il choisit son mot de passe
    //                             → compte passe en statut ACTIVE
    //
    // =========================================================================

    @Override
    @Transactional
    public void register(RegisterRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Vérifications préliminaires
        // On s'assure que l'email et le téléphone ne sont pas déjà utilisés
        // ---------------------------------------------------------------------
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Cet email est déjà utilisé.");
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new PhoneAlreadyExistsException("Ce numéro de téléphone est déjà utilisé.");
        }

        // ---------------------------------------------------------------------
        // Sécurité : seuls PRESTATAIRE et COPROPRIETAIRE peuvent s'inscrire eux-mêmes
        // Les comptes SYNDIC et ADMIN sont créés par l'administrateur
        // ---------------------------------------------------------------------
        if (dto.getRole() != ERole.ROLE_PRESTATAIRE && dto.getRole() != ERole.ROLE_COPROPRIETAIRE) {
            throw new BadRequestException(
                    "Action non autorisée : seuls les prestataires et copropriétaires " +
                            "peuvent s'auto-inscrire."
            );
        }

        // ---------------------------------------------------------------------
        // Récupération du rôle en base de données
        // ---------------------------------------------------------------------
        Role role = roleRepository.findByName(dto.getRole())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle non trouvé : " + dto.getRole()));

        // ---------------------------------------------------------------------
        // Création du compte avec statut PENDING
        // (le compte ne sera actif qu'après validation du code OTP)
        // ---------------------------------------------------------------------
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);

        // ---------------------------------------------------------------------
        // Informations spécifiques selon le rôle
        // Prestataire → coordonnées GPS + spécialité + nom d'entreprise
        // Copropriétaire → résidence + bien
        // ---------------------------------------------------------------------
        if (dto.getRole() == ERole.ROLE_PRESTATAIRE) {
            validateAndSetProviderInfo(user, dto);
        } else if (dto.getRole() == ERole.ROLE_COPROPRIETAIRE) {
            validateAndSetCoOwnerInfo(user, dto);
        }

        User savedUser = userRepository.save(user);

        // ---------------------------------------------------------------------
        // Pour les prestataires → initialisation de l'abonnement GRATUIT
        // (ils pourront passer en PREMIUM plus tard)
        // ---------------------------------------------------------------------
        if (dto.getRole() == ERole.ROLE_PRESTATAIRE) {
            subscriptionService.initialiserAbonnement(savedUser);
        }

        // ---------------------------------------------------------------------
        // Génération du code OTP à 4 chiffres et envoi par email
        // Ex : "1234" → l'utilisateur le saisit dans l'app mobile
        // ---------------------------------------------------------------------
        String code = activationCodeService.generateAndStoreCodeMobile(savedUser);
        emailService.sendActivationCode(user.getEmail(), code, user.getFirstName());

        log.info("Inscription réussie. OTP envoyé à : {}", user.getEmail());
    }

    /**
     * Renvoie un nouveau code OTP pour les utilisateurs mobiles uniquement.
     * Endpoint dédié mobile
     */
    @Override
    @Transactional
    public void resendActivationCode(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable avec cet email : " + email));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Ce compte est déjà actif.");
        }

        // ---------------------------------------------------------------------
        // Cet endpoint est réservé aux utilisateurs mobiles
        // ---------------------------------------------------------------------
        ERole userRole = user.getRole().getName();
        boolean isMobile = (userRole == ERole.ROLE_PRESTATAIRE
                || userRole == ERole.ROLE_COPROPRIETAIRE);
        if (!isMobile) {
            throw new BadRequestException(
                    "Ce compte utilise un lien d'activation, pas un code OTP.");
        }
           
        CodeType type = CodeType.ACCOUNT_ACTIVATION;

        // On conserve le même type que le code initial.
        // Si un code ACCOUNT_ACTIVATION existe déjà pour cet utilisateur,
        // on renvoie un nouveau code ACCOUNT_ACTIVATION.
        // Sinon, il s'agit d'une inscription classique, donc on renvoie un code ACTIVATION.
        Optional<ActivationCode> existingCode = activationCodeRepository
                .findByUserAndType(user, CodeType.ACCOUNT_ACTIVATION);
        if (existingCode.isEmpty()) {
            type = CodeType.ACTIVATION;
        }

        // ---------------------------------------------------------------------
        // Vérification du cooldown
        // ---------------------------------------------------------------------
        long cooldown = activationCodeService.getRemainingCooldownSecond(user, type);
        if (cooldown > 0) {
            throw new BadRequestException(
                    "Veuillez attendre " + cooldown + " secondes " +
                            "avant de demander un nouveau code.");
        }

        // ---------------------------------------------------------------------
        // Génération et envoi du nouveau code OTP
        // ---------------------------------------------------------------------
        String newCode = activationCodeService
                .generateAndStoreCodeMobileWithType(user, type);
        emailService.sendActivationCode(user.getEmail(), newCode, user.getFirstName());

        log.info("Nouveau code OTP renvoyé à : {}", email);
    }

    @Override
    @Transactional
    public void verifyCode(VerifyCodeRequestDTO dto) {

        // ---------------------------------------------------------------------
        // On cherche l'utilisateur par son email
        // ---------------------------------------------------------------------
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // ---------------------------------------------------------------------
        // On vérifie le code OTP reçu
        // S'il est invalide ou expiré → on bloque
        // ---------------------------------------------------------------------
        if (!activationCodeService.verifyCode(user, dto.getCode())) {
            throw new BadRequestException("Code d'activation invalide ou expiré");
        }

        // ---------------------------------------------------------------------
        // Code validé → on le supprime pour qu'il ne puisse pas être réutilisé
        // ---------------------------------------------------------------------
        activationCodeService.deleteCodeByUser(user);

        log.info("Code OTP validé avec succès pour : {}", user.getEmail());
    }

    @Override
    @Transactional
    public void setPassword(SetPasswordRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Vérification que les deux mots de passe saisis sont identiques
        // ---------------------------------------------------------------------
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas.");
        }

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // ---------------------------------------------------------------------
        // On encode le mot de passe (jamais stocké en clair)
        // puis on active le compte → l'utilisateur peut maintenant se connecter
        // ---------------------------------------------------------------------
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("Mot de passe défini et compte activé pour : {}", user.getEmail());
    }


    // =========================================================================
    // PARTIE 2 — CONNEXION ET JWT
    // =========================================================================
    //
    //  Deux flux selon le rôle :
    //
    //  Prestataire / Copropriétaire / Syndic :
    //  login() ──► retourne directement un token JWT
    //
    //  Admin (sécurité renforcée avec 2FA) :
    //  login() ──► envoie un OTP par email ──► verifyAdminLoginOtp() ──► token JWT
    //
    // =========================================================================

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Recherche par email OU par téléphone
        // L'utilisateur peut se connecter avec l'un ou l'autre
        // ---------------------------------------------------------------------
        User user = userRepository.findByEmail(dto.getIdentifier())
                .or(() -> userRepository.findByPhone(dto.getIdentifier()))
                .orElseThrow(() -> new BadRequestException(
                        "Identifiant ou mot de passe incorrect"));

        // ---------------------------------------------------------------------
        // Vérification du mot de passe
        // passwordEncoder.matches() compare le mot de passe saisi
        // avec la version encodée stockée en base
        // ---------------------------------------------------------------------
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BadRequestException("Identifiant ou mot de passe incorrect");
        }

        // ---------------------------------------------------------------------
        // Vérification du statut du compte
        // PENDING  → pas encore activé
        // DISABLED → bloqué par l'admin
        // ---------------------------------------------------------------------
        if (user.getStatus() == UserStatus.PENDING) {
            throw new BadRequestException(
                    "Votre compte n'est pas encore activé. " +
                            "Veuillez vérifier votre email.");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BadRequestException(
                    "Votre compte a été désactivé. " +
                            "Veuillez contacter l'administrateur.");
        }

        // ---------------------------------------------------------------------
        // Cas spécial ADMIN → double authentification (2FA)
        // On envoie un OTP par email avant de donner le token JWT
        // ---------------------------------------------------------------------
        if (user.getRole().getName() == ERole.ROLE_ADMIN) {
            String otpCode = activationCodeService.generateAndStoreCode(user);
            emailService.sendActivationCode(user.getEmail(), otpCode, user.getFirstName());
            log.info("OTP de connexion généré pour l'administrateur : {}", user.getEmail());
            // otpRequired = true signale au front d'afficher l'écran de saisie OTP
            return new LoginResponseDTO(user.getEmail(), true);
        }

        // ---------------------------------------------------------------------
        // Tous les autres rôles → connexion directe avec token JWT
        // ---------------------------------------------------------------------
        return generateLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponseDTO verifyAdminLoginOtp(VerifyCodeRequestDTO dto) {

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Administrateur non trouvé"));

        // ---------------------------------------------------------------------
        // Sécurité : cet endpoint est réservé aux admins uniquement
        // ---------------------------------------------------------------------
        if (user.getRole().getName() != ERole.ROLE_ADMIN) {
            throw new BadRequestException("Action réservée aux administrateurs");
        }

        // ---------------------------------------------------------------------
        // Vérification du code OTP reçu par email
        // ---------------------------------------------------------------------
        if (!activationCodeService.verifyCode(user, dto.getCode())) {
            throw new BadRequestException("Code OTP invalide ou expiré");
        }

        // ---------------------------------------------------------------------
        // Code valide → on le supprime et on génère le token JWT
        // ---------------------------------------------------------------------
        activationCodeService.deleteCodeByUser(user);
        log.info("OTP validé pour l'admin : {}. Génération du token...", user.getEmail());

        return generateLoginResponse(user);
    }

    @Override
    @Transactional
    public void logout(String token) {

        // ---------------------------------------------------------------------
        // On extrait le token JWT du header "Bearer xxxxx"
        // puis on le met en blacklist → il ne sera plus accepté
        // même s'il n'est pas encore expiré
        // ---------------------------------------------------------------------
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            tokenBlacklistService.addToBlackList(jwt);
            log.info("Token ajouté à la blacklist pour déconnexion.");
        }
    }


    // =========================================================================
    // PARTIE 3 — MOT DE PASSE OUBLIÉ
    // =========================================================================
    //
    //  Flux complet :
    //
    //  forgotPassword() ──► verifyForgotPasswordCode(mobile) ──► resetPassword()
    //
    //  Étape 1 : forgotPassword()            → envoi d'un code OTP par email
    //  Étape 2 : verifyForgotPasswordCode()  → validation du code
    //                                          → retourne un token UUID temporaire (mobile) /web (token déjà disponible dans le lien)
    //  Étape 3 : resetPassword()             → nouveau mot de passe avec le token
    //
    // =========================================================================

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Recherche par email OU téléphone
        // ---------------------------------------------------------------------
        User user = userRepository.findByEmail(dto.getEmailOrPhone())
                .or(() -> userRepository.findByPhone(dto.getEmailOrPhone()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun compte n'est associé à cet identifiant."));

        // ---------------------------------------------------------------------
        // Vérifications du statut du compte
        // ---------------------------------------------------------------------
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BadRequestException(
                    "Ce compte a été désactivé par un administrateur.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Ce compte n'est pas encore actif. " +
                            "Veuillez d'abord valider votre inscription.");
        }

        // ---------------------------------------------------------------------
        // Génération du code selon la plateforme :
        // Mobile (Prestataire / Copropriétaire) → code à 4 chiffres
        // Web (Syndic / Admin)                  → token UUID directement sur le lien 
        // ---------------------------------------------------------------------
        String resetToken;
        ERole userRole = user.getRole().getName();

        if (userRole == ERole.ROLE_PRESTATAIRE || userRole == ERole.ROLE_COPROPRIETAIRE) {
            resetToken = activationCodeService.generateAndStoreResetCodeMobile(user);
        } else {
            resetToken = activationCodeService.generateAndStoreResetToken(user);
        }

        emailService.sendPasswordResetCode(user.getEmail(), resetToken, user.getFirstName());
        log.info("Processus de réinitialisation initié pour : {}", user.getEmail());
    }


    // -----------------------------------------------------------------------------------------------------------------------------------------
    // Vérification de la validité d'un code otp à 4 chiffres  de réinitialisation d'un mot de passe envoyé (mobile) et génération du token UUID
    // --------------------------------------------------------------------------------------------------------------------------------------
    @Override
    @Transactional
    public ForgotPasswordVerifyResponseDTO verifyForgotPasswordCode(
            VerifyForgotPasswordCodeRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Recherche de l'utilisateur
        // ---------------------------------------------------------------------
        User user = userRepository.findByEmail(dto.getEmailOrPhone())
                .or(() -> userRepository.findByPhone(dto.getEmailOrPhone()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun compte n'est associé à cet identifiant."));

        // ---------------------------------------------------------------------
        // Récupération et validation du code PASSWORD_RESET
        // ---------------------------------------------------------------------
        ActivationCode activationCode = activationCodeRepository
                .findByCodeAndUser(dto.getCode(), user)
                .filter(ac -> ac.getType() == CodeType.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException(
                        "Code de réinitialisation invalide."));

        if (activationCode.isUsed()) {
            throw new BadRequestException("Ce code a déjà été utilisé.");
        }
        if (activationCode.isExpired()) {
            throw new BadRequestException(
                    "Ce code a expiré. Veuillez refaire une demande.");
        }

        // ---------------------------------------------------------------------
        // Code valide → on le marque comme utilisé (usage unique)
        // puis on génère un token UUID pour l'étape suivante
        // ---------------------------------------------------------------------
        activationCode.setUsed(true);
        activationCodeRepository.save(activationCode);

        String finalResetToken = activationCodeService.generateAndStoreResetToken(user);

        return ForgotPasswordVerifyResponseDTO.builder()
                .token(finalResetToken)
                .build();
    }
   
    // ----------------------------------------------------------------------------------------------------
    // Réinitialisation du mot de passe avec le token UUID 
    // ----------------------------------------------------------------------------------------------------
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Vérification que les deux mots de passe sont identiques
        // ---------------------------------------------------------------------
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas.");
        }

        // ---------------------------------------------------------------------
        // Récupération et validation du token UUID de réinitialisation
        // ---------------------------------------------------------------------
        ActivationCode activationCode = activationCodeRepository
                .findByCodeAndTypeAndUsedFalse(dto.getToken(), CodeType.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException(
                        "Token de réinitialisation invalide ou déjà utilisé."));

        if (activationCode.isExpired()) {
            throw new BadRequestException(
                    "Ce token a expiré. Veuillez refaire une demande.");
        }

        // ---------------------------------------------------------------------
        // Récupération de l'utilisateur associé au token
        // ---------------------------------------------------------------------
        User user = activationCode.getUser();
        if (user == null) {
            throw new ResourceNotFoundException(
                    "Utilisateur introuvable pour ce token.");
        }

        // ---------------------------------------------------------------------
        // Mise à jour du mot de passe (encodé, jamais en clair)
        // puis marquage du token comme utilisé
        // ---------------------------------------------------------------------
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        activationCode.setUsed(true);
        activationCodeRepository.save(activationCode);

        log.info("Mot de passe réinitialisé avec succès pour : {}", user.getEmail());
    }


    // =========================================================================
    // PARTIE 4 — ACTIVATION DES COMPTES CRÉÉS PAR L'ADMIN
    // =========================================================================
    //
    //  Flux : L'admin crée un compte Syndic → un email avec un lien est envoyé
    //         Le syndic clique sur le lien → saisit son mot de passe → compte actif
    //
    //  validateActivationToken() ──► activateAccount()
    //
    // =========================================================================

    /**
     * Étape 1 — Validation du lien d'activation.
     * L'utilisateur clique sur le lien dans son email.
     * On vérifie que le lien est valide et on retourne ses infos
     * pour pré-remplir le formulaire de définition du mot de passe.
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
            throw new ResourceNotFoundException(
                    "Compte utilisateur introuvable pour ce token.");
        }

        log.info("Token d'activation validé pour : {}", user.getEmail());

        // ---------------------------------------------------------------------
        // On retourne les infos du compte pour pré-remplir le formulaire
        // + le token pour l'étape suivante (activateAccount)
        // ---------------------------------------------------------------------
        return AccountActivationInfoDTO.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .token(token)
                .build();
    }
    /**
     * Étape 2 — Définition du mot de passe et activation du compte.
     * L'utilisateur saisit son nouveau mot de passe via le formulaire.
     * Le compte passe en statut ACTIVE.
     */
    @Override
    @Transactional
    public String activateAccount(ActivateAccountRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Vérification que les mots de passe correspondent
        // ---------------------------------------------------------------------
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas.");
        }

        // ---------------------------------------------------------------------
        // Validation du token d'activation
        // ---------------------------------------------------------------------
        ActivationCode activationCode = activationCodeService
                .findValidAccountActivationToken(dto.getToken())
                .orElseThrow(() -> new BadRequestException(
                        "Ce lien d'activation est invalide ou a expiré. " +
                                "Veuillez contacter votre administrateur pour obtenir un nouveau lien."
                ));

        User user = activationCode.getUser();
        if (user == null) {
            throw new ResourceNotFoundException(
                    "Compte utilisateur introuvable pour ce token.");
        }

        // ---------------------------------------------------------------------
        // Activation du compte :
        // 1. Encodage du mot de passe
        // 2. Statut → ACTIVE
        // 3. Token → marqué comme utilisé (usage unique)
        // ---------------------------------------------------------------------
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        activationCode.setUsed(true);
        activationCodeRepository.save(activationCode);

        log.info("Compte activé avec succès pour : {}", user.getEmail());

        return "Votre compte a été activé avec succès. Vous pouvez maintenant vous connecter.";
    }


    /**
     * Renvoie un nouveau lien d'activation pour les comptes web.
     * Utile si le premier lien a expiré.
     * Inclut un cooldown de 60 secondes pour éviter le spam.
     */
    @Override
    @Transactional
    public void resendUserActivationLink(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable avec cet email : " + email));

        // ---------------------------------------------------------------------
        // Vérification que le compte n'est pas déjà actif
        // ---------------------------------------------------------------------
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Ce compte est déjà actif.");
        }

        ERole userRole = user.getRole().getName();
        boolean isMobile = (userRole == ERole.ROLE_PRESTATAIRE
                || userRole == ERole.ROLE_COPROPRIETAIRE);
        if (isMobile) {
            throw new BadRequestException(
                    "Ce compte utilise un code OTP, pas un lien d'activation.");
        }

        // ---------------------------------------------------------------------
        // Vérification du cooldown → 60 secondes entre deux envois
        // ---------------------------------------------------------------------
        long cooldown = activationCodeService.getRemainingCooldownSecond(user, CodeType.ACCOUNT_ACTIVATION);
        if (cooldown > 0) {
            throw new BadRequestException(
                    "Veuillez attendre " + cooldown + " secondes " +
                            "avant de demander un nouveau lien.");
        }

        // ---------------------------------------------------------------------
        // Génération et envoi du nouveau lien d'activation
        // ---------------------------------------------------------------------
        String newToken = activationCodeService
                .generateAndStoreAccountActivationToken(user);
        emailService.sendUserActivationLink(user.getEmail(), newToken, user.getFirstName());
        log.info("Nouveau lien d'activation renvoyé à : {}", email);
    }


    // =========================================================================
    // MÉTHODES PRIVÉES — Utilitaires internes
    // =========================================================================

    /**
     * Vérifie et renseigne les informations spécifiques au prestataire.
     * Un prestataire doit avoir : nom d'entreprise + spécialité + coordonnées GPS
     * (les coordonnées GPS servent au matching dans un rayon de 30km)
     */
    private void validateAndSetProviderInfo(User user, RegisterRequestDTO dto) {

        if (dto.getCompanyName() == null || dto.getCompanyName().isBlank()) {
            throw new BadRequestException(
                    "Le nom de l'entreprise est obligatoire pour un prestataire.");
        }
        if (dto.getSpecialtyId() == null) {
            throw new BadRequestException(
                    "La spécialité est obligatoire pour un prestataire.");
        }
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new BadRequestException(
                    "Les coordonnées GPS sont obligatoires pour localiser vos interventions.");
        }

        user.setCompanyName(dto.getCompanyName());
        user.setSpecialty(specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée")));
        user.setInterventionZone(dto.getInterventionZone());
        user.setLatitude(dto.getLatitude());
        user.setLongitude(dto.getLongitude());
    }

    /**
     * Vérifie et renseigne les informations spécifiques au copropriétaire.
     * L'affectation aux biens se fera après inscription (par le syndic ou via un flux de rattachement).
     */
    private void validateAndSetCoOwnerInfo(User user, RegisterRequestDTO dto) {

        // ---------------------------------------------------------------------
        // Un copropriétaire ne doit pas envoyer les champs réservés aux prestataires
        // ---------------------------------------------------------------------
        if (dto.getSpecialtyId() != null) {
            throw new BadRequestException(
                    "Le champ specialtyId est réservé aux prestataires.");
        }
        if (dto.getCompanyName() != null) {
            throw new BadRequestException(
                    "Le champ companyName est réservé aux prestataires.");
        }
        if (dto.getLatitude() != null || dto.getLongitude() != null) {
            throw new BadRequestException(
                    "Les coordonnées GPS sont réservées aux prestataires.");
        }
    }

    /**
     * Génère la réponse de connexion complète avec le token JWT.
     * Utilisé par login() et verifyAdminLoginOtp() pour éviter la duplication.
     */
    private LoginResponseDTO generateLoginResponse(User user) {

        // ---------------------------------------------------------------------
        // Génération du token JWT contenant :
        // - l'email (identifiant unique)
        // - le rôle (ADMIN, SYNDIC, PRESTATAIRE, COPROPRIETAIRE)
        // - l'ID (pour les requêtes futures)
        // ---------------------------------------------------------------------
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
                .otpRequired(false) // false = pas besoin d'OTP supplémentaire
                .build();
    }
}