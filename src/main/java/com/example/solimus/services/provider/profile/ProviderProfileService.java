package com.example.solimus.services.provider.profile;


import com.example.solimus.dtos.provider.profile.MySubscriptionDTO;
import com.example.solimus.dtos.provider.profile.ProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateLocationDTO;
import com.example.solimus.dtos.provider.profile.UpdateProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.ProviderQuoteListDTO;
import com.example.solimus.dtos.provider.profile.QuoteDetailDTO;
import com.example.solimus.enums.QuoteStatus;
import org.springframework.data.domain.Pageable;

public interface ProviderProfileService {

    // ============================================================
    // Gestion Notifications
    // ============================================================
    void toggleNotifications();

    // ============================================================
    // Gestion Localisation
    // ============================================================
    void updateLocation(UpdateLocationDTO dto);

    // ============================================================
    // Gestion Profil
    // ============================================================
    ProviderProfileDTO getMyProfile();
    UpdateProviderProfileDTO getPersonalInformation();
    void updateProfile(UpdateProviderProfileDTO dto);

    // ============================================================
    // Gestion Abonnement
    // ============================================================
    MySubscriptionDTO getMySubscription(Pageable pageable);

    // ============================================================
    // Gestion Devis
    // ============================================================
    ProviderQuoteListDTO getMyQuotes(QuoteStatus statut, String search, int page, int size);
    QuoteDetailDTO getQuoteDetails(Long quoteId);

}
