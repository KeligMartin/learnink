package com.learnink.admin;

import com.learnink.admin.AdminDtos.DatasourceDto;
import com.learnink.common.InvalidRequestException;
import com.learnink.query.sql.SandboxProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** Sources de données configurées, proposées à l'enseignant pour l'affectation des étudiants. */
@Component
public class DatasourceCatalog {

    private final SandboxProperties properties;

    public DatasourceCatalog(SandboxProperties properties) {
        this.properties = properties;
    }

    public List<DatasourceDto> list() {
        return properties.configuredDatasources().entrySet().stream()
                .map(entry -> new DatasourceDto(entry.getKey(),
                        entry.getValue().name() == null ? "Source " + entry.getKey() : entry.getValue().name()))
                .toList();
    }

    public void assertExists(int number) {
        if (!properties.configuredDatasources().containsKey(number)) {
            throw new InvalidRequestException("La source de données n°" + number + " n'est pas configurée.");
        }
    }
}
