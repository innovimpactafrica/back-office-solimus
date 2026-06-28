package com.example.solimus.services.owner;

import com.example.solimus.dtos.charge.ChargeAllocationDetailDTO;
import com.example.solimus.dtos.charge.MyChargesSummaryDTO;
import com.example.solimus.enums.ChargeStatus;

// Service pour la gestion des charges côté copropriétaire
public interface CoproprietaireChargeService {
    MyChargesSummaryDTO getMesCharges(Integer page, Integer size, ChargeStatus status, String search);
    ChargeAllocationDetailDTO getChargeDetail(Long allocationId);
}
