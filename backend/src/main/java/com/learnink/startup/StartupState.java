package com.learnink.startup;

import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Mémorise, dans la table startup_state de la base applicative, l'empreinte des fichiers traités par une tâche
 * de démarrage : une tâche dont les fichiers n'ont pas changé peut être sautée (démarrages à froid plus rapides).
 */
@Component
public class StartupState {

    private final JdbcTemplate database;

    public StartupState(JdbcTemplate database) {
        this.database = database;
    }

    public boolean isUpToDate(String task, String checksum) {
        return Optional.of(checksum).equals(database.query("SELECT checksum FROM startup_state WHERE task = ?",
                (row, index) -> row.getString(1), task).stream().findFirst());
    }

    public void markDone(String task, String checksum) {
        int updated = database.update("UPDATE startup_state SET checksum = ?, updated_at = ? WHERE task = ?",
                checksum, LocalDateTime.now(), task);
        if (updated == 0) {
            database.update("INSERT INTO startup_state (task, checksum, updated_at) VALUES (?, ?, ?)",
                    task, checksum, LocalDateTime.now());
        }
    }

    /** Empreinte SHA-256 du contenu des fichiers, indépendante de leur ordre de découverte. */
    public static String checksum(Resource... resources) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Resource[] sorted = resources.clone();
            Arrays.sort(sorted, Comparator.comparing(resource -> String.valueOf(resource.getFilename())));
            for (Resource resource : sorted) {
                digest.update(String.valueOf(resource.getFilename()).getBytes());
                try (InputStream input = resource.getInputStream()) {
                    digest.update(input.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
