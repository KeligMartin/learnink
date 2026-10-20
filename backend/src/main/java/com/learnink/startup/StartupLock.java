package com.learnink.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Verrou applicatif MySQL (GET_LOCK) garantissant qu'une seule instance à la fois exécute
 * les tâches de démarrage (chargement de la base bac à sable, des exercices, création de comptes).
 * Le verrou est lié à une connexion : il est libéré automatiquement si l'instance s'arrête.
 * Sans effet sur H2 (développement, une seule instance).
 */
@Component
public class StartupLock {

    private static final Logger log = LoggerFactory.getLogger(StartupLock.class);
    private static final String LOCK_NAME = "learnink_startup";
    private static final int TIMEOUT_SECONDS = 300;

    private final DataSource dataSource;
    private Connection connection;

    public StartupLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public synchronized void acquire() throws SQLException {
        Connection candidate = dataSource.getConnection();
        String product = candidate.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (!product.contains("mariadb") && !product.contains("mysql")) {
            candidate.close();
            return;
        }
        try (Statement statement = candidate.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT GET_LOCK('" + LOCK_NAME + "', " + TIMEOUT_SECONDS + ")")) {
            if (!result.next() || result.getInt(1) != 1) {
                candidate.close();
                throw new IllegalStateException("Impossible d'obtenir le verrou de démarrage après "
                        + TIMEOUT_SECONDS + " s : une autre instance est-elle bloquée ?");
            }
        }
        connection = candidate;
        log.info("Verrou de démarrage obtenu");
    }

    public synchronized void release() throws SQLException {
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')");
        } finally {
            connection.close();
            connection = null;
        }
    }
}
