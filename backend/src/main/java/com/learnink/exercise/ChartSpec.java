package com.learnink.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Graphique associé à un exercice : les {@code labelColumns} premières colonnes du résultat forment le libellé
 * de chaque point, les colonnes numériques suivantes sont tracées comme séries.
 */
@Embeddable
public class ChartSpec {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "chart_type", length = 20)
    private ChartType type;

    @Column(name = "chart_label_columns")
    private Integer labelColumns;

    protected ChartSpec() {
    }

    public ChartSpec(ChartType type, Integer labelColumns) {
        this.type = type;
        this.labelColumns = labelColumns;
    }

    public ChartType getType() {
        return type;
    }

    public int getLabelColumns() {
        return labelColumns == null ? 1 : labelColumns;
    }
}
