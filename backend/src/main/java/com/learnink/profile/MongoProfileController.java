package com.learnink.profile;

import com.learnink.common.InvalidRequestException;
import com.learnink.profile.MongoProfileService.MongoProfileDto;
import com.learnink.query.mongo.MongoDatasets;
import com.learnink.security.AuthenticatedUser;
import com.learnink.user.Role;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/me/mongodb")
public class MongoProfileController {

    public record MongoUriRequest(@NotBlank @Size(max = 500) String uri) {
    }

    private final MongoProfileService service;
    private final MongoDatasets datasets;

    public MongoProfileController(MongoProfileService service, MongoDatasets datasets) {
        this.service = service;
        this.datasets = datasets;
    }

    @GetMapping
    public MongoProfileDto status(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.status(user.id());
    }

    @PutMapping
    public MongoProfileDto save(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody MongoUriRequest request) {
        return service.save(user.id(), request.uri());
    }

    @PostMapping("/verify")
    public MongoProfileDto verify(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.verify(user.id());
    }

    @DeleteMapping
    public MongoProfileDto remove(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.remove(user.id());
    }

    /**
     * Jeu de données à importer dans sa base : une archive avec un fichier JSON par collection.
     * Un étudiant ne télécharge que celui de sa source de données ; l'enseignant peut les télécharger tous.
     */
    @GetMapping("/dataset/{datasource}")
    public void dataset(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int datasource,
                        HttpServletResponse response) throws IOException {
        if (user.role() != Role.TEACHER && user.datasource() != datasource) {
            throw new InvalidRequestException("Vous ne pouvez télécharger que le jeu de données qui vous est attribué.");
        }
        String name = datasets.dataset(datasource).name();
        List<Resource> files = datasets.files(datasource);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"learnink-" + name + "-mongodb.zip\"");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("LISEZMOI.txt"));
            zip.write(readme(name, files).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Resource file : files) {
                zip.putNextEntry(new ZipEntry(name + "/" + file.getFilename()));
                try (InputStream input = file.getInputStream()) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private static String readme(String name, List<Resource> files) {
        StringBuilder text = new StringBuilder("""
                Jeu de données « %s » pour MongoDB (Learnink)
                ==============================================

                Chaque fichier JSON contient une collection : le nom du fichier est le nom de la collection.
                Importez-les tous dans une base nommée « learnink ».

                Avec MongoDB Compass
                  1. Connectez-vous à votre cluster, créez la base « learnink » et une première collection.
                  2. Pour chaque fichier : créez la collection du même nom, puis ADD DATA > Import JSON or CSV file.

                Avec mongoimport (MongoDB Database Tools), depuis ce dossier :
                """.formatted(name));
        for (Resource file : files) {
            String collection = MongoDatasets.collectionName(file);
            text.append("  mongoimport --uri \"<chaîne de connexion>\" --db learnink --collection ").append(collection)
                    .append(" --jsonArray --drop --file ").append(name).append('/').append(file.getFilename()).append('\n');
        }
        return text.toString();
    }
}
