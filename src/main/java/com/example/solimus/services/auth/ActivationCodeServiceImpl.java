package com.example.solimus.services.auth;

import com.example.solimus.entities.User;
import com.example.solimus.entities.auth.ActivationCode;
import com.example.solimus.enums.CodeType;
import com.example.solimus.repositories.ActivationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Implémentation du service de gestion des codes d'activation et de réinitialisation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivationCodeServiceImpl implements ActivationCodeService {

    private final ActivationCodeRepository activationCodeRepository;
    private static final int CODE_LENGTH = 6;
    private static final int CODE_LENGTH_MOBILE = 4;
    private static final int EXPIRATION_MINUTES = 15;
    private static final int RESEND_COOLDOWN_SECONDS = 60; // Délai minimum entre deux envois

    // ============================================================================
    // 🔢 GÉNÉRATION DE CODES
    // ============================================================================

    @Override
    public String generateActivationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    @Override
    public String generateActivationCodeMobile() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH_MOBILE; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    // ============================================================================
    // 💾 STOCKAGE ET RÉCUPÉRATION
    // ============================================================================

    @Override
    @Transactional
    public String generateAndStoreCode(User user) {
        activationCodeRepository.deleteByUserAndType(user, CodeType.ACTIVATION);
        activationCodeRepository.flush();

        String code = generateActivationCode();
        saveCode(user, code, CodeType.ACTIVATION, EXPIRATION_MINUTES);
        log.info("Code standard (6 chiffres) généré pour : {}", user.getEmail());
        return code;
    }

    @Override
    @Transactional
    public String generateAndStoreCodeMobile(User user) {
        activationCodeRepository.deleteByUserAndType(user, CodeType.ACTIVATION);
        activationCodeRepository.flush();

        String code = generateActivationCodeMobile();
        saveCode(user, code, CodeType.ACTIVATION, EXPIRATION_MINUTES);
        log.info("Code mobile (4 chiffres) généré pour : {}", user.getEmail());
        return code;
    }

    @Override
    @Transactional
    public String generateAndStoreResetToken(User user) {
        // Pour la réinitialisation, on utilise un UUID
        activationCodeRepository.deleteByUserAndType(user, CodeType.PASSWORD_RESET);
        activationCodeRepository.flush();

        String token = UUID.randomUUID().toString();
        saveCode(user, token, CodeType.PASSWORD_RESET, EXPIRATION_MINUTES);
        log.info("Token de réinitialisation UUID généré pour : {}", user.getEmail());
        return token;
    }

    @Override
    @Transactional
    public String generateAndStoreResetCodeMobile(User user) {
        // Pour le reset mobile, on utilise un code à 4 chiffres
        activationCodeRepository.deleteByUserAndType(user, CodeType.PASSWORD_RESET);
        activationCodeRepository.flush();

        String code = generateActivationCodeMobile();
        saveCode(user, code, CodeType.PASSWORD_RESET, EXPIRATION_MINUTES);
        log.info("Code mobile de réinitialisation (4 chiffres) généré pour : {}", user.getEmail());
        return code;
    }


    @Override
    @Transactional
    public String generateAndStoreCodeMobileWithType(User user, CodeType type) {
        activationCodeRepository.deleteByUserAndType(user, type);
        activationCodeRepository.flush();

        String code = generateActivationCodeMobile();
        saveCode(user, code, type, EXPIRATION_MINUTES);
        log.info("Code mobile d'activation ({}) à 4 chiffres généré pour : {}", type, user.getEmail());
        return code;
    }

    @Override
    @Transactional
    public String generateAndStoreAccountActivationToken(User user) {
        // Supprimer tout token ACCOUNT_ACTIVATION existant pour éviter les doublons
        activationCodeRepository.deleteByUserAndType(user, CodeType.ACCOUNT_ACTIVATION);
        activationCodeRepository.flush();

        String token = UUID.randomUUID().toString();
        // Le token d'activation de compte expire après 15 minutes
        saveCode(user, token, CodeType.ACCOUNT_ACTIVATION, EXPIRATION_MINUTES);
        log.info("Token d'activation de compte UUID généré pour : {}", user.getEmail());
        return token;
    }

    @Override
    public Optional<ActivationCode> findValidAccountActivationToken(String token) {
        return activationCodeRepository.findByCodeAndTypeAndUsedFalse(token, CodeType.ACCOUNT_ACTIVATION)
                .filter(ac -> !ac.isExpired());
    }

    // ============================================================================
    // ✅ VALIDATION ET NETTOYAGE
    // ============================================================================

    @Override
    public boolean verifyCode(User user, String code) {
        return activationCodeRepository.findByCodeAndUser(code, user)
                .map(ac -> !ac.isExpired() && !ac.isUsed())
                .orElse(false);
    }

    @Override
    public long getRemainingCooldownSecond(User user, CodeType type) {
        try {
            // 1. Récupérer le dernier code envoyé pour cet utilisateur et ce type
            Optional<ActivationCode> lastCodeOpt = activationCodeRepository
                    .findTopByUserAndTypeOrderByCreatedAtDesc(user, type);

            if (lastCodeOpt.isEmpty()) {
                return 0; // Aucun code envoyé précédemment, pas de cooldown
            }

            ActivationCode lastCode = lastCodeOpt.get();

            // 2. Calculer le temps écoulé depuis la création du dernier code (en secondes)
            long secondsSinceLastCode = Duration
                    .between(lastCode.getCreatedAt(), LocalDateTime.now())
                    .getSeconds();

            // 3. Vérifier si le délai minimum (RESEND_COOLDOWN_SECONDS) est écoulé
            if (secondsSinceLastCode < RESEND_COOLDOWN_SECONDS) {
                // Retourner le nombre de secondes restant à attendre
                return RESEND_COOLDOWN_SECONDS - secondsSinceLastCode;
            }

            // Délai écoulé, on peut renvoyer immédiatement
            return 0;

        } catch (Exception e) {
            log.error("Erreur lors du calcul du cooldown pour {}: {}", user.getEmail(), e.getMessage());
            // En cas d'erreur technique, on impose le délai par sécurité
            return RESEND_COOLDOWN_SECONDS;
        }
    }

    @Override
    @Transactional
    public void deleteCodeByUser(User user) {
        activationCodeRepository.deleteByUser(user);
    }

    @Override
    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * *") // Nettoyage tous les jours à 2h du matin
    public void cleanupExpiredCodes() {
        log.info("Démarrage du nettoyage automatique des codes OTP expirés...");
        activationCodeRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }

    // ============================================================================
    // 💾 MÉTHODE PRIVÉE - SAUVEGARDE
    // ============================================================================

    private void saveCode(User user, String code, CodeType type, int expirationMinutes) {
        ActivationCode activationCode = new ActivationCode();
        activationCode.setUser(user);
        activationCode.setCode(code);
        activationCode.setType(type);
        activationCode.setExpiryDate(LocalDateTime.now().plusMinutes(expirationMinutes));
        activationCode.setUsed(false);
        activationCodeRepository.save(activationCode);
    }
}
