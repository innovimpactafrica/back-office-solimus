package com.example.solimus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contrôles exécutés avant la création du contexte Spring, pour qu'une panne de démarrage se lise
 * en une ligne ERROR dans les logs du conteneur au lieu d'une pile de 200 lignes (ou d'un 502 muet).
 * <ol>
 *   <li>Profil « docker » (production) : variables obligatoires vides ou absentes. Seuls leurs NOMS
 *       sont journalisés.</li>
 *   <li>Base MySQL : connexion TCP à l'hôte et au port de {@code spring.datasource.url}, puis vraie
 *       connexion JDBC. L'erreur MySQL est traduite par son code (1045, 1040, 1129, 1049…). Ni le
 *       message brut, qui contient le nom d'utilisateur, ni les identifiants ne sont journalisés.</li>
 * </ol>
 * En cas d'échec, l'application s'arrête (code de sortie 1) : Swarm la redémarre, et le log dit pourquoi.
 * Contrôle de la base désactivable par {@code APP_STARTUP_DB_CHECK=false}.
 */
@Slf4j
public class StartupPreflight implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Variables sans lesquelles le profil de production ne peut pas fonctionner. */
    static final List<String> REQUIRED_IN_PRODUCTION =
            List.of("DB_HOST", "DB_NAME", "DB_USER", "DB_PASSWORD", "JWT_SECRET");

    private static final Pattern MYSQL_URL = Pattern.compile("^jdbc:mysql://([^/:?]+)(?::(\\d+))?");
    private static final int ATTEMPTS = 3;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("docker");

        if (production) {
            checkRequiredVariables(environment);
        }
        if (environment.getProperty("APP_STARTUP_DB_CHECK", Boolean.class, true)) {
            checkDatabase(environment);
        }
    }

    private static void checkRequiredVariables(ConfigurableEnvironment environment) {
        List<String> missing = new ArrayList<>();
        for (String name : REQUIRED_IN_PRODUCTION) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            String names = String.join(", ", missing);
            log.error("Démarrage impossible : variables d'environnement obligatoires vides ou absentes : {}. "
                    + "Renseignez-les dans Dokploy (onglet Environment de l'application), puis redéployez.", names);
            throw new IllegalStateException("Variables obligatoires manquantes : " + names);
        }
        String jwtSecret = environment.getProperty("JWT_SECRET", "");
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.warn("JWT_SECRET fait moins de 32 octets : la signature des jetons (HS256) sera refusée à la connexion.");
        }
    }

    private static void checkDatabase(ConfigurableEnvironment environment) {
        String url;
        try {
            url = environment.getProperty("spring.datasource.url", "");
        } catch (IllegalArgumentException e) {
            // Placeholder non résolu : la variable manquante est nommée par le message de Spring.
            log.error("Démarrage impossible : spring.datasource.url ne peut pas être construite ({}).", e.getMessage());
            throw new IllegalStateException("URL de base de données incomplète", e);
        }
        Matcher matcher = MYSQL_URL.matcher(url);
        if (!matcher.find()) {
            log.warn("Contrôle de la base ignoré : spring.datasource.url n'est pas une URL jdbc:mysql://.");
            return;
        }
        String host = matcher.group(1);
        int port = matcher.group(2) == null ? 3306 : Integer.parseInt(matcher.group(2));
        int timeoutSeconds = environment.getProperty("DB_CONNECT_TIMEOUT_SECONDS", Integer.class, 10);

        String tcpError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            tcpError = tcpProbe(host, port, timeoutSeconds);
            if (tcpError == null) {
                break;
            }
            log.warn("Base MySQL {}:{} injoignable (essai {}/{}) : {}", host, port, attempt, ATTEMPTS, tcpError);
            if (attempt < ATTEMPTS) {
                sleep(5);
            }
        }
        if (tcpError != null) {
            log.error("Démarrage impossible : la base MySQL {}:{} (DB_HOST / DB_PORT) ne répond pas depuis ce conteneur ({}). "
                    + "« timed out » : un pare-feu ignore ce serveur, autorisez son IP publique sur le port {} du serveur MySQL. "
                    + "« refused » : MySQL est arrêté ou n'écoute pas sur ce port. « UnknownHost » : DB_HOST est mal écrit.",
                    host, port, tcpError, port);
            throw new IllegalStateException("Base MySQL injoignable sur " + host + ":" + port);
        }

        Properties properties = new Properties();
        properties.setProperty("user", environment.getProperty("spring.datasource.username", ""));
        properties.setProperty("password", environment.getProperty("spring.datasource.password", ""));
        properties.setProperty("connectTimeout", String.valueOf(timeoutSeconds * 1000));
        properties.setProperty("socketTimeout", String.valueOf(timeoutSeconds * 1000));
        try (Connection ignored = DriverManager.getConnection(url, properties)) {
            log.info("Base MySQL joignable et identifiants acceptés sur {}:{}.", host, port);
        } catch (SQLException e) {
            String cause = explain(e);
            log.error("Démarrage impossible : MySQL {}:{} répond mais refuse la connexion — code {} / SQLState {} : {}",
                    host, port, e.getErrorCode(), e.getSQLState(), cause);
            throw new IllegalStateException("Connexion MySQL refusée (code " + e.getErrorCode() + ")");
        }
    }

    /** null si la connexion TCP réussit, sinon une description courte (type d'exception + message réseau). */
    private static String tcpProbe(String host, int port, int timeoutSeconds) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutSeconds * 1000);
            return null;
        } catch (UnknownHostException e) {
            return "UnknownHost : " + host;
        } catch (IOException e) {
            return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " : " + e.getMessage());
        }
    }

    /** Traduit l'erreur MySQL sans reprendre son message, qui contient le nom d'utilisateur et l'IP cliente. */
    static String explain(SQLException e) {
        return switch (e.getErrorCode()) {
            case 1045 -> "accès refusé : DB_USER / DB_PASSWORD incorrects, ou utilisateur non autorisé depuis l'IP de ce serveur.";
            case 1044 -> "accès refusé à la base DB_NAME pour cet utilisateur (droits GRANT manquants).";
            case 1049 -> "base inconnue : DB_NAME n'existe pas sur ce serveur MySQL.";
            case 1040 -> "« Too many connections » : max_connections est atteint (connexions d'autres applications ou redémarrages en boucle).";
            case 1129 -> "hôte bloqué (max_connect_errors) : exécuter FLUSH HOSTS sur le serveur MySQL.";
            case 1130 -> "cet hôte n'est pas autorisé à se connecter : créer l'utilisateur pour l'IP de ce serveur.";
            case 1203 -> "max_user_connections atteint pour cet utilisateur.";
            default -> "SQLState 08 = coupure réseau pendant l'échange (TLS, proxy, pare-feu) ; sinon voir le code MySQL ci-dessus.";
        };
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
