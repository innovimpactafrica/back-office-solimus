package com.example.solimus.dtos.admin.syndic;

import com.example.solimus.enums.Country;
import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionDuration;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    private String phone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    // Informations société
    @NotBlank(message = "Le nom de la société est obligatoire")
    private String companyName;

    @NotBlank(message = "La ville est obligatoire")
    private String city;

    @NotNull(message = "Le pays est obligatoire")
    private Country country;

    @NotBlank(message = "L'adresse est obligatoire")
    private String address;

    private Double latitude;   // rempli via l'autocomplète Google Places, optionnel
    private Double longitude;  // rempli via l'autocomplète Google Places, optionnel

    // Abonnement
    @NotNull(message = "La formule d'abonnement est obligatoire")
    private Long syndicPlanId;

    @NotNull(message = "La durée de l'abonnement est obligatoire")
    private SubscriptionDuration duration;

    // Choisi par l'admin dans son propre formulaire avant la redirection TouchPay
    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod method;

    private LocalDate startDate; // optionnel — aujourd'hui si non fourni
}