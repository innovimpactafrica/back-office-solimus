package com.example.solimus.services.admin.settings;

import com.example.solimus.dtos.admin.settings.UpdateWithdrawalSettingsDTO;
import com.example.solimus.dtos.admin.settings.WithdrawalSettingsDTO;
import com.example.solimus.entities.User;
import com.example.solimus.entities.WithdrawalSettings;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.repositories.WithdrawalSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WithdrawalSettingsServiceImpl implements WithdrawalSettingsService {

    private final WithdrawalSettingsRepository withdrawalSettingsRepository;
    private final UserRepository userRepository;

    // Identifiant fixe de l'unique ligne de réglages — jamais une autre valeur, jamais une deuxième ligne
    private static final Long SETTINGS_ID = 1L;

    // Valeur posée automatiquement à la toute première consultation, si aucun réglage n'existe encore
    private static final BigDecimal DEFAULT_MONTHLY_LIMIT = BigDecimal.valueOf(20_000_000);

    @Override
    @Transactional
    public WithdrawalSettingsDTO getMonthlyLimit() {

        // Cherche la ligne existante ; si absente (première utilisation), la crée avec la valeur par défaut
        WithdrawalSettings settings = withdrawalSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);

        // Convertit l'entité en DTO de réponse
        return toDTO(settings);
    }

    @Override
    @Transactional
    public WithdrawalSettingsDTO updateMonthlyLimit(UpdateWithdrawalSettingsDTO dto) {

        // Récupère la ligne existante, ou la crée avec la valeur par défaut si elle n'existait pas encore
        WithdrawalSettings settings = withdrawalSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);

        // Applique la nouvelle limite envoyée par l'admin
        settings.setMonthlyLimit(dto.getMonthlyLimit());

        // Trace quel admin a fait cette modification 
        settings.setUpdatedBy(getCurrentAdmin());

        // Persiste les changements
        WithdrawalSettings saved = withdrawalSettingsRepository.save(settings);

        return toDTO(saved);
    }

    // Crée la ligne unique de réglages, avec l'id fixe et la valeur par défaut — appelée seulement
    // quand aucune ligne n'existe encore en base
    private WithdrawalSettings createDefaultSettings() {

        WithdrawalSettings settings = new WithdrawalSettings();
        // Force l'id à 1 : c'est ce qui garantit qu'il n'y aura jamais qu'une seule ligne
        settings.setId(SETTINGS_ID);
        settings.setMonthlyLimit(DEFAULT_MONTHLY_LIMIT);

        return withdrawalSettingsRepository.save(settings);
    }

    // Récupère l'admin actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentAdmin() {
        // Récupère l'email stocké dans le token JWT de l'utilisateur connecté
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // Recherche l'utilisateur correspondant à cet email
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur introuvable"));
    }

    // Convertit l'entité WithdrawalSettings en DTO de réponse
    private WithdrawalSettingsDTO toDTO(WithdrawalSettings settings) {
        return WithdrawalSettingsDTO.builder()
                .monthlyLimit(settings.getMonthlyLimit())
                .build();
    }
}