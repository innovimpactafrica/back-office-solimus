package com.example.solimus.services.auth;

import com.example.solimus.entities.User;
import com.example.solimus.entities.auth.ActivationCode;
import com.example.solimus.enums.CodeType;

/**
 * Interface définissant les services de gestion des codes d'activation (OTP).
 * Utilisée pour valider l'identité par email lors de l'inscription ou de la récupération de compte.
 */
public interface ActivationCodeService {
    
    /**
     * Génère un code d'activation aléatoire à 6 chiffres.
     * @return Le code généré sous forme de chaîne de caractères.
     */
    String generateActivationCode();

    /**
     * Génère un code d'activation aléatoire à 4 chiffres (optimisé pour mobile).
     * @return Le code généré sous forme de chaîne de caractères.
     */
    String generateActivationCodeMobile();

    /**
     * Génère un nouveau code à 6 chiffres, supprime les anciens pour l'utilisateur, et stocke le nouveau.
     * @param user L'utilisateur concerné.
     * @return Le code généré et stocké.
     */
    String generateAndStoreCode(User user);

    /**
     * Génère un token UUID pour la réinitialisation du mot de passe.
     * @param user L'utilisateur concerné.
     * @return Le token généré et stocké.
     */
    String generateAndStoreResetToken(User user);

    /**
     * Génère un token UUID sécurisé pour l'activation de compte (créé par l'admin).
     * Ce token expire après 60 minutes et est lié au compte utilisateur.
     * @param user L'utilisateur dont le compte doit être activé.
     * @return Le token UUID généré et stocké.
     */
    String generateAndStoreAccountActivationToken(User user);

    /**
     * Recherche un token d'activation de compte valide (non expiré, non utilisé).
     * @param token Le token UUID à valider.
     * @return L'entité ActivationCode si valide, sinon vide.
     */
    java.util.Optional<ActivationCode> findValidAccountActivationToken(String token);

    /**
     * Génère un nouveau code à 4 chiffres (mobile), supprime les anciens pour l'utilisateur, et stocke le nouveau.
     * @param user L'utilisateur concerné.
     * @return Le code généré et stocké.
     */
    String generateAndStoreCodeMobile(User user);

    /**
     * Vérifie si le code fourni est valide (correspond à l'utilisateur et n'est pas expiré).
     * @param user L'utilisateur à vérifier.
     * @param code Le code saisi par l'utilisateur.
     * @return true si le code est valide, false sinon.
     */
    boolean verifyCode(User user, String code);

    /**
     * Supprime définitivement tous les codes associés à un utilisateur.
     * @param user L'utilisateur concerné.
     */
    void deleteCodeByUser(User user);
    
    /**
     * Calcule le temps restant (en secondes) avant de pouvoir renvoyer un code.
     * @param user L'utilisateur concerné.
     * @param type Le type de code (ACCOUNT_ACTIVATION, PASSWORD_RESET, etc.).
     * @return Le nombre de secondes à attendre (0 si prêt).
     */
    long getRemainingCooldownSecond(User user, CodeType type);

    /**
     * Supprime de la base de données tous les codes dont la date d'expiration est dépassée.
     */
    void cleanupExpiredCodes();
}
