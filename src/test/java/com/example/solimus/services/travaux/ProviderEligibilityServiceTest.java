package com.example.solimus.services.travaux;

import com.example.solimus.dtos.provider.eligibility.ProviderEligibilityDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.services.provider.eligibility.ProviderEligibilityService;
import com.example.solimus.services.provider.wallet.WalletBalanceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderEligibilityServiceTest {

    private final WalletBalanceService wallet = mock(WalletBalanceService.class);
    private final ProviderEligibilityService service = new ProviderEligibilityService(wallet, new BigDecimal("1000"));

    @Test
    void exactlyOneThousandIsNotEnough() {
        when(wallet.getCurrentBalance(1L)).thenReturn(new BigDecimal("1000"));
        ProviderEligibilityDTO dto = service.getEligibility(user(1L));
        assertThat(dto.isEligible()).isFalse();
        assertThat(dto.getMissingAmount()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> service.assertEligible(user(1L))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aboveOneThousandIsEnough() {
        when(wallet.getCurrentBalance(2L)).thenReturn(new BigDecimal("1001"));
        assertThat(service.isEligible(2L)).isTrue();
        assertThat(service.getEligibility(user(2L)).getMissingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void noWalletMeansNoAccess() {
        when(wallet.getCurrentBalance(3L)).thenReturn(null);
        assertThat(service.isEligible(3L)).isFalse();
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }
}
