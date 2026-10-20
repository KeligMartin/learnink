package com.learnink.exercise;

import com.learnink.query.QueryEngine;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant fonctionnel stable, utilisé pour la mise à jour des exercices depuis les fichiers de seed. */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private QueryEngine engine;

    /** Source de données (jeu de données) sur laquelle porte l'exercice. */
    @Column(nullable = false, columnDefinition = "integer default 1")
    private int datasource = 1;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String statement;

    @Column(nullable = false, length = 4000)
    private String referenceQuery;

    /** Si vrai, l'ordre des lignes du résultat est pris en compte dans la correction. */
    @Column(nullable = false)
    private boolean orderMatters;

    /** Graphique à afficher avec le résultat (exercices statistiques), absent sinon. */
    @Embedded
    private ChartSpec chart;

    /** Exercice retiré des fichiers de seed : masqué mais conservé avec l'historique des tentatives. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived;

    protected Exercise() {
    }

    public Exercise(String code) {
        this.code = code;
    }

    public void update(QueryEngine engine, int datasource, int level, int position, String title, String statement,
                       String referenceQuery, boolean orderMatters, ChartSpec chart) {
        this.engine = engine;
        this.datasource = datasource;
        this.level = level;
        this.position = position;
        this.title = title;
        this.statement = statement;
        this.referenceQuery = referenceQuery;
        this.orderMatters = orderMatters;
        this.chart = chart;
        this.archived = false;
    }

    public void archive() {
        this.archived = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public QueryEngine getEngine() {
        return engine;
    }

    public int getDatasource() {
        return datasource;
    }

    public int getLevel() {
        return level;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getStatement() {
        return statement;
    }

    public String getReferenceQuery() {
        return referenceQuery;
    }

    public boolean isOrderMatters() {
        return orderMatters;
    }

    public ChartSpec getChart() {
        return chart == null || chart.getType() == null ? null : chart;
    }

    public boolean isArchived() {
        return archived;
    }
}
