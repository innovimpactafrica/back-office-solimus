package com.example.solimus.services.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Implémentation du service JWT pour SOLIMUS.
 * Gère le cycle de vie des jetons d'accès (génération, extraction des claims et validation).
 */
@Service
public class JwtServiceImpl implements JwtService {

    // ============================================================================
    // ⚙️ CONFIGURATION
    // ============================================================================

    @Value("${jwt.secret:solimus-secret-key-256-bits-minimum-required-for-security-2025}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // 24 heures par défaut
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 jours par défaut
    private long refreshExpiration;

    // ============================================================================
    // 🔑 CLÉ DE SIGNATURE
    // ============================================================================

    /**
     * Génère la clé de signature cryptographique à partir de la chaîne secrète.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    // ============================================================================
    // 🎫 GÉNÉRATION DE JETONS
    // ============================================================================

    /**
     * Crée un token JWT incluant l'email, le rôle et l'ID de l'utilisateur.
     */
    @Override
    public String generateToken(String email, String role, Long id) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("id", id);
        return createToken(claims, email, expiration);
    }

    /**
     * Génère un token de rafraîchissement avec une durée de vie plus longue.
     */
    @Override
    public String generateRefreshToken(String email) {
        return createToken(new HashMap<>(), email, refreshExpiration);
    }

    /**
     * Construit le jeton avec les claims, le sujet et la signature.
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationTime) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ============================================================================
    // 🔍 EXTRACTION DES DONNÉES (CLAIMS)
    // ============================================================================

    /**
     * Récupère l'ensemble des données contenues dans le token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    @Override
    public String extractUsername(String token) {
        return extractEmail(token);
    }

    @Override
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // ============================================================================
    // ✅ VALIDATION
    // ============================================================================

    /**
     * Vérifie si le jeton a dépassé sa date de fin de validité.
     */
    @Override
    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Vérifie la structure et la validité temporelle du jeton.
     */
    @Override
    public Boolean isTokenValid(String token) {
        try {
            if (token == null || token.split("\\.").length != 3) {
                return false;
            }
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
