package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.intervention.CreateQuoteDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.InterventionRequestSummaryDTO;
import com.example.solimus.dtos.provider.*;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.services.provider.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/provider")
@RequiredArgsConstructor
@Tag(name = "Provider", description = "Actions réservées aux Prestataires")
public class ProviderController {

    private final ProviderService providerService;

    @Operation(summary = "Lister les demandes d'intervention (Listing paginé et filtré)")
    @GetMapping("/available-requests")
    public ResponseEntity<Page<InterventionRequestSummaryDTO>> getAvailableRequests(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        return ResponseEntity.ok(providerService.getAvailableRequests(search, status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Obtenir le nombre total de demandes reçues")
    @GetMapping("/requests/count")
    public ResponseEntity<java.util.Map<String, Long>> getTotalRequestsCount() {
        return ResponseEntity.ok(java.util.Map.of("total", providerService.getTotalRequestsCount()));
    }

    @Operation(summary = "Récupérer le détail complet d'une demande par son ID")
    @GetMapping("/requests/{id}")
    public ResponseEntity<InterventionRequestDTO> getRequestDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getRequestDetails(id));
    }

    @Operation(summary = "Créer un devis (Brouillon ou Envoi)")
    @PostMapping("/quotes")
    public ResponseEntity<String> createQuote(@RequestBody @Valid CreateQuoteDTO dto) {
        providerService.createQuote(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Devis enregistré avec succès.");
    }

    @Operation(summary = "Lister les délais d'estimation disponibles")
    @GetMapping("/estimated-delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getEstimatedDelays() {
        return ResponseEntity.ok(providerService.getEstimatedDelays());
    }

    @Operation(summary = "Démarrer une intervention (Prestataire)")
    @PostMapping("/requests/{id}/start")
    public ResponseEntity<String> startIntervention(@PathVariable Long id) {
        providerService.startIntervention(id);
        return ResponseEntity.ok("Les travaux ont été démarrés avec succès.");
    }

    @Operation(summary = "Ajouter une photo pendant les travaux")
    @PostMapping(value = "/requests/{id}/work-photos", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> ajouterPhotoTravaux(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo) {
        
        providerService.ajouterPhotoTravaux(id, photo);
        return ResponseEntity.ok("Photo ajoutée avec succès.");
    }

    @Operation(summary = "Ajouter un commentaire sur l'intervention")
    @PostMapping("/requests/{id}/comments")
    public ResponseEntity<String> ajouterCommentaire(
            @PathVariable Long id,
            @RequestParam("commentaire") String commentaire) {
        
        providerService.ajouterCommentaire(id, commentaire);
        return ResponseEntity.ok("Commentaire ajouté avec succès.");
    }

    @Operation(summary = "Terminer l'intervention")
    @PostMapping("/requests/{id}/finish")
    public ResponseEntity<String> terminerIntervention(@PathVariable Long id) {
        providerService.terminerIntervention(id);
        return ResponseEntity.ok("L'intervention a été marquée comme terminée.");
    }

    // =========================================================================
    // PROFIL PRESTATAIRE
    // =========================================================================

    @Operation(summary = "Récupérer le profil du prestataire connecté")
    @GetMapping("/profile")
    public ResponseEntity<ProviderProfileDTO> getMyProfile() {
        return ResponseEntity.ok(providerService.getMyProfile());
    }

    @Operation(summary = "Activer ou désactiver la disponibilité (En ligne / Hors ligne)")
    @PostMapping("/profile/toggle-availability")
    public ResponseEntity<String> toggleAvailability() {
        providerService.toggleAvailability();
        return ResponseEntity.ok("Statut de disponibilité mis à jour avec succès.");
    }

    @Operation(summary = "Récupérer les informations personnelles (Édition profil)")
    @GetMapping("/profile/personal-info")
    public ResponseEntity<UpdateProviderProfileDTO> getPersonalInformation() {
        return ResponseEntity.ok(providerService.getPersonalInformation());
    }

    @Operation(summary = "Mettre à jour les informations personnelles du profil")
    @PutMapping(value = "/profile/personal-info", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> updateProfile(
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "lastName", required = false) String lastName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "interventionZone", required = false) String interventionZone,
            @RequestParam(value = "latitude", required = false) java.math.BigDecimal latitude,
            @RequestParam(value = "longitude", required = false) java.math.BigDecimal longitude,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        
       UpdateProviderProfileDTO dto =
            UpdateProviderProfileDTO.builder()
                .companyName(companyName)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .email(email)
                .interventionZone(interventionZone)
                .latitude(latitude)
                .longitude(longitude)
                .build();

        providerService.updateProfile(dto, photo);
        return ResponseEntity.ok("Profil mis à jour avec succès.");
    }

    // =========================================================================
    // MES DEVIS
    // =========================================================================

    @Operation(summary = "Récupérer la liste des devis (avec filtres et pagination)")
    @GetMapping("/quotes")
    public ResponseEntity<ProviderQuoteListDTO> getMesDevis(
            @RequestParam(required = false) QuoteStatus statut,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerService.getMesDevis(statut, search, page, size));
    }

    @Operation(summary = "Récupérer le détail d'un devis")
    @GetMapping("/quotes/{id}")
    public ResponseEntity<QuoteDetailDTO> getQuoteDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getQuoteDetails(id));
    }

    // =========================================================================
    // PORTEFEUILLE (WALLET)
    // =========================================================================

    @Operation(summary = "Récupérer les informations du portefeuille (Wallet)")
    @GetMapping("/wallet")
    public ResponseEntity<WalletDTO> getMonWallet() {
        return ResponseEntity.ok(providerService.getMonWallet());
    }

    @Operation(summary = "Demander un versement (Wave, Orange Money)")
    @PostMapping("/wallet/withdraw")
    public ResponseEntity<WithdrawalRequestDTO> demanderVersement(
            @RequestBody @Valid DemanderVersementDTO dto) {
        return ResponseEntity.ok(providerService.demanderVersement(dto));
    }

    // =========================================================================
    // TABLEAU DE BORD (DASHBOARD)
    // =========================================================================

    @Operation(summary = "Récupérer les données consolidées du tableau de bord (Dashboard)")
    @GetMapping("/dashboard")
    public ResponseEntity<ProviderDashboardDTO> getDashboard() {
        return ResponseEntity.ok(providerService.getDashboard());
    }
}
