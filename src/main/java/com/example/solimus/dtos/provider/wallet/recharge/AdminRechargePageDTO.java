package com.example.solimus.dtos.provider.wallet.recharge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRechargePageDTO {
    /** Badge : recharges en attente de validation, toutes pages confondues. */
    private long pendingValidationCount;
    private Page<ProviderWalletRechargeDTO> recharges;
}
