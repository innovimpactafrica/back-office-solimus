package com.example.solimus.services.admin.syndic;

import com.example.solimus.dtos.admin.syndic.CreateSyndicDTO;
import com.example.solimus.dtos.admin.syndic.CreateSyndicResponseDTO;
import com.example.solimus.dtos.admin.syndic.SyndicPlanOptionDTO;

import java.util.List;

public interface SyndicService {

    /**
     * Crée un compte syndic (User + SyndicProfile + SyndicSubscription en attente de paiement).
     * Le mot de passe temporaire n'est généré et envoyé par email qu'à la confirmation du paiement.
     */
    CreateSyndicResponseDTO createSyndic(CreateSyndicDTO dto);

    /**
     * Retourne les formules actives (id + nom), pour la liste déroulante du formulaire "Nouveau syndic".
     */
    List<SyndicPlanOptionDTO> listAvailablePlans();
}