package com.example.solimus.enums;

/**
 * Type de téléphone d'où provient un token FCM — envoyé par l'app mobile
 * lors de l'enregistrement du device token (POST /api/account/device-token).
 */
public enum DeviceType {
    ANDROID,
    IOS
}
