package com.example.solimus.config;

import com.example.solimus.services.auth.JwtService;
import com.example.solimus.services.auth.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Filtre JWT pour valider les tokens à chaque requête.
 * Ce filtre intercepte les requêtes entrantes, extrait le token Bearer,
 * vérifie sa validité (signature, expiration, blacklist) et établit
 * le contexte de sécurité Spring Security.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // ============================================================================
    // 📦 DÉPENDANCES
    // ============================================================================
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    // ============================================================================
    // 🔍 FILTRAGE DES REQUÊTES
    // ============================================================================

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ════════════════════════════════════════════════════════════════════════
        // 🔥 EXCLURE L'ACTUATOR DU FILTRE JWT (permet Basic Auth)
        // ════════════════════════════════════════════════════════════════════════
        String path = request.getServletPath();
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }
        // ════════════════════════════════════════════════════════════════════════

        String requestPath = request.getRequestURI();

        // 1. Laisser passer les routes publiques sans validation
        if (isPublicPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extraire le header Authorization
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 3. Vérifier si le format du token est correct (Bearer)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        // 4. Vérifier si le token a été invalidé (Blacklist / Déconnexion)
        if (tokenBlacklistService.isBlackListed(jwt)) {
            sendUnauthorizedResponse(response, "Token invalide (déconnecté). Veuillez vous reconnecter.");
            return;
        }

        try {
            // 5. Extraire l'identité de l'utilisateur (email)
            userEmail = jwtService.extractUsername(jwt);

            // 6. Si l'utilisateur n'est pas encore authentifié dans le contexte actuel
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 7. Valider la signature et l'expiration du token
                if (jwtService.isTokenValid(jwt)) {
                    // Extraire le rôle pour les autorisations
                    String role = jwtService.extractRole(jwt);
                    // Si le rôle commence déjà par ROLE_, on l'utilise tel quel, sinon on l'ajoute
                    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

                    // Créer l'objet d'authentification pour Spring Security
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null, // Pas de mot de passe requis à ce stade
                            Collections.singletonList(new SimpleGrantedAuthority(authority))
                    );

                    log.info("🔐 Authentification réussie pour {} avec l'autorité : {}", userEmail, authority);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Enregistrer l'utilisateur dans le contexte de sécurité
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            // Continuer la chaîne de filtres
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expiré : {}", e.getMessage());
            sendUnauthorizedResponse(response, "Token expiré. Veuillez vous reconnecter.");
        } catch (JwtException e) {
            log.warn("Validation JWT échouée : {}", e.getMessage());
            sendUnauthorizedResponse(response, "Token invalide.");
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la validation JWT", e);
            sendUnauthorizedResponse(response, "Erreur d'authentification.");
        }
    }

    // ============================================================================
    // 🔓 VÉRIFICATION DES ROUTES PUBLIQUES
    // ============================================================================

    /**
     * Identifie les chemins qui ne nécessitent pas de jeton d'accès.
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/")
                && !path.equals("/api/auth/logout")
                && !path.equals("/api/auth/me")
                || path.startsWith("/api/payments/bridge/")
                || path.startsWith("/api/payments/intouch/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    // ============================================================================
    // ❌ GESTION DES ERREURS
    // ============================================================================

    /**
     * Envoie une réponse d'erreur 401 au format JSON en cas d'échec d'authentification.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "UNAUTHORIZED");
        errorResponse.put("message", message);
        errorResponse.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorResponse.put("timestamp", System.currentTimeMillis());

        new ObjectMapper().writeValue(response.getWriter(), errorResponse);
    }
}