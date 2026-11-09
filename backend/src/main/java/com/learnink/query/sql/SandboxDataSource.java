package com.learnink.query.sql;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pool de connexions dédié à la base bac à sable, distinct de la base applicative.
 * Volontairement non exposé comme {@link javax.sql.DataSource} pour ne pas interférer avec la configuration JPA.
 * Toutes les connexions sont en lecture seule ; en production l'utilisateur ne doit disposer que du droit SELECT.
 * Le pool ne s'ouvre qu'à la première requête : la base peut être (re)chargée au démarrage avant toute connexion.
 */
public class SandboxDataSource implements AutoCloseable {

    private static final long IDLE_TIMEOUT_MS = 30_000;

    private final HikariDataSource pool = new HikariDataSource();

    public SandboxDataSource(int number, SandboxProperties.Datasource properties) {
        pool.setPoolName("sandbox-" + number);
        pool.setJdbcUrl(properties.url());
        pool.setUsername(properties.username());
        pool.setPassword(properties.password());
        pool.setMaximumPoolSize(properties.maximumPoolSize());
        pool.setReadOnly(true);
        // Une connexion reste ouverte (évite une nouvelle connexion chiffrée à chaque requête), les autres sont
        // libérées après 30 s d'inactivité pour respecter le quota de connexions de l'hébergeur
        pool.setMinimumIdle(Math.min(properties.minimumIdle() == null ? 1 : properties.minimumIdle(),
                properties.maximumPoolSize()));
        pool.setIdleTimeout(IDLE_TIMEOUT_MS);
        if (properties.connectionInitSql() != null && !properties.connectionInitSql().isBlank()) {
            pool.setConnectionInitSql(properties.connectionInitSql());
        }
    }

    public Connection getConnection() throws SQLException {
        return pool.getConnection();
    }

    @Override
    public void close() {
        pool.close();
    }
}
