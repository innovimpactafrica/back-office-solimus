package com.example.solimus.services.admin.profile;

import com.example.solimus.dtos.admin.profile.AdminChangePasswordDTO;
import com.example.solimus.dtos.admin.profile.AdminProfileDTO;
import com.example.solimus.dtos.admin.profile.ChangePasswordResultDTO;
import com.example.solimus.dtos.admin.profile.UpdateAdminProfileDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProfileServiceImpl implements AdminProfileService {

    private final UserRepository userRepository;
    private final MinioService minioService;
    private final PasswordEncoder passwordEncoder;

    // Même règle de robustesse que ChangePasswordDTO (syndic) — on la réutilise, pas de nouvelle règle
    private static final int MIN_PASSWORD_LENGTH = 6;

    private static final List<String> ALLOWED_PHOTO_CONTENT_TYPES = List.of("image/jpeg", "image/png");
    private static final long MAX_PHOTO_SIZE_BYTES = 2 * 1024 * 1024; // 2 Mo

    // =========================================================================
    // BLOC 1 — INFORMATIONS PERSONNELLES
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminProfileDTO getMyProfile() {
        return toDTO(getCurrentAdmin());
    }

    @Override
    @Transactional
    public AdminProfileDTO updateMyProfile(UpdateAdminProfileDTO dto) {
        User currentAdmin = getCurrentAdmin();

        // Mise à jour partielle : seuls les champs réellement envoyés (non null) sont modifiés

        if (dto.getFullName() != null) {
            // Le prénom = premier mot, le nom = reste (gère les noms composés)
            String[] names = splitFullName(dto.getFullName());
            currentAdmin.setFirstName(names[0]);
            currentAdmin.setLastName(names[1]);
        }

        if (dto.getEmail() != null) {
            // Vérifier l'unicité de l'email si modifié (parmi les autres comptes)
            if (!dto.getEmail().equalsIgnoreCase(currentAdmin.getEmail())
                    && userRepository.existsByEmailAndIdNot(dto.getEmail(), currentAdmin.getId())) {
                throw new EmailAlreadyExistsException("Cet email est déjà utilisé par un autre compte.");
            }
            currentAdmin.setEmail(dto.getEmail());
        }

        if (dto.getPhone() != null) {
            // Vérifier l'unicité du téléphone si modifié (parmi les autres comptes)
            if (!dto.getPhone().equals(currentAdmin.getPhone())
                    && userRepository.existsByPhoneAndIdNot(dto.getPhone(), currentAdmin.getId())) {
                throw new PhoneAlreadyExistsException("Ce numéro de téléphone est déjà utilisé par un autre compte.");
            }
            currentAdmin.setPhone(dto.getPhone());
        }

        User saved = userRepository.save(currentAdmin);
        log.info("Profil admin mis à jour : {}", saved.getEmail());
        return toDTO(saved);
    }

    @Override
    @Transactional
    public AdminProfileDTO updateProfilePhoto(MultipartFile photo) {
        User currentAdmin = getCurrentAdmin();

        if (photo == null || photo.isEmpty()) {
            throw new BadRequestException("Aucune photo fournie");
        }
        if (!ALLOWED_PHOTO_CONTENT_TYPES.contains(photo.getContentType())) {
            throw new BadRequestException("Format non supporté (JPG ou PNG uniquement)");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new BadRequestException("Fichier trop volumineux (max 2 Mo)");
        }

        String photoUrl = minioService.uploadFile(photo, "admin-photos");
        currentAdmin.setProfilePhotoUrl(photoUrl);

        User saved = userRepository.save(currentAdmin);
        log.info("Photo de profil mise à jour pour l'admin : {}", saved.getEmail());
        return toDTO(saved);
    }

    // =========================================================================
    // BLOC 3 — CHANGEMENT DE MOT DE PASSE
    // =========================================================================

    @Override
    @Transactional
    public ChangePasswordResultDTO changePassword(AdminChangePasswordDTO dto) {
        User currentAdmin = getCurrentAdmin();

        // 1. newPassword doit correspondre à confirmPassword
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BadRequestException("Les mots de passe ne correspondent pas");
        }

        // 2. currentPassword doit correspondre au mot de passe actuellement stocké
        if (!passwordEncoder.matches(dto.getCurrentPassword(), currentAdmin.getPassword())) {
            throw new BadRequestException("Mot de passe actuel incorrect");
        }

        // 3. newPassword doit respecter la règle de robustesse déjà en place
        if (dto.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Le nouveau mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères");
        }

        currentAdmin.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(currentAdmin);
        log.info("Mot de passe changé pour l'admin : {}", currentAdmin.getEmail());

        return ChangePasswordResultDTO.builder()
                .success(true)
                .message("Mot de passe modifié avec succès")
                .build();
    }

    // =========================================================================
    // Méthodes Utilitaires
    // =========================================================================

    private User getCurrentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur introuvable"));
    }

    // Le premier mot devient le prénom, le reste (préservé tel quel) devient le nom —
    // gère les noms composés sans les couper au milieu
    private String[] splitFullName(String nomComplet) {
        String trimmed = nomComplet.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex == -1) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, spaceIndex), trimmed.substring(spaceIndex + 1).trim()};
    }

    private AdminProfileDTO toDTO(User admin) {
        return AdminProfileDTO.builder()
                .photoUrl(admin.getProfilePhotoUrl())
                .fullName(admin.getFirstName() + " " + admin.getLastName())
                .role(admin.getRole().getName().getLabel())
                .email(admin.getEmail())
                .phone(admin.getPhone())
                .build();
    }
}