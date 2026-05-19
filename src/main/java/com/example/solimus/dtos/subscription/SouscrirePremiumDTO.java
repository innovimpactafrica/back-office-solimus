package com.example.solimus.dtos.subscription;

import com.example.solimus.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SouscrirePremiumDTO {

    @NotNull(message = "Le moyen de paiement est obligatoire")
    private PaymentMethod moyenPaiement;

    private boolean renouvellementAuto = true;
}
