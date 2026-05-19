package com.example.solimus.services.geolocation;

import org.springframework.stereotype.Service;

/**
 * Service utilitaire pour les calculs géographiques.
 * Utilise la formule de Haversine pour calculer les distances sur la courbure de la Terre.
 */
@Service
public class GeolocationService {

    // Rayon moyen de la Terre en kilomètres
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calcule la distance en kilomètres entre deux points GPS.
     * 
     * @param lat1 Latitude du point A
     * @param lon1 Longitude du point A
     * @param lat2 Latitude du point B
     * @param lon2 Longitude du point B
     * @return Distance en km
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // 1. On convertit les différences de coordonnées en Radians (nécessaire pour les fonctions sinus/cosinus)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        // 2. Application de la formule de Haversine
        // 'a' représente le carré de la demi-corde entre les points
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        // 'c' représente la distance angulaire en radians
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 3. On multiplie par le rayon de la Terre pour obtenir la distance réelle en km
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Vérifie si un prestataire se trouve dans le rayon autorisé (ex: 30 km).
     */
    public boolean isWithinRadius(double lat1, double lon1, double lat2, double lon2, double radiusKm) {
        return calculateDistance(lat1, lon1, lat2, lon2) <= radiusKm;
    }
}
