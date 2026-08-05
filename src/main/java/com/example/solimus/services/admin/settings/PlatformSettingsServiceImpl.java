package com.example.solimus.services.admin.settings;

import com.example.solimus.dtos.admin.settings.PlatformSettingsDTO;
import com.example.solimus.dtos.admin.settings.UpdatePlatformSettingsDTO;
import com.example.solimus.entities.PlatformSettings;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.PlatformSettingsRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsServiceImpl implements PlatformSettingsService {

    private final PlatformSettingsRepository platformSettingsRepository;
    private final UserRepository userRepository;

    // Identifiant fixe de l'unique ligne de réglages — jamais une autre valeur, jamais une deuxième ligne
    private static final Long SETTINGS_ID = 1L;

    // Valeurs posées automatiquement à la toute première consultation, si aucun réglage n'existe encore
    private static final String DEFAULT_PLATFORM_NAME = "SOLIMUS";
    private static final String DEFAULT_WEBSITE = "www.solimus-property.com";
    private static final String DEFAULT_SUPPORT_EMAIL = "support@solimus.com";
    private static final String DEFAULT_SUPPORT_PHONE = "+221 71 000 00 00";

    @Override
    @Transactional
    public PlatformSettingsDTO getPlatformSettings() {

        // Cherche la ligne existante ; si absente (première utilisation), la crée avec les valeurs par défaut
        PlatformSettings settings = platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);

        return toDTO(settings);
    }

    @Override
    @Transactional
    public PlatformSettingsDTO updatePlatformSettings(UpdatePlatformSettingsDTO dto) {

        // Récupère la ligne existante, ou la crée avec les valeurs par défaut si elle n'existait pas encore
        PlatformSettings settings = platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);

        // Mise à jour partielle : seuls les champs réellement envoyés sont modifiés
        if (dto.getPlatformName() != null) settings.setPlatformName(dto.getPlatformName());
        if (dto.getWebsite() != null) settings.setWebsite(dto.getWebsite());
        if (dto.getSupportEmail() != null) settings.setSupportEmail(dto.getSupportEmail());
        if (dto.getSupportPhone() != null) settings.setSupportPhone(dto.getSupportPhone());

        // Trace quel admin a fait cette modification
        settings.setUpdatedBy(getCurrentAdmin());

        PlatformSettings saved = platformSettingsRepository.save(settings);

        return toDTO(saved);
    }

    // Crée la ligne unique de réglages, avec l'id fixe et les valeurs par défaut — appelée seulement
    // quand aucune ligne n'existe encore en base
    private PlatformSettings createDefaultSettings() {

        PlatformSettings settings = new PlatformSettings();
        // Force l'id à 1 : c'est ce qui garantit qu'il n'y aura jamais qu'une seule ligne
        settings.setId(SETTINGS_ID);
        settings.setPlatformName(DEFAULT_PLATFORM_NAME);
        settings.setWebsite(DEFAULT_WEBSITE);
        settings.setSupportEmail(DEFAULT_SUPPORT_EMAIL);
        settings.setSupportPhone(DEFAULT_SUPPORT_PHONE);

        return platformSettingsRepository.save(settings);
    }

    // Récupère l'admin actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur introuvable"));
    }

    private PlatformSettingsDTO toDTO(PlatformSettings settings) {
        return PlatformSettingsDTO.builder()
                .platformName(settings.getPlatformName())
                .website(settings.getWebsite())
                .supportEmail(settings.getSupportEmail())
                .supportPhone(settings.getSupportPhone())
                .build();
    }
}