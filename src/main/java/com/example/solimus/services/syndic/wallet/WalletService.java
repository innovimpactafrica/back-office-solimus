package com.example.solimus.services.syndic.wallet;

import com.example.solimus.dtos.syndic.wallet.*;

import java.util.List;

public interface WalletService {
    List<ResidenceSimpleDTO> getSyndicResidences();
    List<BudgetItemSimpleDTO> getBudgetItemsWithoutCommonFacility(Long residenceId);
    void createWithdrawalRequest(CreateWithdrawalRequestDTO dto);
    WalletBalanceDTO getWalletBalance();
    WalletKpiDTO getWalletKpis(Long residenceId);
    WalletChartDTO getWalletChart(Long residenceId);
    WalletChartDTO getWalletChartQuarterly(Long residenceId);
    WalletResidencesOverviewResponseDTO getWalletResidencesOverview();
    WalletFlowOverviewResponseDTO getWalletFlowsOverview(Long residenceId);
    WalletFlowListResponseDTO getWalletFlows(Long residenceId, int page, int size);
    WithdrawalKpiDTO getWithdrawalKpis(Long residenceId);
    WithdrawalDetailDTO getWithdrawalDetail(Long withdrawalId);
    WithdrawalListResponseDTO getWithdrawalsList(Long residenceId, int page, int size);
}
