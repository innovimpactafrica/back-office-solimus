package com.example.solimus.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Exécute une action (typiquement un envoi d'e-mail) seulement après le commit de la transaction
 * courante : un rollback ne déclenche jamais l'action, et l'action ne peut jamais faire échouer
 * ni rallonger la transaction métier. Toute exception est journalisée puis avalée.
 * Sans transaction active, l'action est exécutée immédiatement (même garde-fou).
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeRun(action);
                }
            });
        } else {
            safeRun(action);
        }
    }

    private static void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Action post-commit en échec (non bloquante) : {}", e.getMessage());
        }
    }
}
