package com.example.solimus.dtos.payment;

import com.example.solimus.enums.Country;
import com.example.solimus.enums.SubscriptionDuration;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// [Admin] Création d'un syndic avec paiement Wave / Orange Money — mêmes champs que CreateSyndicDTO,
// sans "method" (déduit de ?operator=)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyCreateSyndicDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    private String phone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotBlank(message = "Le nom de la société est obligatoire")
    private String companyName;

    @NotBlank(message = "La ville est obligatoire")
    private String city;

    @NotNull(message = "Le pays est obligatoire")
    private Country country;

    @NotBlank(message = "L'adresse est obligatoire")
    private String address;

    private Double latitude;
    private Double longitude;

    @NotNull(message = "La formule d'abonnement est obligatoire")
    private Long syndicPlanId;

    @NotNull(message = "La durée de l'abonnement est obligatoire")
    private SubscriptionDuration duration;

    private LocalDate startDate; // optionnel — aujourd'hui si non fourni
}
