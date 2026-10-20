package com.learnink.query;

import java.util.List;

public record SchemaDescription(List<Table> tables) {

    public record Table(String name, List<Column> columns) {
    }

    /**
     * @param primaryKey vrai si la colonne fait partie de la clé primaire
     * @param references colonne référencée ("table.colonne") si la colonne est une clé étrangère
     */
    public record Column(String name, String type, boolean primaryKey, String references) {
    }
}
