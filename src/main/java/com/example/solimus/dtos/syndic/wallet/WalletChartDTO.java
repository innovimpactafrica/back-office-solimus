package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletChartDTO {

    private List<WalletChartPeriodDTO> periods;
}
