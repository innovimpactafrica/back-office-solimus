package com.example.solimus.services.auth;

import com.example.solimus.entities.auth.TokenBlacklist;
import com.example.solimus.repositories.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Service de gestion de la liste noire des jetons (Blacklist).
 * Permet d'invalider les jetons lors de la déconnexion avant leur expiration naturelle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtService jwtService;

    /**
     * Ajoute un jeton à la liste noire.
     * Le jeton restera blacklisté jusqu'à sa date d'expiration initiale.
     */
    @Override
    @Transactional
    public void addToBlackList(String token) {
        if (isBlackListed(token)) {
            return;
        }

        TokenBlacklist blacklistEntry = new TokenBlacklist();
        blacklistEntry.setToken(token);
        blacklistEntry.setBlacklistedAt(LocalDateTime.now());
        
        // Extraction de la date d'expiration du jeton pour optimiser le stockage
        LocalDateTime expiryDate = jwtService.extractExpiration(token)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        
        blacklistEntry.setExpiryDate(expiryDate);
        
        tokenBlacklistRepository.save(blacklistEntry);
        log.info("Jeton invalidé et ajouté à la liste noire.");
    }

    /**
     * Vérifie si le jeton est présent dans la base de données des jetons invalidés.
     */
    @Override
    public boolean isBlackListed(String token) {
        return tokenBlacklistRepository.findByToken(token).isPresent();
    }

    /**
     * Tâche planifiée pour supprimer les jetons de la blacklist une fois qu'ils ont expiré
     * naturellement (ils ne sont alors plus valides de toute façon).
     * S'exécute chaque nuit à 3h.
     */
    @Override
    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {
        log.info("Démarrage de la maintenance de la liste noire des jetons...");
        tokenBlacklistRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}
