package com.example.solimus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Conversion unique de {@code provider_wallet_transactions.category} d'ENUM MySQL vers VARCHAR(40).
 * <p>
 * Hibernate 6 crée les colonnes {@code @Enumerated(STRING)} en ENUM MySQL, et {@code ddl-auto=update}
 * ne modifie jamais une colonne existante : la nouvelle catégorie RECHARGE y serait refusée. La conversion
 * conserve toutes les valeurs existantes (mêmes chaînes). Idempotente : sans effet si la colonne est déjà
 * un VARCHAR. En cas d'échec (droit ALTER manquant), une ligne ERROR donne la requête à exécuter.
 */
@Slf4j
@Component
public class ProviderWalletCategoryColumnMigration {

    static final String ALTER_SQL =
            "ALTER TABLE provider_wallet_transactions MODIFY COLUMN category VARCHAR(40) NOT NULL";

    private final JdbcTemplate jdbcTemplate;

    public ProviderWalletCategoryColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            // Propre à MySQL (colonnes ENUM) : sans objet sur PostgreSQL, où la colonne est créée en VARCHAR
            String product = jdbcTemplate.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (product == null || !product.toLowerCase(java.util.Locale.ROOT).contains("mysql")) {
                return;
            }
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'provider_wallet_transactions' AND COLUMN_NAME = 'category'",
                    String.class);
            if (types.isEmpty() || !"enum".equalsIgnoreCase(types.get(0))) {
                return;
            }
            jdbcTemplate.execute(ALTER_SQL);
            log.info("provider_wallet_transactions.category converti d'ENUM en VARCHAR(40) (catégorie RECHARGE autorisée).");
        } catch (Exception e) {
            log.error("Conversion de provider_wallet_transactions.category impossible ({}). Les recharges de wallet "
                    + "échoueront tant que la colonne reste un ENUM. À exécuter sur la base : {};",
                    e.getClass().getSimpleName(), ALTER_SQL);
        }
    }
}
