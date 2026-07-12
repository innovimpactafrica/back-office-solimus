package com.example.solimus.dtos.owner.charge;

import com.example.solimus.enums.ChargePaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitierPaiementChargeDTO {
    @NotNull
    private ChargePaymentMethod method; // WAVE, ORANGE_MONEY, CARTE_BANCAIRE
}
