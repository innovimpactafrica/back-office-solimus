package com.example.solimus.controllers;


import com.example.solimus.dtos.provider.profile.MySubscriptionDTO;
import com.example.solimus.dtos.provider.profile.ProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateLocationDTO;
import com.example.solimus.dtos.provider.profile.ProviderQuoteListDTO;
import com.example.solimus.dtos.provider.profile.QuoteDetailDTO;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.services.provider.profile.ProviderProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider/profile")
@RequiredArgsConstructor
@Tag(name = "Prestataire - Profil", description = "Gestion du profil du prestataire")
public class ProviderProfileController {

    private final ProviderService providerService;
    private final ProviderProfileService providerProfileService;

    // ============================================================
    // PROFIL
    // ============================================================
    @Operation(summary = "Obtenir mon profil")
    @GetMapping
    public ResponseEntity<ProviderProfileDTO> getMyProfile() {
        return ResponseEntity.ok(providerProfileService.getMyProfile());
    }

    @Operation(summary = "Obtenir mes informations personnelles")
    @GetMapping("/personal-info")
    public ResponseEntity<UpdateProviderProfileDTO> getPersonalInformation() {
        return ResponseEntity.ok(providerProfileService.getPersonalInformation());
    }


    @Operation(summary = "Mettre à jour mon profil")
    @PutMapping
    public ResponseEntity<Void> updateProfile(@RequestBody UpdateProviderProfileDTO dto) {
        providerProfileService.updateProfile(dto);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // PARAMÈTRES DU COMPTE
    // ============================================================

    //Localisation
    @Operation(summary = "Mettre à jour ma position GPS")
    @PutMapping("/location")
    public ResponseEntity<Void> updateLocation(@Valid @RequestBody UpdateLocationDTO dto) {
        providerProfileService.updateLocation(dto);
        return ResponseEntity.noContent().build();
    }

    //Notification
    @Operation(summary = "Activer/Désactiver les notifications")
    @PutMapping("/notifications")
    public ResponseEntity<Void> toggleNotifications() {
        providerProfileService.toggleNotifications();
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // ABONNEMENT
    // ============================================================
    @Operation(summary = "Obtenir mon abonnement")
    @GetMapping("/subscription")
    public ResponseEntity<MySubscriptionDTO> getMySubscription(@PageableDefault(page = 0, size = 10) Pageable pageable) {
        return ResponseEntity.ok(providerProfileService.getMySubscription(pageable));
    }

    // ============================================================
    // DEVIS
    // ============================================================
    @Operation(summary = "Lister mes devis (avec filtres et pagination)")
    @GetMapping("/quotes")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    public ResponseEntity<ProviderQuoteListDTO> getMyQuotes(
            @RequestParam(required = false) QuoteStatus statut,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerProfileService.getMyQuotes(statut, search, page, size));
    }

    @Operation(summary = "Voir le détail d'un devis")
    @GetMapping("/quotes/{id}")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    public ResponseEntity<QuoteDetailDTO> getQuoteDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerProfileService.getQuoteDetails(id));
    }
}
