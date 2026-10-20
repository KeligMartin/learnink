package com.learnink.query.sql;

import com.learnink.startup.StartupOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Charge chaque base bac à sable (schéma + données) au démarrage, selon {@link SandboxInitMode}.
 * En mode IF_CHANGED, l'empreinte SHA-256 des scripts est conservée par base (ligne n de la table sandbox_state
 * de la base applicative) : une base n'est rechargée que si le jeu de données a changé.
 */
@Component
@Order(StartupOrder.SANDBOX)
public class SandboxLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxLoader.class);

    private final SandboxProperties properties;
    private final ResourceLoader resourceLoader;
    private final JdbcTemplate applicationDatabase;

    public SandboxLoader(SandboxProperties properties, ResourceLoader resourceLoader, JdbcTemplate applicationDatabase) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.applicationDatabase = applicationDatabase;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException, SQLException {
        SandboxInitMode mode = properties.effectiveInitMode();
        if (mode == SandboxInitMode.NEVER) {
            return;
        }
        for (Map.Entry<Integer, SandboxProperties.Datasource> entry : properties.configuredDatasources().entrySet()) {
            int number = entry.getKey();
            List<Resource> scripts = entry.getValue().scriptsOrEmpty().stream().map(resourceLoader::getResource).toList();
            if (scripts.isEmpty()) {
                continue;
            }
            String checksum = checksum(scripts);
            if (mode == SandboxInitMode.IF_CHANGED && Optional.of(checksum).equals(storedChecksum(number))) {
                log.info("Base bac à sable n°{} à jour, aucun rechargement", number);
                continue;
            }
            long start = System.currentTimeMillis();
            load(entry.getValue(), scripts);
            if (mode == SandboxInitMode.IF_CHANGED) {
                storeChecksum(number, checksum);
            }
            log.info("Base bac à sable n°{} chargée en {} ms", number, System.currentTimeMillis() - start);
        }
    }

    private void load(SandboxProperties.Datasource datasource, List<Resource> scripts) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                datasource.loadUrl(), datasource.adminUsername(), datasource.adminPassword())) {
            for (Resource script : scripts) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(script, StandardCharsets.UTF_8));
            }
        }
    }

    private Optional<String> storedChecksum(int number) {
        return applicationDatabase.query("SELECT checksum FROM sandbox_state WHERE id = ?",
                (row, index) -> row.getString(1), number).stream().findFirst();
    }

    private void storeChecksum(int number, String checksum) {
        int updated = applicationDatabase.update(
                "UPDATE sandbox_state SET checksum = ?, loaded_at = ? WHERE id = ?", checksum, LocalDateTime.now(), number);
        if (updated == 0) {
            applicationDatabase.update("INSERT INTO sandbox_state (id, checksum, loaded_at) VALUES (?, ?, ?)",
                    number, checksum, LocalDateTime.now());
        }
    }

    private static String checksum(List<Resource> scripts) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Resource script : scripts) {
                try (InputStream input = script.getInputStream()) {
                    digest.update(input.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
