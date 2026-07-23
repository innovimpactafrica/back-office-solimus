package com.example.solimus.dtos.admin.syndic;

import com.example.solimus.enums.SubscriptionDuration;
import lombok.*;

import java.time.LocalDate;

// ===== DTO CRÉATION - NOUVEAU SYNDIC (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSyndicDTO {

    // Informations personnelles
    private String firstName;
    private String lastName;
    private String phone;
    private String email;

    // Informations société
    private String companyName;
    private String city;
    private String country;
    private String address;
    private Double latitude;   // rempli via l'autocomplète Google Places, optionnel
    private Double longitude;  // rempli via l'autocomplète Google Places, optionnel

    // Abonnement
    private Long syndicPlanId;
    private SubscriptionDuration duration;
    private LocalDate startDate;

    @Builder.Default
    private Boolean sendAccessByEmail = true;
}